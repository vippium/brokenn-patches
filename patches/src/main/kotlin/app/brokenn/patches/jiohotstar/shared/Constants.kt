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

    // TopBar only: its fingerprints are version-agnostic (see
    // topbar/Fingerprints.kt), so it is offered for both builds whose
    // TopBar code was verified identical apart from R8 renaming.
    // The other patches keep COMPATIBILITY_JIOHOTSTAR above — do NOT
    // widen that one to 26.08.17.3, or Manager would offer every patch
    // for a build they were not verified against.
    val COMPATIBILITY_JIOHOTSTAR_TOPBAR = Compatibility(
        name = "JioHotstar",
        packageName = "in.startv.hotstar",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x1A237E,
        targets = listOf(
            AppTarget(version = "26.08.17.3"),
            AppTarget(version = "26.10.12.2")
        )
    )
}
