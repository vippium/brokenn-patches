package app.brokenn.patches.jiohotstar.voiceassistant

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.addFreeRegisters
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR

@Suppress("unused")
val removeVoiceAssistantPatch = bytecodePatch(
    name = "Remove ChatGPT Voice Assistant",
    description = "Strips out the integrated ChatGPT voice assistant module entirely."
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    execute {
        // Search-bar mic: force the server-driven voice-search gate off.
        VoiceSearchEnabledFingerprint.method.addInstructions(0, """
            const/4 v0, 0x0
            return v0
        """)

        // CVD orb in page protos: false makes mappers drop it.
        for (fingerprint in listOf(
            WatchPageCvdOrbFingerprint,
            ExplorePageCvdOrbFingerprint,
            LandingPageCvdOrbFingerprint,
            LandingV2PageCvdOrbFingerprint
        )) {
            fingerprint.method.addInstructions(0, """
                const/4 v0, 0x0
                return v0
            """)
        }

        // Fallback mic branch: collapse both equaliser-config readers to disabled (suspension-safe insert).
        val equaliserMethods = listOf(
            CvdEqualiserEventFingerprint.method,
            CvdEqualiserFlowFingerprint.method
        )
        for (method in equaliserMethods) {
            val unboxIndex = method.implementation!!.instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_VIRTUAL &&
                    (it as? ReferenceInstruction)?.reference?.toString()?.contains("booleanValue") == true
            }
            val reg = method.getInstruction<OneRegisterInstruction>(unboxIndex + 1).registerA
            val zero = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
            method.addInstructions(unboxIndex + 2, zero)
        }

        // Unknown-widget cut: rename the CVD wire names so the parser skips them.
        val typeMethod = CvdWidgetTypeFingerprint.method
        // TrayFooterWidget = the Ask-more-on-Chatgpt footer under the home suggestion chips.
        val wires = setOf("CVDHeroWidget", "CVDControlUnitWidget", "CVDOrbWidget", "TrayFooterWidget")
        for ((index, instruction) in typeMethod.implementation!!.instructions.withIndex()) {
            val reference = (instruction as? ReferenceInstruction)?.reference
            if (instruction.opcode == Opcode.CONST_STRING && reference is StringReference) {
                val wire = reference.string
                if (wire in wires) {
                    val reg = typeMethod.getInstruction<OneRegisterInstruction>(index).registerA
                    typeMethod.replaceInstruction(index, "const-string v$reg, \"${wire}Removed\"")
                }
            }
        }


        // Ask-pill click: neuter the CVD-orb action handler
        val click = AskActionClickFingerprint.method
        val clickInstrs = click.implementation!!.instructions
        val switchIdx = clickInstrs.indexOfFirst { it.opcode == Opcode.PACKED_SWITCH }
        require(switchIdx >= 0) { "AskActionClick: packed-switch dispatch not found" }
        var unitIdx = -1
        for ((idx, ins) in clickInstrs.withIndex()) {
            if (idx <= switchIdx || ins.opcode != Opcode.SGET_OBJECT) continue
            val f = (ins as ReferenceInstruction).reference as? FieldReference
            if (f != null && f.definingClass == "Lkotlin/Unit;" && f.name == "a") { unitIdx = idx; break }
        }
        require(unitIdx >= 0) { "AskActionClick: Unit return path not found" }
        click.addInstructionsWithLabels(
            switchIdx,
            "goto/16 :skip_actions",
            ExternalLabel("skip_actions", clickInstrs[unitIdx])
        )

        // Home suggestion tray: a staggered tray of TopicsPill chips
        val tray = HomeSuggestionTrayFingerprint.method
        val (_, trayRegs) = tray.addFreeRegisters(3)
        val aReg = trayRegs
        val bReg = trayRegs + 1
        val cReg = trayRegs + 2
        val firstInsn = tray.implementation!!.instructions[0]
        tray.addInstructionsWithLabels(
            0,
            """
            invoke-virtual/range {p0 .. p0}, Lcom/hotstar/ui/model/widget/StaggeredTrayWidget;->getData()Lcom/hotstar/ui/model/widget/StaggeredTrayWidget${'$'}Data;
            move-result-object v$aReg
            invoke-virtual/range {v$aReg .. v$aReg}, Lcom/hotstar/ui/model/widget/StaggeredTrayWidget${'$'}Data;->getItemsList()Ljava/util/List;
            move-result-object v$aReg
            invoke-interface/range {v$aReg .. v$aReg}, Ljava/util/List;->isEmpty()Z
            move-result v$bReg
            if-nez v$bReg, :not_pill
            const/4 v$bReg, 0x0
            invoke-interface/range {v$aReg .. v$bReg}, Ljava/util/List;->get(I)Ljava/lang/Object;
            move-result-object v$bReg
            check-cast v$bReg, Lcom/hotstar/ui/model/widget/StaggeredTrayWidget${'$'}Item;
            invoke-virtual/range {v$bReg .. v$bReg}, Lcom/hotstar/ui/model/widget/StaggeredTrayWidget${'$'}Item;->hasTopicsPill()Z
            move-result v$bReg
            if-eqz v$bReg, :not_pill
            const/4 v$cReg, 0x0
            return-object v$cReg
            :not_pill
            """.trimIndent(),
            ExternalLabel("method_start", firstInsn)
        )
    }
}
