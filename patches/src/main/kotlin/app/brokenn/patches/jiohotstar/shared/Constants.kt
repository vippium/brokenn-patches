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
            // Tested and verified working against a real 26.08.17.3 APKM.
            AppTarget(version = "26.08.17.3"),
            // Upstream-tested fallback (Paresh patches).
            AppTarget(version = "26.04.27.10")
        )
    )
}
