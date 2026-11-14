package app.brokenn.patches.jiohotstar.player

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.addFreeRegisters
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR

@Suppress("unused")
val hidePlayerButtonsPatch = bytecodePatch(
    name = "Hide player buttons",
    description = "Hides selected buttons (Watch Next, Episodes, Shop, Speed, Rate, Next Episode) from the player control bar."
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    val hideWatchNext by booleanOption(
        key = "hide_watch_next", title = "Hide Watch Next",
        description = "Hides the Watch Next button.", default = false
    )
    val hideEpisodes by booleanOption(
        key = "hide_episodes", title = "Hide Episodes",
        description = "Hides the Episodes button.", default = false
    )
    val hideShop by booleanOption(
        key = "hide_shop", title = "Hide Shop",
        description = "Hides the Shop button.", default = false
    )
    val hideSpeed by booleanOption(
        key = "hide_speed", title = "Hide Speed",
        description = "Hides the Speed button.", default = false
    )
    val hideRate by booleanOption(
        key = "hide_rate", title = "Hide Rate",
        description = "Hides the Rate button.", default = false
    )
    val hideNextEpisode by booleanOption(
        key = "hide_next_episode", title = "Hide Next Episode",
        description = "Hides the Next Episode button.", default = false
    )

    execute {
        val titles = buildList {
            if (hideWatchNext == true) add("Watch Next")
            if (hideEpisodes == true) add("Episodes")
            if (hideShop == true) add("Shop")
            if (hideSpeed == true) add("Speed")
            if (hideRate == true) add("Rate")
            if (hideNextEpisode == true) add("Next Episode")
        }
        if (titles.isEmpty()) return@execute

        // Path 1 (active): VerticalPlayerControlWidget.Settings.items - each item is a
        // Button whose data.text is the visible label. Hidden items skip the loop body,
        // so they never reach the button bar while the data stays intact server-side.
        val v2Method = VerticalControlAssemblyFingerprint.method
        val (_, v2Regs) = v2Method.addFreeRegisters(2)
        val workReg = v2Regs
        val needleReg = v2Regs + 1

        val v2Impl = v2Method.implementation!!.instructions
        val settingSites = v2Impl.withIndex().filter { (_, ins) ->
            ins.opcode == Opcode.CHECK_CAST &&
                (ins as? ReferenceInstruction)?.reference.toString()
                    .contains("VerticalPlayerControlWidget" + '$' + "SettingItem;")
        }
        require(settingSites.isNotEmpty()) { "VerticalControlAssembly: settings loop not found" }

        for (site in settingSites.reversed()) {
            val siteIdx = site.index
            var head = -1
            for (j in siteIdx downTo 0) {
                val ins = v2Impl[j]
                if (ins.opcode == Opcode.INVOKE_INTERFACE &&
                    (ins as? ReferenceInstruction)?.reference.toString().contains("Iterator;->hasNext")
                ) { head = j; break }
            }
            require(head >= 0) { "VerticalControlAssembly: loop head not found" }

            val block = buildString {
                append("invoke-virtual/range {v$workReg .. v$workReg}, Lcom/hotstar/ui/model/widget/VerticalPlayerControlWidget" + '$' + "SettingItem;->getButton()Lcom/hotstar/ui/model/feature/atom/Button;\n")
                append("move-result-object v$workReg\n")
                append("if-eqz v$workReg, :not_hidden\n")
                append("invoke-virtual/range {v$workReg .. v$workReg}, Lcom/hotstar/ui/model/feature/atom/Button;->getData()Lcom/hotstar/ui/model/feature/atom/Button" + '$' + "Data;\n")
                append("move-result-object v$workReg\n")
                append("if-eqz v$workReg, :not_hidden\n")
                append("invoke-virtual/range {v$workReg .. v$workReg}, Lcom/hotstar/ui/model/feature/atom/Button" + '$' + "Data;->getText()Ljava/lang/String;\n")
                append("move-result-object v$workReg\n")
                append("if-eqz v$workReg, :not_hidden\n")
                titles.forEachIndexed { i, title ->
                    append("const-string v$needleReg, \"$title\"\n")
                    append("invoke-virtual/range {v$workReg .. v$needleReg}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n")
                    append("move-result v$workReg\n")
                    val next = if (i < titles.size - 1) ":hidden_${i + 1}" else ":not_hidden"
                    append("if-eqz v$workReg, $next\n")
                    append("goto/16 :loop_head\n")
                    if (i < titles.size - 1) append(":hidden_${i + 1}\n")
                }
                append(":not_hidden\n")
            }
            v2Method.addInstructionsWithLabels(
                siteIdx + 1,
                block,
                ExternalLabel("loop_head", v2Impl[head])
            )
        }

        // Path 2 (legacy v1 menu bar): same title filter over the F5 items.
        val menuMethod = PlayerMenuAssemblyFingerprint.method
        val (_, menuRegs) = menuMethod.addFreeRegisters(2)
        val menuTitleReg = menuRegs
        val menuNeedleReg = menuRegs + 1

        val menuImpl = menuMethod.implementation!!.instructions
        val menuSites = menuImpl.withIndex().filter { (_, ins) ->
            ins.opcode == Opcode.CHECK_CAST &&
                (ins as? ReferenceInstruction)?.reference.toString().contains("LBd/F5;")
        }
        for (site in menuSites.reversed()) {
            val siteIdx = site.index
            val itemReg = menuMethod.getInstruction<OneRegisterInstruction>(siteIdx).registerA
            var head = -1
            for (j in siteIdx downTo 0) {
                val ins = menuImpl[j]
                if (ins.opcode == Opcode.INVOKE_INTERFACE &&
                    (ins as? ReferenceInstruction)?.reference.toString().contains("Iterator;->hasNext")
                ) { head = j; break }
            }
            if (head < 0) continue

            val block = buildString {
                append("invoke-interface {v$itemReg}, LBd/F5;->getTitle()Ljava/lang/String;\n")
                append("move-result-object v$menuTitleReg\n")
                titles.forEachIndexed { i, title ->
                    append("const-string v$menuNeedleReg, \"$title\"\n")
                    append("invoke-virtual/range {v$menuTitleReg .. v$menuNeedleReg}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n")
                    append("move-result v$menuTitleReg\n")
                    val next = if (i < titles.size - 1) ":hidden_${i + 1}" else ":hidden_next"
                    append("if-eqz v$menuTitleReg, $next\n")
                    append("goto/16 :loop_head\n")
                    if (i < titles.size - 1) append(":hidden_${i + 1}\n")
                }
                append(":hidden_next\n")
            }
            menuMethod.addInstructionsWithLabels(
                siteIdx + 1,
                block,
                ExternalLabel("loop_head", menuImpl[head])
            )
        }
    }
}
