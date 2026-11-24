package app.brokenn.patches.jiohotstar.notification

import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import app.brokenn.patches.shared.hasStringConstant
import app.brokenn.patches.shared.instrs
import app.brokenn.patches.shared.redefinesRegister
import app.brokenn.patches.shared.touchedRegisters
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

private const val POPUP_FLAG = "all.system.pn_permission_popup.enabled"
private const val DISABLED_FLAG = "brokenn.system.pn_permission_popup.disabled"
private const val FCAP_DETAILS_ADAPTER = "Lcom/hotstar/ui/action/FCAPDetailsJsonAdapter;"
private const val FREQUENCY_CAPPED_STATUS_ACTION =
    "Lcom/hotstar/bff/models/common/FrequencyCappedStatusAction;"
private const val HANDLE_FCAP_LOG_PREFIX = "handleFrequencyCappedStatusAction"

@Suppress("unused")
val hideNotificationPopupPatch = bytecodePatch(
    name = "Hide notification nudge",
    description = "Disables the in-app notification popup permanently.",
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    execute {
        if (mutePopupFlag() == 0) throw PatchException("Notification popup flag not found")
        if (forceCapExhausted() == 0) throw PatchException("Frequency cap evaluator not found")
    }
}

private fun BytecodePatchContext.mutePopupFlag(): Int {
    var muted = 0
    classDefForEach { candidate ->
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
    sites.sortedByDescending { it.first }.forEach { (index, valueRegister, _) ->
        requireRegisterDeadAfter(evaluator, index, valueRegister)
        evaluator.addInstructions(index, "const/4 v$valueRegister, 0x0")
        forced++
    }
    return forced
}

private fun requireRegisterDeadAfter(
    method: MutableMethod,
    siteIndex: Int,
    register: Int,
) {
    method.implementation!!.instructions.drop(siteIndex + 1).forEach { instruction ->
        if (register !in instruction.touchedRegisters()) return@forEach
        if (!instruction.redefinesRegister(register)) {
            throw PatchException("Frequency cap decision register is read after the store")
        }
        return
    }
}
