package app.brokenn.patches.jiohotstar.notification

import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import app.brokenn.patches.shared.hasStringConstant
import app.brokenn.patches.shared.instrs
import app.brokenn.patches.shared.referenceText
import app.brokenn.patches.shared.stringConstants
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

// The remote-config flag that arms the automatic permission popup. The key is server owned,
// so the anchor survives obfuscation and app updates.
private const val POPUP_FLAG = "all.system.pn_permission_popup.enabled"

// A key the config service can never know, so the flag always falls back to its false default.
private const val DISABLED_FLAG = "brokenn.system.pn_permission_popup.disabled"

// App-owned adapter type held only by the frequency-cap manager, so the manager is found
// without obfuscated names.
private const val FCAP_DETAILS_ADAPTER = "Lcom/hotstar/ui/action/FCAPDetailsJsonAdapter;"

// Public BFF model handed to the frequency-cap evaluator.
private const val FREQUENCY_CAPPED_STATUS_ACTION =
    "Lcom/hotstar/bff/models/common/FrequencyCappedStatusAction;"

// Source-level name of the evaluator, kept as a second identity check.
private const val HANDLE_FCAP_LOG_PREFIX = "handleFrequencyCappedStatusAction"

@Suppress("unused")
val hideNotificationPopupPatch = bytecodePatch(
    name = "Hide notification nudge",
    description = "Disables the in-app notification popup permanently.",
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    execute {
        // Only the flag is muted. No permission call and no popup route is touched, so the
        // system dialog still opens whenever the user asks for it.
        if (mutePopupFlag() == 0) throw PatchException("Notification popup flag not found")
        if (forceCapExhausted() == 0) throw PatchException("Frequency cap evaluator not found")
    }
}

// Points the popup flag at an unknown config key, so the popup can never be enabled remotely.
private fun BytecodePatchContext.mutePopupFlag(): Int {
    var muted = 0
    classDefForEach { candidate ->
        // Proxying every class is expensive, so short-circuit on the immutable def first.
        if (!candidate.hasStringConstant(POPUP_FLAG)) return@classDefForEach
        mutableClassDefBy(candidate).methods.forEach { method ->
            method.instrs().forEachIndexed { index, instruction ->
                if (instruction.opcode != Opcode.CONST_STRING) return@forEachIndexed
                if (instruction.referenceText() != POPUP_FLAG) return@forEachIndexed
                val register = method.getInstruction<OneRegisterInstruction>(index).registerA
                method.replaceInstruction(index, "const-string v$register, \"$DISABLED_FLAG\"")
                muted++
            }
        }
    }
    return muted
}

// The popup is a server dialog delivered as a frequency-capped action. The cap evaluator
// decides with one boolean whether the capped action may run; forcing that boolean to false
// makes every evaluation answer "already capped", so the popup never shows - not on a fresh
// install and not after any cap window. Manual allow flows and the system dialog are
// different code paths and stay untouched.
private fun BytecodePatchContext.forceCapExhausted(): Int {
    val managers = mutableListOf<String>()
    classDefForEach { candidate ->
        if (candidate.fields.any { it.type == FCAP_DETAILS_ADAPTER }) managers += candidate.type
    }
    if (managers.size != 1) return 0
    val managerType = managers.single()
    val managerDef = mutableClassDefBy(managerType)
    val evaluators = managerDef.methods.filter { method ->
        method.parameterTypes.contains(FREQUENCY_CAPPED_STATUS_ACTION) &&
            method.stringConstants().any { it.startsWith(HANDLE_FCAP_LOG_PREFIX) }
    }
    if (evaluators.size != 1) return 0
    val evaluator = evaluators.single()
    val implementation = evaluator.implementation ?: return 0

    // The decision holder is the only class that is both constructed inside the evaluator and
    // written through a boolean field store.
    val constructed = implementation.instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.NEW_INSTANCE) return@mapNotNull null
        ((instruction as ReferenceInstruction).reference as TypeReference).type
    }.toSet()
    val holderTypes = implementation.instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.IPUT_BOOLEAN) return@mapNotNull null
        val field = (instruction as ReferenceInstruction).reference as FieldReference
        if (field.type != "Z" || field.definingClass !in constructed) return@mapNotNull null
        field.definingClass
    }.distinct()
    if (holderTypes.size != 1) return 0
    val holderType = holderTypes.single()

    val sites = implementation.instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.IPUT_BOOLEAN) return@mapIndexedNotNull null
        val field = (instruction as ReferenceInstruction).reference as FieldReference
        if (field.definingClass != holderType) return@mapIndexedNotNull null
        Triple(index, (instruction as TwoRegisterInstruction).registerA, field)
    }
    if (sites.isEmpty()) return 0

    var forced = 0
    // Insert from the back so earlier indices stay valid while the method grows.
    sites.sortedByDescending { it.first }.forEach { (index, valueRegister, _) ->
        // The value register is overwritten with zero, so it must not be read again before
        // its next write, or the evaluator would observe a corrupted value.
        requireRegisterDeadAfter(evaluator, index, valueRegister)
        evaluator.addInstructions(index, "const/4 v$valueRegister, 0x0")
        forced++
    }
    return forced
}

// Opcodes whose first register operand is a destination, so touching it redefines the
// register instead of reading its old value.
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

private fun touchedRegisters(instruction: Instruction): List<Int> = when (instruction) {
    is RegisterRangeInstruction ->
        (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
    is FiveRegisterInstruction ->
        listOf(
            instruction.registerC, instruction.registerD, instruction.registerE,
            instruction.registerF, instruction.registerG,
        ).take(instruction.registerCount)
    is ThreeRegisterInstruction ->
        listOf(instruction.registerA, instruction.registerB, instruction.registerC)
    is TwoRegisterInstruction -> listOf(instruction.registerA, instruction.registerB)
    is OneRegisterInstruction -> listOf(instruction.registerA)
    else -> emptyList()
}

private fun requireRegisterDeadAfter(
    method: MutableMethod,
    siteIndex: Int,
    register: Int,
) {
    method.implementation!!.instructions.drop(siteIndex + 1).forEach { instruction ->
        val registers = touchedRegisters(instruction)
        if (register !in registers) return@forEach
        val redefines = instruction.opcode in WRITES_FIRST_REGISTER && registers.first() == register
        if (!redefines) {
            throw PatchException("Frequency cap decision register is read after the store")
        }
        return
    }
}
