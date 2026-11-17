package app.brokenn.patches.jiohotstar.voiceassistant

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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

// Sub-nav row parser; the Ask pill rides the ILLUSTRATION_PILL branch like Swiggy pills.
object CvdSubNavParserFingerprint : Fingerprint(
    filters = listOf(
        string("getSubMenuItemsList(...)"),
        string("getIllustrationPill(...)")
    )
)

// Widget dispatcher arm that converts the home staggered tray into the ChatGPT suggestion
// block. Anchored on the proto widget type, which is not obfuscated.
object StaggeredTrayConvertFingerprint : Fingerprint(
    custom = { method, _ -> method.convertsStaggeredTray() }
)

private const val STAGGERED_TRAY =
    "Lcom/hotstar/ui/model/widget/StaggeredTrayWidget;"

/** True when [method] calls a converter that takes a staggered tray widget. */
private fun Method.convertsStaggeredTray(): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction is ReferenceInstruction &&
            (instruction.reference as? MethodReference)?.parameterTypes?.let { types ->
                types.size == 1 && types[0] == STAGGERED_TRAY
            } == true
    } == true
