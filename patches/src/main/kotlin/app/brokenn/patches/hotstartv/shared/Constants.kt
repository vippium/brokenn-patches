package app.brokenn.patches.hotstartv.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_HOTSTAR_TV = Compatibility(
        name = "Hotstar TV",
        packageName = "in.startv.hotstartvonly",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x002434,
        targets = listOf(
            AppTarget(version = "26.08.03.3")
        )
    )
}
