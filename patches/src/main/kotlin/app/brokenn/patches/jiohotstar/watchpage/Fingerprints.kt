package app.brokenn.patches.jiohotstar.watchpage

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal object AdaptiveTabConverterFingerprint : Fingerprint(
    returnType = "Lcom/hotstar/bff/models/widget/BffAdaptiveTabContainerWidget;",
    parameters = listOf("Lcom/hotstar/ui/model/widget/AdaptiveTabContainerWidget;")
)

internal object PillConverterFingerprint : Fingerprint(
    returnType = "Lcom/hotstar/bff/models/widget/BffIllustrationPillWidget;",
    parameters = listOf("L"),
    filters = listOf(
        string("getIllustration(...)"),
        string("getActions(...)")
    )
)

internal object SubNavParserFingerprint : Fingerprint(
    filters = listOf(
        string("getSubMenuItemsList(...)"),
        string("getIllustrationPill(...)")
    )
)
