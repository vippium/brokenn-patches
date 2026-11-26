package app.brokenn.patches.hotstartv.ad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

internal object AdMetadataConstructorFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Z", "Z", "Z", "Z", "Z", "Ljava/lang/String;", "Ljava/util/List;"),
    filters = listOf(
        string("ssaiTag"),
        string("perPodPositions")
    )
)

internal object WatchLiveAdsInitFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ads/watch/WatchLiveAdsViewModel;",
    name = "b",
    returnType = "V",
)

internal object WatchLiveAdsInitTvFingerprint : Fingerprint(
    definingClass = "Lcom/hotstar/ads/watch/WatchLiveAdsViewModel;",
    name = "d",
    returnType = "V",
)

internal object DisplayAdComposableFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    filters = listOf(
        string("DisplayAd"),
    )
)

internal object DisplayAdSlotFingerprint : Fingerprint(
    definingClass = "LMp/o;",
    name = "d",
    returnType = "V",
)

internal object HotstarTvBillboardAdGateFingerprint : Fingerprint(
    definingClass = "Lla/j0;",
    name = "d",
    returnType = "Z",
)

internal object SgaiPrefetcherGetAdFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        string("SGAI_Prefetcher"),
        string("getAd: ")
    )
)

internal object LiveAdsManagerProcessFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        string("LiveAdsManager"),
        string("Removed stale ad at %d")
    )
)

internal object AdCuePointsConverterFingerprint : Fingerprint(
    filters = listOf(
        string("getCuePointsList(...)")
    )
)
