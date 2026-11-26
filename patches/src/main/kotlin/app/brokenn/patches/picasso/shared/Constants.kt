package app.brokenn.patches.picasso.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_PICASSO = Compatibility(
        name = "Picasso",
        packageName = "com.offshore.picasso",
        appIconColor = 0xD32F2F,
        targets = listOf(
            AppTarget(version = "10.14.5")
        )
    )

    const val ADS_CONFIG = "Lcom/offshore/picasso/network/model/config/AdsConfigNew;"
    const val UPDATE_INFO = "Lcom/offshore/picasso/network/model/config/ApkUpdateInfo;"
    const val CONFIGURATION = "Lcom/offshore/picasso/network/model/config/Configuration;"

    const val SPLASH_ACTIVITY = "Lcom/offshore/picasso/SplashScreenActivity;"
    const val MAIN_ACTIVITY = "Lcom/offshore/picasso/MainActivity;"
    const val APP_CONFIG = "Lcom/offshore/picasso/network/model/config/AppConfig;"

    const val BANNER_ADS = "Lcom/offshore/picasso/utils/ads/BannerAds;"
    const val INTERSTITIAL_ADS = "Lcom/offshore/picasso/utils/ads/PopUpAds;"
    const val NATIVE_ADS = "Lcom/offshore/picasso/utils/ads/NativeAds;"
    const val REWARDED_ADS = "Lre/o;"
}
