package app.brokenn.patches.hotstartv.playback

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.brokenn.patches.hotstartv.shared.Constants.COMPATIBILITY_HOTSTAR_TV

@Suppress("unused")
val forceHdr10Patch = bytecodePatch(
    name = "Force HDR10",
    description = "Forces the HDR10 capability check to pass so HDR10 streams are offered on capable displays."
) {
    compatibleWith(COMPATIBILITY_HOTSTAR_TV)

    execute {
        IsHdr10SupportedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """)
    }
}
