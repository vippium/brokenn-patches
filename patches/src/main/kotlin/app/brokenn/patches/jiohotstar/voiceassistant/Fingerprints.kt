package app.brokenn.patches.jiohotstar.voiceassistant

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

// Search-bar mic gate; false hides the primary mic branch.
object VoiceSearchEnabledFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ui/model/pagedata/ExplorePageData;",
    name = "getVoiceSearchEnabled",
    returnType = "Z"
)

// Page-proto CVD orb field; mappers gate on hasCvdOrb.
object WatchPageCvdOrbFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ui/model/pagedata/WatchPageData;",
    name = "hasCvdOrb",
    returnType = "Z"
)

object ExplorePageCvdOrbFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ui/model/pagedata/ExplorePageData;",
    name = "hasCvdOrb",
    returnType = "Z"
)

object LandingPageCvdOrbFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ui/model/pagedata/LandingPageData;",
    name = "hasCvdOrb",
    returnType = "Z"
)

object LandingV2PageCvdOrbFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ui/model/pagedata/LandingV2PageData;",
    name = "hasCvdOrb",
    returnType = "Z"
)

// Equaliser-config readers (disambiguated by param count); false kills the fallback mic.
object CvdEqualiserEventFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L", "L"),
    filters = listOf(
        string("all.cvd.equaliser_enabled")
    )
)

object CvdEqualiserFlowFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
    filters = listOf(
        string("all.cvd.equaliser_enabled")
    )
)

// Widget enum clinit (filters in string order); renamed wires make the parser skip CVD widgets.
object CvdWidgetTypeFingerprint : Fingerprint(
    filters = listOf(
        string("CVDHeroWidget"),
        string("CVDControlUnitWidget"),
        string("CVDOrbWidget"),
        string("CVD_ORB_WIDGET")
    )
)

// The CVD-orb click lambda (version-pinned class; the orb's action processor).
object AskActionClickFingerprint : Fingerprint(
    definingClass = "LGo/i;",
    name = "invoke",
    returnType = "Ljava/lang/Object;"
)

// The home suggestion tray (ChatGPT block) converter: staggered tray of TopicsPill chips.
object HomeSuggestionTrayFingerprint : Fingerprint(
    definingClass = "LBd/Q7;",
    name = "a",
    returnType = "LBd/P7;"
)
