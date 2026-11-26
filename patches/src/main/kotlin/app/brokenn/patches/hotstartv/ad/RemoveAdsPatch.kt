package app.brokenn.patches.hotstartv.ad

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.returnEarly
import app.morphe.patcher.patch.bytecodePatch
import app.brokenn.patches.shared.firstInstructionIndex
import app.brokenn.patches.shared.nextInstructionIndex
import app.brokenn.patches.shared.referenceText
import app.brokenn.patches.hotstartv.shared.Constants.COMPATIBILITY_HOTSTAR_TV
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes pre-roll, mid-roll, live-match and home-feed ads."
) {
    compatibleWith(COMPATIBILITY_HOTSTAR_TV)

    execute {

        AdMetadataConstructorFingerprint.method.addInstructions(0, """
            const/4 p1, 0x1
            const/4 p2, 0x1
            const/4 p3, 0x0
            const/4 p4, 0x0
            const/4 p5, 0x0
            const-string p6, ""
        """)

        val liveAdsInit = WatchLiveAdsInitFingerprint.matchOrNull()?.method
            ?: WatchLiveAdsInitTvFingerprint.method
        liveAdsInit.returnEarly()
        DisplayAdComposableFingerprint.method.returnEarly()
        DisplayAdSlotFingerprint.matchOrNull()?.method?.returnEarly()

        SgaiPrefetcherGetAdFingerprint.matchOrNull()?.method?.addInstructions(0, """
            invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
            move-result-object v0
            return-object v0
        """)

        HotstarTvBillboardAdGateFingerprint.matchOrNull()?.method?.returnEarly(false)

        LiveAdsManagerProcessFingerprint.matchOrNull()?.method?.addInstructions(0, """
            sget-object v0, Lkotlin/Unit;->a:Lkotlin/Unit;
            return-object v0
        """)

        val cuePoints = AdCuePointsConverterFingerprint.matchOrNull()?.method ?: return@execute
        val fetchIndex = cuePoints.firstInstructionIndex {
            it.referenceText() == CUE_POINTS_GETTER
        }
        if (fetchIndex >= 0) {
            val resultIndex = cuePoints.nextInstructionIndex(fetchIndex, 2) {
                it.opcode == Opcode.MOVE_RESULT_OBJECT
            }
            if (resultIndex >= 0) {
                val listRegister = cuePoints.getInstruction<OneRegisterInstruction>(resultIndex).registerA
                cuePoints.addInstructions(resultIndex + 1, """
                    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
                    move-result-object v$listRegister
                """)
            }
        }
    }
}

private const val CUE_POINTS_GETTER =
    "Lcom/hotstar/ui/model/feature/intervention/AdBreakInterventionSource;->getCuePointsList()Ljava/util/List;"
