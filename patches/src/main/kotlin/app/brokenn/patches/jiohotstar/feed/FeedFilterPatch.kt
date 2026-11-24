package app.brokenn.patches.jiohotstar.feed

import app.brokenn.patches.jiohotstar.settings.openBrokennSettingsPatch
import app.brokenn.patches.jiohotstar.shared.Constants
import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import app.brokenn.patches.jiohotstar.shared.enableStatus
import app.brokenn.patches.shared.instructionIndices
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal object TraySpaceAssemblerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("Lcom/hotstar/bff/api/v2/Space;"),
    strings = listOf("getWidgetWrappersList(...)", "getTitle(...)"),
)

@Suppress("unused")
val feedFilterPatch = bytecodePatch(
    name = "Feed filter",
    description = "Removes selected home feed trays by title, configured in Brokenn settings",
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    dependsOn(openBrokennSettingsPatch)

    execute {
        enableStatus("feedFilter")

        val trayAssembler = TraySpaceAssemblerFingerprint.method
        val trayInstanceofIndices = trayAssembler.instructionIndices { instr ->
            instr.opcode == Opcode.INSTANCE_OF
        }
        if (trayInstanceofIndices.size != 1) throw IllegalStateException(
            "Feed filter: expected exactly one instanceof in the tray assembler, found ${trayInstanceofIndices.size} (shape changed?)"
        )
        val trayInstanceofIndex = trayInstanceofIndices.single()
        val trayCastIndex = trayAssembler.instructionIndices { instr ->
            instr.opcode == Opcode.CHECK_CAST
        }.filter { it < trayInstanceofIndex }.maxOrNull() ?: throw IllegalStateException(
            "Feed filter: widget list check-cast not found (shape changed?)"
        )
        val trayReg = trayAssembler.getInstruction<OneRegisterInstruction>(trayCastIndex).registerA
        trayAssembler.addInstructions(
            trayCastIndex + 1,
            "invoke-static {v$trayReg}, ${Constants.BROKENN_PREFS_CLASS}" +
                "->filterShelfList(Ljava/util/List;)Ljava/util/List;\n" +
                "move-result-object v$trayReg",
        )
    }
}
