package app.brokenn.patches.jiohotstar.player

import app.morphe.patcher.Fingerprint

// The player-control menu assembly coroutine (v1 control bar path).
object PlayerMenuAssemblyFingerprint : Fingerprint(
    definingClass = "LNd/d0;",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;"
)

// The active (v2) player converter: PlayerV2Widget -> control data (vertical settings loop).
object VerticalControlAssemblyFingerprint : Fingerprint(
    definingClass = "LBd/a6;",
    name = "a",
    returnType = "LBd/Z5;"
)
