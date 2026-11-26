package app.brokenn.patches.jiohotstar.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.brokenn.patches.jiohotstar.shared.Constants.BROKENN_STATUS_CLASS

internal object BrokennStatusLoadFingerprint : Fingerprint(
    definingClass = BROKENN_STATUS_CLASS,
    name = "load",
)

fun enableStatus(functionName: String) {
    BrokennStatusLoadFingerprint.method.addInstruction(
        0,
        "invoke-static {}, $BROKENN_STATUS_CLASS->$functionName()V",
    )
}
