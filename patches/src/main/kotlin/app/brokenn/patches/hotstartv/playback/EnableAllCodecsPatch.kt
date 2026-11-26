package app.brokenn.patches.hotstartv.playback

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.brokenn.patches.hotstartv.shared.Constants.COMPATIBILITY_HOTSTAR_TV

@Suppress("unused")
val enableAllCodecsPatch = bytecodePatch(
    name = "Enable all codecs",
    description = "Unlocks H.265, VP9, AV1 and 4K playback on capable devices."
) {
    compatibleWith(COMPATIBILITY_HOTSTAR_TV)

    execute {
        Is4kSupportedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """)

        IsCodecSupportedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """)
    }
}
