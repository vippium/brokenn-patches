package app.brokenn.patches.jiohotstar.ui.topbar

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object TopBarContainerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    strings = listOf("TopBarHeight"),
)

internal object TopBarScrollFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    name = "invokeSuspend",
    parameters = listOf("Ljava/lang/Object;"),
    custom = { method, _ -> method.hasShowHideCallPair() },
)

internal object MenuTabSelectorFingerprint : Fingerprint(
    custom = { method, _ -> method.isMenuTabSelector() },
)

internal fun Method.isMenuTabSelector(): Boolean {
    if (returnType != "V" || parameterTypes.toList().isNotEmpty()) return false
    val instructions = implementation?.instructions?.toList() ?: return false
    var tabFlagRead = false
    var itemFlagRead = false
    var firstOrNullFallback = false
    instructions.forEach { instr ->
        when (instr.opcode) {
            Opcode.IGET_BOOLEAN -> {
                val ref = (instr as? ReferenceInstruction)?.reference as? FieldReference
                if (ref != null && ref.name == "e") {
                    if (ref.definingClass == "Lcom/hotstar/bff/models/widget/BffMenuWidgetTab;") tabFlagRead = true
                    if (ref.definingClass == "Lcom/hotstar/bff/models/widget/BffMenuItemWidgetData;") itemFlagRead = true
                }
            }
            Opcode.INVOKE_STATIC, Opcode.INVOKE_VIRTUAL,
            Opcode.INVOKE_INTERFACE, Opcode.INVOKE_SUPER -> {
                val ref = (instr as? ReferenceInstruction)?.reference as? MethodReference
                if (ref != null && ref.name == "firstOrNull") firstOrNullFallback = true
            }
            else -> {}
        }
    }
    return tabFlagRead && itemFlagRead && firstOrNullFallback
}

internal fun Method.hasShowHideCallPair(): Boolean {
    val instructions = implementation?.instructions?.toList() ?: return false
    data class Call(val receiver: Int, val arg: Int, val value: Int)
    val constValues = mutableMapOf<Int, Int>()
    val calls = mutableListOf<Call>()
    instructions.forEach { instr ->
        when (instr.opcode) {
            Opcode.CONST_4 -> {
                val lit = (instr as NarrowLiteralInstruction).narrowLiteral.toInt()
                if (lit == 0 || lit == 1) {
                    constValues[(instr as OneRegisterInstruction).registerA] = lit
                }
            }
            Opcode.INVOKE_VIRTUAL -> {
                val ref = (instr as? ReferenceInstruction)?.reference as? MethodReference
                if (ref != null && ref.parameterTypes.toList() == listOf("Z") && ref.returnType == "V") {
                    val regs = instr as FiveRegisterInstruction
                    constValues[regs.registerD]?.let { calls.add(Call(regs.registerC, regs.registerD, it)) }
                }
            }
            else -> {}
        }
    }
    return calls.size >= 6 && calls.asSequence().zipWithNext { a, b -> a to b }.any { (a, b) ->
        a.value == 1 && b.value == 0 && a.receiver == b.receiver && a.arg != b.arg
    }
}
