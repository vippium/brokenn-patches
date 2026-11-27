package app.brokenn.patches.jiohotstar.ui.profile

import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import app.brokenn.patches.shared.constructsType
import app.brokenn.patches.shared.firstInstructionIndex
import app.brokenn.patches.shared.instrs
import app.brokenn.patches.shared.nextInstructionIndex
import app.brokenn.patches.shared.referenceText
import app.brokenn.patches.shared.stringConstants
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val PROFILE_DATA = "Lcom/hotstar/ui/model/widget/ProfileSelectionWidget\$Data;"
private const val PROFILES_GETTER = "$PROFILE_DATA->getProfilesList()Ljava/util/List;"
private const val BFF_PROFILE = "Lcom/hotstar/bff/models/widget/BffProfile;"
private const val BFF_PROFILE_INIT = "$BFF_PROFILE-><init>"
private const val LIST_ADD = "Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z"
private const val SCRATCH_REGISTERS = 2
private const val SCAN_LIMIT = 24

@Suppress("unused")
val hideKidsProfilePatch = bytecodePatch(
    name = "Hide kids profile",
    description = "Removes the kids profile from the profile picker."
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    execute {
        val kidsConstant = kidsProfileConstant()
        val tileTypeField = bffProfileFieldOfType(kidsConstant.substringAfter(':'))

        val mapper = profilePickerMapper()
        val addIndex = mapper.tileAppendIndex()
        val (listReg, tileReg) = mapper.appendRegisters(addIndex)

        val provider = mapper.getFreeRegisterProvider(addIndex, SCRATCH_REGISTERS, listReg, tileReg)
        val type = provider.getFreeRegister4Bit()
        val kids = provider.getFreeRegister4Bit()
        if (type < 0 || kids < 0) throw PatchException("Profile picker has no 4-bit scratch registers")

        mapper.addInstructionsWithLabels(
            addIndex,
            """
                iget-object v$type, v$tileReg, $tileTypeField
                sget-object v$kids, $kidsConstant
                if-eq v$type, v$kids, :kids_tile_skipped
            """.trimIndent(),
            ExternalLabel("kids_tile_skipped", mapper.instrs()[addIndex + 1]),
        )
    }
}

private fun BytecodePatchContext.kidsProfileConstant(): String {
    var constant: String? = null
    classDefForEach { candidate ->
        if (constant != null) return@classDefForEach
        if (candidate.superclass != "Ljava/lang/Enum;") return@classDefForEach
        val initializer = candidate.methods.firstOrNull { it.name == "<clinit>" } ?: return@classDefForEach
        val names = initializer.stringConstants()
        if (names.none { it == "ADULT" } || names.none { it == "KIDS" }) return@classDefForEach
        val writes = mutableMapOf<String, String>()
        var pendingName: String? = null
        initializer.instrs().forEach { ins ->
            val name = ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string
            if (name != null) pendingName = name
            if (ins.opcode != Opcode.SPUT_OBJECT || pendingName == null) return@forEach
            val field = (ins as ReferenceInstruction).reference as? FieldReference ?: return@forEach

            if (field.type != candidate.type) return@forEach
            writes[pendingName!!] = "${field.definingClass}->${field.name}:${field.type}"
        }
        if (writes.keys == setOf("ADULT", "KIDS")) constant = writes["KIDS"]
    }
    return constant ?: throw PatchException("Kids profile type enum not found")
}

private fun BytecodePatchContext.bffProfileFieldOfType(kidsType: String): String =
    mutableClassDefBy(BFF_PROFILE).fields.firstOrNull { it.type == kidsType }
        ?.let { "${it.definingClass}->${it.name}:${it.type}" }
        ?: throw PatchException("Profile type field not found")

private fun BytecodePatchContext.profilePickerMapper(): MutableMethod {
    var mapper: MutableMethod? = null
    classDefForEach { candidate ->
        if (mapper != null) return@classDefForEach
        if (!candidate.hasProfilesGetter()) return@classDefForEach
        mapper = mutableClassDefBy(candidate).methods.firstOrNull { method ->
            method.constructsType(BFF_PROFILE) && method.tileAppendIndexOrNull() != null
        }
    }
    return mapper ?: throw PatchException("Profile picker mapper not found")
}

private fun ClassDef.hasProfilesGetter(): Boolean =
    methods.any { method -> method.instrs().any { it.referenceText() == PROFILES_GETTER } }

private fun MutableMethod.tileAppendIndex(): Int =
    tileAppendIndexOrNull() ?: throw PatchException("Profile tile append not found")

private fun MutableMethod.tileAppendIndexOrNull(): Int? {
    val initIndex = firstInstructionIndex { it.referenceText()?.contains(BFF_PROFILE_INIT) == true }
    if (initIndex < 0) return null
    val index = nextInstructionIndex(initIndex, SCAN_LIMIT) { ins ->
        ins.opcode in APPEND_OPCODES && ins.referenceText() == LIST_ADD
    }
    return index.takeIf { it >= 0 }
}

private val APPEND_OPCODES = setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE)

private fun MutableMethod.appendRegisters(addIndex: Int): Pair<Int, Int> {
    val registers = when (val invoke = instrs()[addIndex]) {
        is FiveRegisterInstruction -> invoke.registerC to invoke.registerD
        is RegisterRangeInstruction -> invoke.startRegister to invoke.startRegister + 1
        else -> throw PatchException("Profile tile append format not supported")
    }
    if (registers.first !in 0..15 || registers.second !in 0..15) {
        throw PatchException("Profile tile registers are out of reach of the kids check")
    }
    return registers
}
