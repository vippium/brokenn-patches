package app.brokenn.patches.jiohotstar.watchpage.player

import app.brokenn.patches.jiohotstar.misc.settings.openBrokennSettingsPatch
import app.brokenn.patches.jiohotstar.shared.Constants
import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import app.brokenn.patches.jiohotstar.watchpage.tabs.AdaptiveTabConverterFingerprint
import app.brokenn.patches.jiohotstar.shared.Constants.TAB_WIDGET
import app.brokenn.patches.jiohotstar.shared.Constants.TAB_WIDGET_DATA
import app.brokenn.patches.jiohotstar.shared.enableStatus
import app.brokenn.patches.shared.instrs
import app.brokenn.patches.shared.instructionIndices
import app.brokenn.patches.shared.prevInstructionIndex
import app.brokenn.patches.shared.redefinesRegister
import app.brokenn.patches.shared.touchedRegisters
import app.brokenn.patches.shared.referenceText
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val hidePlayerButtonsPatch = bytecodePatch(
    name = "Hide player buttons",
    description = "Hides selected buttons from the video player."
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    dependsOn(openBrokennSettingsPatch)

    execute {
        enableStatus("hidePlayerButtons")

        val dConv = PlayerWidgetConverterFingerprint.method
        val dImpl = dConv.instrs()

        data class Site(val idx: Int, val elemReg: Int, val loopHead: Int, val itemType: String)
        val sites = dImpl.mapIndexedNotNull { idx, ins ->
            val ref = ins.referenceText()
            if (ins.opcode != Opcode.INVOKE_STATIC || ref?.contains("PlayerControlMenuItem;)") != true) {
                return@mapIndexedNotNull null
            }
            val elemReg = dConv.getInstruction<OneRegisterInstruction>(idx + 1).registerA
            val head = dConv.prevInstructionIndex(idx + 1) { l ->
                l.opcode == Opcode.INVOKE_INTERFACE &&
                    l.referenceText()?.contains("Iterator;->hasNext") == true
            }
            require(head >= 0) { "PlayerWidgetConverter: loop head not found" }
            Site(idx, elemReg, head, ref.substringAfterLast(')'))
        }
        require(sites.isNotEmpty()) { "PlayerWidgetConverter: menu item sites not found" }

        for (site in sites.sortedByDescending { it.idx }) {
            requireRegistersSafeInLoop(dImpl, site.idx + 2, site.loopHead, listOf(13, 15), "PlayerWidgetConverter")
            val block = buildString {
                append("invoke-interface {v${site.elemReg}}, ${site.itemType}->getTitle()Ljava/lang/String;\n")
                append("move-result-object v13\n")
                append(Constants.prefsStringCall("isPlayerButtonHidden", "13", "15"))
                append("if-nez v15, :pi_skip\n")
                append(":pi_keep\n")
                append("nop\n")
            }
            dConv.addInstructionsWithLabels(
                site.idx + 2,
                block,
                ExternalLabel("pi_skip", dImpl[site.loopHead])
            )
        }

        val tabContainer = AdaptiveTabConverterFingerprint.method
        val tabImpl = tabContainer.instrs()
        val landscapeMarker = tabImpl.indexOfFirst { ins ->
            ins.opcode == Opcode.CONST_STRING &&
                ins.referenceText() == "getLandscapeTabsList(...)"
        }
        require(landscapeMarker >= 0) { "TabConverter: landscape tabs getter not found" }
        val nextListMarker = tabImpl.withIndex().firstOrNull { (idx, ins) ->
            idx > landscapeMarker && ins.opcode == Opcode.CONST_STRING &&
                ins.referenceText()?.endsWith("List(...)") == true
        }?.index ?: tabImpl.size
        val landscapeSite = tabImpl.withIndex().firstOrNull { (idx, ins) ->
            idx in landscapeMarker + 1 until nextListMarker &&
                ins.opcode == Opcode.CHECK_CAST &&
                ins.referenceText()?.contains(TAB_WIDGET) == true
        }?.index ?: throw IllegalStateException("TabConverter: landscape cast not found")
        val itemReg = tabContainer.getInstruction<OneRegisterInstruction>(landscapeSite).registerA
        val landscapeHead = tabContainer.prevInstructionIndex(landscapeSite) { ins ->
            ins.opcode == Opcode.INVOKE_INTERFACE &&
                ins.referenceText()?.contains("Iterator;->hasNext") == true
        }
        require(landscapeHead >= 0) { "TabConverter: landscape loop head not found" }

        requireRegistersSafeInLoop(tabImpl, landscapeSite + 1, landscapeHead, listOf(5, 6), "TabConverter")

        val landscapeBlock = buildString {
            append("invoke-virtual {v$itemReg}, ${TAB_WIDGET}->getData()${TAB_WIDGET_DATA}\n")
            append("move-result-object v5\n")
            append("invoke-virtual {v5}, Lcom/hotstar/ui/model/widget/TabWidget${'$'}Data;->getTitle()Ljava/lang/String;\n")
            append("move-result-object v5\n")
            append(Constants.prefsStringCall("isPlayerButtonHidden", "5", "6"))
            append("if-nez v6, :pl_skip\n")
            append(":pl_keep\n")
            append("nop\n")
        }
        tabContainer.addInstructionsWithLabels(
            landscapeSite + 1,
            landscapeBlock,
            ExternalLabel("pl_skip", tabImpl[landscapeHead])
        )

        val topBar = PlayerTopBarFingerprint.method
        val appends = topBar.instructionIndices { ins ->
            ins.opcode == Opcode.INVOKE_VIRTUAL &&
                ins.referenceText()?.let { it.contains("->add(Ljava/lang/Object;)Z") && !it.contains("ArrayList") } == true
        }
        require(appends.size >= 4) { "PlayerTopBar: button append sites not found (${appends.size})" }
        for (idx in appends.sortedDescending()) {
            val ins = topBar.getInstruction<FiveRegisterInstruction>(idx)
            val listReg = ins.registerC
            val itemReg = ins.registerD
            require(listReg < 16 && itemReg < 16) { "PlayerTopBar: append registers exceed the 4-bit form" }
            topBar.replaceInstruction(
                idx,
                "invoke-static {v$listReg, v$itemReg}, ${Constants.BROKENN_PREFS_CLASS}->" +
                    "addTopBarItem(Ljava/lang/Object;Ljava/lang/Object;)V"
            )
        }

        val backArrow = PlayerBackButtonFingerprint.method
        val backImpl = backArrow.instrs()
        val backParamRegisters = backArrow.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 }
        val backRegisterCount = backArrow.implementation!!.registerCount
        require(backRegisterCount > backParamRegisters) { "PlayerBackButton: no local register free" }
        backArrow.addInstructionsWithLabels(
            0,
            buildString {
                append("invoke-static {}, ${Constants.BROKENN_PREFS_CLASS}->isPlayerBackHidden()Z\n")
                append("move-result v0\n")
                append("if-eqz v0, :pb_keep\n")
                append("return-void\n")
            },
            ExternalLabel("pb_keep", backImpl[0])
        )
    }
}

private fun requireRegistersSafeInLoop(
    impl: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>,
    injectIndex: Int,
    loopHead: Int,
    registers: List<Int>,
    tag: String,
) {
    val backEdge = impl.withIndex().firstOrNull { (idx, ins) ->
        idx > injectIndex && (ins.opcode == Opcode.GOTO ||
            ins.opcode == Opcode.GOTO_16 || ins.opcode == Opcode.GOTO_32)
    }?.index ?: throw IllegalStateException("$tag: loop back edge not found")

    fun firstTouchIsWrite(from: Int, until: Int, register: Int): Boolean {
        for (idx in from until until) {
            val ins = impl[idx]
            if (register !in ins.touchedRegisters()) continue
            return ins.redefinesRegister(register)
        }
        return true
    }
    registers.forEach { register ->
        require(firstTouchIsWrite(loopHead, injectIndex, register)) {
            "$tag: register v$register is read before redefinition in the loop head half"
        }
        require(firstTouchIsWrite(injectIndex, backEdge, register)) {
            "$tag: register v$register is read before redefinition in the loop tail half"
        }
    }
}
