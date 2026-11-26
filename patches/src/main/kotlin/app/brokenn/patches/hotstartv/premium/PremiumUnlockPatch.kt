package app.brokenn.patches.hotstartv.premium

import app.morphe.patcher.util.returnEarly
import app.morphe.patcher.patch.bytecodePatch
import app.brokenn.patches.hotstartv.shared.Constants.COMPATIBILITY_HOTSTAR_TV

@Suppress("unused")
val premiumUnlockPatch = bytecodePatch(
    name = "Premium unlock",
    description = "Unlocks premium UI features, removes subscription nudges, and enables downloads."
) {
    compatibleWith(COMPATIBILITY_HOTSTAR_TV)

    execute {
        IsSubscribedFingerprint.method.returnEarly(true)

        IsPremiumFingerprint.method.returnEarly(false)

        SubscriptionNudgeFingerprint.matchOrNull()?.method?.returnEarly()
    }
}
