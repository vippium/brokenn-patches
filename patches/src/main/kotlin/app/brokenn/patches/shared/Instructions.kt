package app.brokenn.patches.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

// Instruction helpers shared by every app's patch package: method scans,
// reference lookups and animation-snap insertion.

fun Method.instrs(): List<Instruction> = implementation?.instructions?.toList() ?: emptyList()

// toString of the instruction's reference, or null when the instruction carries none.
fun Instruction.referenceText(): String? =
    (this as? ReferenceInstruction)?.reference?.toString()

fun Method.stringConstants(): Set<String> =
    instrs().mapNotNull { (it as? ReferenceInstruction)?.reference as? StringReference }
        .map { it.string }.toSet()

fun Method.fieldRefKeys(): Set<String> =
    instrs().mapNotNull { (it as? ReferenceInstruction)?.reference as? FieldReference }
        .map { "${it.definingClass}->${it.name}:${it.type}" }.toSet()

fun Method.typeRefKeys(): Set<String> =
    instrs().mapNotNull { (it as? ReferenceInstruction)?.reference as? TypeReference }
        .map { it.type }.toSet()

fun Method.constructsType(type: String): Boolean = type in typeRefKeys()

fun Method.readsField(fieldKey: String): Boolean = fieldKey in fieldRefKeys()

fun Method.isStatic(): Boolean = accessFlags and 0x0008 != 0

fun ClassDef.classStrings(): Set<String> = methods.flatMap { it.stringConstants() }.toSet()

// Index of the first instruction matching the predicate, or -1.
fun Method.firstInstructionIndex(predicate: (Instruction) -> Boolean): Int =
    instrs().indexOfFirst(predicate)

// Index of the first instruction after [from] (exclusive) and within [limit] steps
// matching the predicate, or -1.
fun Method.nextInstructionIndex(from: Int, limit: Int = Int.MAX_VALUE, predicate: (Instruction) -> Boolean): Int {
    val impl = instrs()
    val end = minOf(impl.size, from + 1 + limit)
    for (i in from + 1 until end) if (predicate(impl[i])) return i
    return -1
}

// Index of the last instruction before [from] (exclusive) matching the predicate, or -1.
fun Method.prevInstructionIndex(from: Int, predicate: (Instruction) -> Boolean): Int {
    val impl = instrs()
    for (i in from - 1 downTo 0) if (predicate(impl[i])) return i
    return -1
}

// Indices of all instructions matching the predicate.
fun Method.instructionIndices(predicate: (Instruction) -> Boolean): List<Int> =
    instrs().withIndex().filter { predicate(it.value) }.map { it.index }

// Insert const/4 0x0 into the duration register of every tween-factory call so
// the animation snaps instead of tweening. The duration is the invoke's first register.
fun MutableMethod.zeroTweenDurations(factoryClass: String, factoryMethod: String, failMessage: String) {
    val indices = instructionIndices { ins ->
        ins.opcode == Opcode.INVOKE_STATIC &&
            ((ins as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                it.definingClass == factoryClass && it.name == factoryMethod
            } == true
    }
    require(indices.isNotEmpty()) { failMessage }
    indices.sortedDescending().forEach { index ->
        val reg = when (val invoke = getInstruction<Instruction>(index)) {
            is FiveRegisterInstruction -> invoke.registerC
            is RegisterRangeInstruction -> invoke.startRegister
            else -> throw IllegalStateException("$failMessage: unexpected tween invoke format")
        }
        addInstructions(index, "const/4 v$reg, 0x0")
    }
}
