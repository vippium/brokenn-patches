package app.brokenn.patches.jiohotstar.ui.topbar

import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import app.brokenn.patches.shared.instrs
import app.brokenn.patches.shared.instructionIndices
import app.brokenn.patches.shared.nextInstructionIndex
import app.brokenn.patches.shared.zeroTweenDurations
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val removeTopBarSwitcherPatch = bytecodePatch(
    name = "Remove top bar switcher",
    description = "Removes the JioHotstar/Tadka switcher from the top of Home"
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    execute {
        val (tweenFactoryClass, tweenFactoryMethod) = discoverTweenFactory()

        val containerMethod = TopBarContainerFingerprint.method
        val topBarClass = containerMethod.definingClass
        val containerImpl = containerMethod.instrs()

        val heightAccessorIndices = containerMethod.instructionIndices { instr ->
            instr.opcode == Opcode.INVOKE_STATIC &&
                (instr as? ReferenceInstruction)?.reference?.let { ref ->
                    ref is MethodReference && ref.definingClass == topBarClass && ref.returnType == "F"
                } == true
        }
        require(heightAccessorIndices.size == 1) {
            "TopBar container: expected one own-class height accessor call, found ${heightAccessorIndices.size}"
        }
        val containerHeightCallIndex = heightAccessorIndices.first()
        val heightAccessor = (containerMethod.getInstruction<Instruction>(containerHeightCallIndex)
            as ReferenceInstruction).reference as MethodReference

        val contentMethod = mutableClassDefBy(topBarClass).methods.singleOrNull { candidate ->
            candidate.name != containerMethod.name &&
                candidate.parameterTypes.toList() != containerMethod.parameterTypes.toList() &&
                candidate.implementation?.instructions?.any { instr ->
                    instr.opcode == Opcode.INVOKE_STATIC &&
                        (instr as? ReferenceInstruction)?.reference.let { ref ->
                            ref is MethodReference && ref.definingClass == heightAccessor.definingClass &&
                                ref.name == heightAccessor.name &&
                                ref.parameterTypes.toList() == heightAccessor.parameterTypes.toList()
                        }
                } == true
        } ?: throw PatchException("TopBar content: height accessor caller not found or ambiguous")

        val switcherCallIndices = contentMethod.instructionIndices { instr ->
            (instr as? ReferenceInstruction)?.reference?.let { ref ->
                ref is MethodReference && ref.returnType == "Ljava/lang/Object;" &&
                    ref.parameterTypes.toList() ==
                        listOf("Ljava/lang/Object;", "Ljava/lang/Object;", "Ljava/lang/Object;")
            } == true
        }
        require(switcherCallIndices.size == 1) {
            "TopBar content: expected one switcher dispatch call, found ${switcherCallIndices.size}"
        }
        val contentCallIndex = switcherCallIndices.first()
        val afterContent = contentMethod.getInstruction<Instruction>(contentCallIndex + 1)
        require(afterContent.opcode != Opcode.MOVE_RESULT &&
            afterContent.opcode != Opcode.MOVE_RESULT_OBJECT &&
            afterContent.opcode != Opcode.MOVE_RESULT_WIDE
        ) {
            "TopBar content: switcher call result is used — removing it is not safe"
        }
        contentMethod.removeInstruction(contentCallIndex)

        val addFloatIndex = containerMethod.nextInstructionIndex(containerHeightCallIndex, limit = 20) { instr ->
            instr.opcode == Opcode.ADD_FLOAT || instr.opcode == Opcode.ADD_FLOAT_2ADDR
        }
        require(addFloatIndex >= 0) {
            "TopBar container: no ADD_FLOAT after j() call (fingerprint matched the wrong method?)"
        }
        val addFloatInstruction = containerImpl[addFloatIndex]
        val containerHeightRegister = when (addFloatInstruction) {
            is ThreeRegisterInstruction -> addFloatInstruction.registerA
            is TwoRegisterInstruction -> addFloatInstruction.registerA
            else -> throw IllegalStateException(
                "TopBar container: float add is neither 3-register nor 2addr form"
            )
        }
        containerMethod.addInstructions(
            addFloatIndex + 1,
            "const v$containerHeightRegister, 0x41C80000"
        )

        val containerImplAfterInsert = containerMethod.instrs()
        val expandedTargetBits = 0x3EA41A42
        val topBarHeightIndex = TopBarContainerFingerprint.stringMatches.first().index + 1
        val oneFloatIndices = containerImplAfterInsert.withIndex()
            .filter { (i, ins) ->
                i in (addFloatIndex + 2) until topBarHeightIndex &&
                    ins.opcode == Opcode.CONST_HIGH16 &&
                    (ins as NarrowLiteralInstruction).narrowLiteral == 0x3f800000
            }
            .map { it.index }
        require(oneFloatIndices.size >= 2) {
            "TopBar container: expected the expanded/collapsed target branch after ADD_FLOAT"
        }
        val expandedBranchIndex = oneFloatIndices.last()
        val expandedRegister = containerMethod
            .getInstruction<OneRegisterInstruction>(expandedBranchIndex).registerA
        containerMethod.replaceInstruction(
            expandedBranchIndex,
            "const v$expandedRegister, $expandedTargetBits"
        )

        containerMethod.zeroTweenDurations(
            tweenFactoryClass, tweenFactoryMethod,
            "TopBar container: no animation duration calls found",
            fromIndex = addFloatIndex + 2,
            toIndexExclusive = topBarHeightIndex,
        )

        val sgetDIndices = containerMethod.instructionIndices { instr ->
            instr.opcode == Opcode.SGET &&
                ((instr as? ReferenceInstruction)?.reference as? FieldReference)?.let {
                    it.type == "F" && it.definingClass == topBarClass
                } == true
        }
        require(sgetDIndices.isNotEmpty()) {
            "TopBar container: no static float field read found in b()"
        }
        require(sgetDIndices.size == 1) {
            "TopBar container: more than one static float field read in b() — ambiguous, refusing to guess"
        }
        val sgetDRegister = containerMethod
            .getInstruction<OneRegisterInstruction>(sgetDIndices.first()).registerA
        containerMethod.addInstructions(sgetDIndices.first() + 1, "const v$sgetDRegister, 0x00000000")

        val scrollMethod = TopBarScrollFingerprint.method

        data class BoolCall(val index: Int, val receiver: Int, val arg: Int, val value: Int)
        val constBool = mutableMapOf<Int, Int>()
        val calls = mutableListOf<BoolCall>()
        scrollMethod.instrs().forEachIndexed { index, instr ->
            when (instr.opcode) {
                Opcode.CONST_4 -> {
                    val lit = (instr as NarrowLiteralInstruction).narrowLiteral.toInt()
                    if (lit == 0 || lit == 1) {
                        constBool[(instr as OneRegisterInstruction).registerA] = lit
                    }
                }
                Opcode.INVOKE_VIRTUAL -> {
                    val ref = (instr as? ReferenceInstruction)?.reference as? MethodReference
                    if (ref != null && ref.parameterTypes == listOf("Z") &&
                        ref.returnType == "V"
                    ) {
                        val regs = instr as FiveRegisterInstruction
                        constBool[regs.registerD]?.let { value ->
                            calls.add(BoolCall(index, regs.registerC, regs.registerD, value))
                        }
                    }
                }
                else -> {}
            }
        }
        val showCall = calls.asSequence()
            .zipWithNext { a, b -> a to b }
            .firstOrNull { (a, b) ->
                a.value == 1 && b.value == 0 && b.index == a.index + 1 &&
                    a.receiver == b.receiver && a.arg != b.arg
            }?.first
        require(showCall != null) {
            "TopBar scroll: (true,false) show pair not found — scroll listener " +
                "structure changed in this app version"
        }
        scrollMethod.addInstructions(showCall.index, "const/4 v${showCall.arg}, 0x0")

        val scrollImpl = scrollMethod.instrs()
        val firstItemShowBranchIndex = scrollImpl.withIndex()
            .firstOrNull { (i, ins) ->
                ins.opcode == Opcode.NEG_INT &&
                    i + 1 < scrollImpl.size &&
                    scrollImpl[i + 1].opcode == Opcode.IF_LE
            }?.index?.plus(1)
        require(firstItemShowBranchIndex != null) {
            "TopBar scroll: no neg-int + if-le first-item show branch found (fingerprint matched the wrong method?)"
        }
        scrollMethod.replaceInstruction(firstItemShowBranchIndex, "nop")

        findHeaderTransitionMethod(tweenFactoryClass, tweenFactoryMethod).zeroTweenDurations(
            tweenFactoryClass, tweenFactoryMethod,
            "TopBar header: no tween factory calls found"
        )

        val menuTabSelector = MenuTabSelectorFingerprint.method
        val flagReadIndices = menuTabSelector.instructionIndices { instr ->
            instr.opcode == Opcode.IGET_BOOLEAN &&
                (instr as? ReferenceInstruction)?.reference?.let { ref ->
                    ref is FieldReference && ref.name == "e" && (
                        ref.definingClass == "Lcom/hotstar/bff/models/widget/BffMenuWidgetTab;" ||
                            ref.definingClass == "Lcom/hotstar/bff/models/widget/BffMenuItemWidgetData;"
                        )
                } == true
        }
        require(flagReadIndices.isNotEmpty()) {
            "TopBar landing: no server-marked tab flag reads found (fingerprint matched the wrong method?)"
        }
        flagReadIndices.forEach { index ->
            val reg = menuTabSelector.getInstruction<OneRegisterInstruction>(index).registerA
            menuTabSelector.replaceInstruction(index, "const/4 v$reg, 0x0")
        }
    }
}
