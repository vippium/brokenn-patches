package app.brokenn.patches.jiohotstar.ui.voiceassistant

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object VoiceSearchEnabledFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ui/model/pagedata/ExplorePageData;",
    name = "getVoiceSearchEnabled",
    returnType = "Z"
)

internal object WatchPageCvdOrbFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ui/model/pagedata/WatchPageData;",
    name = "hasCvdOrb",
    returnType = "Z"
)

internal object ExplorePageCvdOrbFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ui/model/pagedata/ExplorePageData;",
    name = "hasCvdOrb",
    returnType = "Z"
)

internal object LandingPageCvdOrbFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ui/model/pagedata/LandingPageData;",
    name = "hasCvdOrb",
    returnType = "Z"
)

internal object LandingV2PageCvdOrbFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ui/model/pagedata/LandingV2PageData;",
    name = "hasCvdOrb",
    returnType = "Z"
)

internal object CvdEqualiserEventFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L", "L"),
    filters = listOf(
        string("all.cvd.equaliser_enabled")
    )
)

internal object CvdEqualiserFlowFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
    filters = listOf(
        string("all.cvd.equaliser_enabled")
    )
)

internal object CvdPageTypeParserFingerprint : Fingerprint(
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        string("LandingV2Page"),
        string("ConversationalDiscoveryPage")
    )
)

internal object CvdPageScreenFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/navigation/Screen${'$'}CVDPage;",
    name = "<clinit>",
    returnType = "V"
)

internal object CvdActionSheetScreenFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/navigation/Screen${'$'}CVDActionSheetPage;",
    name = "<clinit>",
    returnType = "V"
)

internal object CvdOrbComposableFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("cvdOrb"),
        string("BffCVDOrb_")
    )
)

internal object CvdChatBubbleComposableFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("cvd_chat_bubble")
    )
)

internal object CvdActionSheetComposableFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("tag_cvd_action_sheet_page_content_sheet")
    )
)

internal object CvdCoreSearchGateFingerprint : Fingerprint(
    filters = listOf(
        string("all.cvd.allowed_cvd_from_core_search_for_lpd")
    )
)

internal object CvdWidgetTypeFingerprint : Fingerprint(
    filters = listOf(
        string("CVDHeroWidget"),
        string("CVDControlUnitWidget"),
        string("CVDOrbWidget"),
        string("CVD_ORB_WIDGET")
    )
)

internal object CvdSubNavParserFingerprint : Fingerprint(
    filters = listOf(
        string("getSubMenuItemsList(...)"),
        string("getIllustrationPill(...)")
    )
)

internal object HomeSuggestionTrayFingerprint : Fingerprint(
    parameters = listOf("Lcom/hotstar/ui/model/widget/StaggeredTrayWidget;"),
    filters = listOf(
        string("getTrayHeader(...)"),
        string("getItemsList(...)")
    )
)
