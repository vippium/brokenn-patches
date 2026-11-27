package app.brokenn.patches.all.updates

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import java.util.logging.Logger

private const val EXTENSION_CLASS = "Lapp/brokenn/extension/all/versioncode/DisablePlayStoreUpdatesPatch;"

private var originalVersionCode: Int = 0

private val disablePlayStoreUpdatesResourcePatch = resourcePatch {
    execute {
        packageMetadata?.versionCode?.let { originalVersionCode = it.toInt() }
    }

    finalize {

        document("AndroidManifest.xml").use { manifest ->
            manifest.documentElement.setAttribute("android:versionCode", Int.MAX_VALUE.toString())
        }
    }
}

@Suppress("unused")
internal val disablePlayStoreUpdatesPatch = bytecodePatch(
    name = "Disable Play Store updates",
    description = "Disables Play Store updates by setting the version code to the maximum allowed. " +
            "This patch may cause unexpected issues with some apps and does not work if the " +
            "app is installed by root mounting",
    default = false
) {

    dependsOn(disablePlayStoreUpdatesResourcePatch)

    extendWith("extensions/change-version-code.mpe")

    finalize {
        Fingerprint(
            definingClass = EXTENSION_CLASS,
            name = "originalVersionCode"
        ).method.returnEarly(originalVersionCode)

        val logger by lazy { Logger.getLogger(this::class.java.name) }

        Fingerprint(
            filters = listOf(
                fieldAccess(
                    opcode = Opcode.IGET,
                    smali = "Landroid/content/pm/PackageInfo;->versionCode:I"
                )
            ),
            custom = { _, classDef ->
                !classDef.type.startsWith("Lapp/brokenn/extension")
            }
        ).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            val instruction = this.getInstruction<TwoRegisterInstruction>(index)
            val moveResultRegister = instruction.registerA
            val packageInfoRegister = instruction.registerB
            val moveResultIndex = index + 1

            if (moveResultRegister >= 16) {
                val provider = getFreeRegisterProvider(
                    moveResultIndex,
                    moveResultRegister,
                    packageInfoRegister
                )
                if (!provider.hasFreeRegisters()) {
                    logger.warning("Method does not have enough free registers, version code may not be overridden for: $this")
                    return@matchAllMethodIndicesForEach
                }
                val free = provider.getFreeRegister()
                if (free >= 16) {
                    logger.warning("No 4-bit register available, version code may not be overridden for: $this")
                    return@matchAllMethodIndicesForEach
                }

                addInstruction(
                    moveResultIndex,
                    """
                        move-result v$free
                        move/from16 v$moveResultRegister, v$free
                    """
                )
            } else {
                addInstruction(
                    moveResultIndex,
                    "move-result v$moveResultRegister"
                )
            }

            replaceInstruction(
                index,
                "invoke-static/range { v$packageInfoRegister .. v$packageInfoRegister }, " +
                        "$EXTENSION_CLASS->getVersionCode(Landroid/content/pm/PackageInfo;)I"
            )
        }

        Fingerprint(
            filters = listOf(
                methodCall(
                    opcode = Opcode.INVOKE_VIRTUAL,
                    smali = "Landroid/content/pm/PackageInfo;->getLongVersionCode()J"
                )
            ),
            custom = { _, classDef ->
                !classDef.type.startsWith("Lapp/brokenn/extension")
            }
        ).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            if (getInstruction(index + 1).opcode != Opcode.MOVE_RESULT_WIDE) {
                return@matchAllMethodIndicesForEach
            }
            val register = getInstruction<FiveRegisterInstruction>(index).registerC

            replaceInstruction(
                index,
                "invoke-static/range { v$register .. v$register }, " +
                        "$EXTENSION_CLASS->getVersionCodeLong(Landroid/content/pm/PackageInfo;)J"
            )
        }
    }
}
