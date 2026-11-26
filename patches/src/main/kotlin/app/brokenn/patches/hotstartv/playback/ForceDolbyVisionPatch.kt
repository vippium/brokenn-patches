package app.brokenn.patches.hotstartv.playback

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.brokenn.patches.hotstartv.shared.Constants.COMPATIBILITY_HOTSTAR_TV

@Suppress("unused")
val forceDolbyVisionPatch = bytecodePatch(
    name = "Force Dolby Vision",
    description = "Forces the Dolby Vision capability check to pass so DV streams are offered on capable displays."
) {
    compatibleWith(COMPATIBILITY_HOTSTAR_TV)

    execute {
        IsDolbyVisionSupportedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """)
    }
}
