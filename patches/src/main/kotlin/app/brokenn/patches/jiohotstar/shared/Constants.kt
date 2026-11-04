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
            AppTarget(version = "26.10.12.2")
        )
    )

    // TopBar only — its fingerprints are version-agnostic and locate the
    // code by scanning, so future app versions are attempted too (a scan
    // miss fails loudly instead of mis-patching). Do not widen the shared
    // list above; other patches are verified for 26.10 only.
    val COMPATIBILITY_JIOHOTSTAR_TOPBAR = Compatibility(
        name = "JioHotstar",
        packageName = "in.startv.hotstar",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x1A237E,
        targets = listOf(
            // Any future version — experimental; verified versions below.
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "26.08.17.3"),
            AppTarget(version = "26.10.12.2")
        )
    )
}
