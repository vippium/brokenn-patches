package app.brokenn.patches.jiohotstar.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_JIOHOTSTAR = Compatibility(
        name = "JioHotstar",
        packageName = "in.startv.hotstar",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x1A237E,
        targets = listOf(
            AppTarget(version = "26.11.09.1")
        )
    )

    private const val TAB_WIDGET_BASE = "Lcom/hotstar/ui/model/widget/TabWidget"
    private const val ILLUSTRATION_PILL_BASE = "Lcom/hotstar/ui/model/widget/IllustrationPillWidget"
    const val TAB_WIDGET = "$TAB_WIDGET_BASE;"
    const val TAB_WIDGET_DATA = "$TAB_WIDGET_BASE\$Data;"
    const val TAB_WIDGET_URL = "$TAB_WIDGET_BASE\$Url;"
    const val ILLUSTRATION_PILL_WIDGET = "$ILLUSTRATION_PILL_BASE;"
    const val ILLUSTRATION_PILL_DATA = "$ILLUSTRATION_PILL_BASE\$Data;"
    const val ILLUSTRATION_PILL_SEGMENT = "$ILLUSTRATION_PILL_BASE\$Segment;"
    const val ILLUSTRATION = "Lcom/hotstar/ui/model/feature/image/Illustration;"
    const val LOTTIE = "Lcom/hotstar/ui/model/feature/image/Lottie;"
    const val BFF_PILL_WIDGET = "Lcom/hotstar/bff/models/widget/BffIllustrationPillWidget;"
    const val BFF_PILL_SUB_MENU_ITEM = "Lcom/hotstar/bff/models/widget/BffIllustrationPillSubMenuItem;"

    const val SETTINGS_PACKAGE = "Lapp/brokenn/extension/jiohotstar/settings"
    const val BROKENN_PREFS_CLASS = "$SETTINGS_PACKAGE/BrokennPrefs;"
    const val BROKENN_STATUS_CLASS = "$SETTINGS_PACKAGE/BrokennStatus;"

    fun prefsStringCall(method: String, argReg: String, resultReg: String) =
        "invoke-static {v$argReg}, $BROKENN_PREFS_CLASS->$method(Ljava/lang/String;)Z\n" +
            "move-result v$resultReg\n"
}
