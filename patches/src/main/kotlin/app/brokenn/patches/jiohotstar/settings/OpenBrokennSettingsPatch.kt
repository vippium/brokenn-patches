package app.brokenn.patches.jiohotstar.settings

import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import org.w3c.dom.Element

private const val NAV_HOOK_CLASS = "Lapp/brokenn/extension/jiohotstar/settings/NavHook;"
private const val SETTINGS_ACTIVITY = "app.brokenn.extension.jiohotstar.settings.BrokennSettingsActivity"
private const val VIBRATE_PERMISSION = "android.permission.VIBRATE"

/** Declares the settings activity so NavHook can launch it. */
private val brokennSettingsManifestPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as? Element
                ?: error("AndroidManifest.xml: no <application> element")

            val activities = application.getElementsByTagName("activity")
            var declared = false
            for (i in 0 until activities.length) {
                val activity = activities.item(i) as? Element ?: continue
                if (activity.getAttribute("android:name") == SETTINGS_ACTIVITY) declared = true
            }

            if (!declared) {
                val activity = manifest.createElement("activity")
                activity.setAttribute("android:name", SETTINGS_ACTIVITY)
                activity.setAttribute("android:label", "Brokenn settings")
                activity.setAttribute("android:exported", "false")
                activity.setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                activity.setAttribute("android:screenOrientation", "portrait")
                application.appendChild(activity)
            }

            val permissions = manifest.getElementsByTagName("uses-permission")
            for (i in 0 until permissions.length) {
                val permission = permissions.item(i) as? Element ?: continue
                if (permission.getAttribute("android:name") == VIBRATE_PERMISSION) return@use
            }

            val vibrate = manifest.createElement("uses-permission")
            vibrate.setAttribute("android:name", VIBRATE_PERMISSION)
            manifest.insertBefore(vibrate, application)
        }
    }
}

@Suppress("unused")
val openBrokennSettingsPatch = bytecodePatch(
    name = "Add Brokenn settings",
    description = "Adds a hidden settings page, opened by double-tapping the Home tab button.",
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    dependsOn(brokennSettingsManifestPatch)

    extendWith("extensions/brokenn-settings.mpe")

    execute {
        // Locate the HSBottomNavigation click dispatcher.
        val composable = HSBottomNavigationFingerprint.method
        val composableInsns = composable.instructionsOrNull?.toList()
            ?: error("HSBottomNavigation composable has no instructions")

        // Dispatcher is <init>(I, Object, Object); resolve the class dynamically for renames.
        var dispatcherType: String? = null
        discovery@ for (i in 0 until composableInsns.size) {
                if (composableInsns[i].opcode != Opcode.NEW_INSTANCE) continue
                for (j in i + 1 until minOf(i + 4, composableInsns.size)) {
                    if (composableInsns[j].opcode != Opcode.INVOKE_DIRECT) continue
                    val ref = (composableInsns[j] as? ReferenceInstruction)?.reference as? MethodReference
                    if (ref?.name != "<init>") continue
                    if (ref.parameterTypes != listOf("I", "Ljava/lang/Object;", "Ljava/lang/Object;")) continue
                    dispatcherType = (composableInsns[i] as? ReferenceInstruction)?.reference.let { it as? TypeReference }?.type
                    break@discovery
                }
        }
        if (dispatcherType == null) error("HSBottomNavigation: click dispatcher construction not found")

        val dispatcher = Fingerprint(definingClass = dispatcherType, name = "invoke").method
        val insns = dispatcher.instructionsOrNull?.toList()
            ?: error("Click dispatcher has no instructions")

        // Default case: IGET_OBJECT / CHECK_CAST Function1 / IGET_OBJECT / CHECK_CAST item / invoke.
        var itemReg = -1
        var hookIndex = -1
        for (i in 3 until insns.size) {
            val invoke = insns[i] as? FiveRegisterInstruction ?: continue
            val ref = (insns[i] as? ReferenceInstruction)?.reference as? MethodReference ?: continue
            if (ref.definingClass != "Lkotlin/jvm/functions/Function1;" || ref.name != "invoke") continue
            if (insns[i - 1].opcode != Opcode.CHECK_CAST ||
                insns[i - 2].opcode != Opcode.IGET_OBJECT ||
                insns[i - 3].opcode != Opcode.CHECK_CAST
            ) continue

            val itemCastReg = (insns[i - 1] as OneRegisterInstruction).registerA
            val lambdaCastReg = (insns[i - 3] as OneRegisterInstruction).registerA
            if ((insns[i - 3] as? ReferenceInstruction)?.reference?.let { (it as? TypeReference)?.type } !=
                "Lkotlin/jvm/functions/Function1;"
            ) continue
            // invoke-interface {lambda, item}
            if (itemCastReg != invoke.registerD || lambdaCastReg != invoke.registerC) continue

            hookIndex = i
            itemReg = itemCastReg
            break
        }
        if (hookIndex < 0) error("Click dispatcher: nav-item case not found")

        val lambdaReg = (insns[hookIndex - 3] as OneRegisterInstruction).registerA
        val freeReg = dispatcher.findFreeRegister(hookIndex, itemReg, lambdaReg)

        // Guard the dispatch: a consumed tap opens settings and skips navigation.
        dispatcher.addInstructionsWithLabels(
            hookIndex,
            """
                invoke-static {v$itemReg}, $NAV_HOOK_CLASS->intercept(Ljava/lang/Object;)Z
                move-result v$freeReg
                if-eqz v$freeReg, :nav_click
                sget-object v$freeReg, Lkotlin/Unit;->a:Lkotlin/Unit;
                return-object v$freeReg
            """,
            ExternalLabel("nav_click", insns[hookIndex])
        )
    }
}
