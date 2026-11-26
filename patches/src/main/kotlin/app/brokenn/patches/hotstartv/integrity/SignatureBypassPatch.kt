package app.brokenn.patches.hotstartv.integrity

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import app.brokenn.patches.hotstartv.shared.Constants.COMPATIBILITY_HOTSTAR_TV

@Suppress("unused")
val signatureBypassPatch = bytecodePatch(
    name = "Bypass signature check",
    description = "Bypasses app signature verification to allow API access."
) {
    compatibleWith(COMPATIBILITY_HOTSTAR_TV)

    execute {
        val method = SignatureCheckFingerprint.method
        val matches = SignatureCheckFingerprint.instructionMatches

        val encodeIndex = matches[1].index
        val moveResultIndex = encodeIndex + 1
        val reg = method.getInstruction<OneRegisterInstruction>(moveResultIndex).registerA

        method.addInstructions(moveResultIndex + 1, """
            const-string v$reg, "GnwgdTsKCP54v02ZSST5iA"
        """)
    }
}
