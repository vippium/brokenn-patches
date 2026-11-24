package app.brokenn.patches.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

fun Method.instrs(): List<Instruction> = implementation?.instructions?.toList() ?: emptyList()

fun Instruction.referenceText(): String? =
    (this as? ReferenceInstruction)?.reference?.toString()

fun Method.stringConstants(): Set<String> =
    instrs().mapNotNull { (it as? ReferenceInstruction)?.reference as? StringReference }
        .map { it.string }.toSet()

private fun Method.fieldRefKeys(): Set<String> =
    instrs().mapNotNull { (it as? ReferenceInstruction)?.reference as? FieldReference }
        .map { "${it.definingClass}->${it.name}:${it.type}" }.toSet()

private fun Method.typeRefKeys(): Set<String> =
    instrs().mapNotNull { (it as? ReferenceInstruction)?.reference as? TypeReference }
        .map { it.type }.toSet()

fun Method.constructsType(type: String): Boolean = type in typeRefKeys()

fun Method.readsField(fieldKey: String): Boolean = fieldKey in fieldRefKeys()

fun Method.isStatic(): Boolean = AccessFlags.STATIC.isSet(accessFlags)

fun ClassDef.classStrings(): Set<String> = methods.flatMap { it.stringConstants() }.toSet()

fun ClassDef.hasStringConstant(value: String): Boolean = methods.any { method ->
    method.implementation?.instructions?.any { instruction ->
        (instruction as? ReferenceInstruction)?.reference.let { it as? StringReference }?.string == value
    } == true
}

private val WRITES_FIRST_REGISTER = setOf(
    Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST, Opcode.CONST_HIGH16,
    Opcode.CONST_WIDE, Opcode.CONST_WIDE_16, Opcode.CONST_WIDE_32, Opcode.CONST_WIDE_HIGH16,
    Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16,
    Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16,
    Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16,
    Opcode.MOVE_EXCEPTION, Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_OBJECT, Opcode.MOVE_RESULT_WIDE,
    Opcode.NEW_INSTANCE, Opcode.NEW_ARRAY, Opcode.CHECK_CAST, Opcode.ARRAY_LENGTH,
    Opcode.INSTANCE_OF,
    Opcode.IGET, Opcode.IGET_BOOLEAN, Opcode.IGET_BYTE, Opcode.IGET_CHAR, Opcode.IGET_SHORT,
    Opcode.IGET_OBJECT, Opcode.IGET_WIDE,
    Opcode.SGET, Opcode.SGET_BOOLEAN, Opcode.SGET_BYTE, Opcode.SGET_CHAR, Opcode.SGET_SHORT,
    Opcode.SGET_OBJECT, Opcode.SGET_WIDE,
)

fun Instruction.redefinesRegister(register: Int): Boolean =
    opcode in WRITES_FIRST_REGISTER && touchedRegisters().firstOrNull() == register

fun Instruction.touchedRegisters(): List<Int> = when (this) {
    is RegisterRangeInstruction ->
        (startRegister until startRegister + registerCount).toList()
    is FiveRegisterInstruction ->
        listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is ThreeRegisterInstruction -> listOf(registerA, registerB, registerC)
    is TwoRegisterInstruction -> listOf(registerA, registerB)
    is OneRegisterInstruction -> listOf(registerA)
    else -> emptyList()
}

fun Method.firstInstructionIndex(predicate: (Instruction) -> Boolean): Int =
    instrs().indexOfFirst(predicate)

fun Method.nextInstructionIndex(from: Int, limit: Int = Int.MAX_VALUE, predicate: (Instruction) -> Boolean): Int {
    val impl = instrs()
    val end = minOf(impl.size.toLong(), from + 1L + limit).toInt()
    for (i in from + 1 until end) if (predicate(impl[i])) return i
    return -1
}

fun Method.prevInstructionIndex(from: Int, predicate: (Instruction) -> Boolean): Int {
    val impl = instrs()
    for (i in from - 1 downTo 0) if (predicate(impl[i])) return i
    return -1
}

fun Method.instructionIndices(predicate: (Instruction) -> Boolean): List<Int> =
    instrs().withIndex().filter { predicate(it.value) }.map { it.index }

fun MutableMethod.zeroTweenDurations(
    factoryClass: String,
    factoryMethod: String,
    failMessage: String,
    fromIndex: Int = 0,
    toIndexExclusive: Int = Int.MAX_VALUE,
) {
    val indices = instructionIndices { ins ->
        (ins.opcode == Opcode.INVOKE_STATIC || ins.opcode == Opcode.INVOKE_STATIC_RANGE) &&
            ((ins as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                it.definingClass == factoryClass && it.name == factoryMethod
            } == true
    }.filter { it in fromIndex until toIndexExclusive }
    require(indices.isNotEmpty()) { failMessage }
    indices.sortedDescending().forEach { index ->
        val reg = when (val invoke = getInstruction<Instruction>(index)) {
            is FiveRegisterInstruction -> invoke.registerC
            is RegisterRangeInstruction -> invoke.startRegister
            else -> throw IllegalStateException("$failMessage: unexpected tween invoke format")
        }

        val zero = when {
            reg <= 15 -> "const/4 v$reg, 0x0"
            reg <= 255 -> "const/16 v$reg, 0x0"
            else -> "const v$reg, 0x0"
        }
        addInstructions(index, zero)
    }
}
