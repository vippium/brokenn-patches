package app.brokenn.patches.jiohotstar.font

import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import app.brokenn.patches.shared.instrs
import app.brokenn.patches.shared.referenceText
import app.brokenn.patches.shared.stringConstants
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val SANS_SERIF = "sans-serif"
private const val SANS_SERIF_SOURCE = "FontFamily.SansSerif"
private const val MONOSPACE_SOURCE = "FontFamily.Monospace"
private const val LOCALE_FAMILY_GETTER = "getTesseractFontFamily"
private const val ENGLISH_FAMILY_GETTER = "getFontFamilyEn"
private const val ARABIC_FAMILY_GETTER = "getFontFamilyArabic"

@Suppress("unused")
val useSystemFontPatch = bytecodePatch(
    name = "Use system font",
    description = "Replaces the bundled Inter typeface with your device's system font",
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    execute {
        // The generic font family table: its initializer builds the "sans-serif" family
        // next to the other Android generics, so the string triple identifies it alone.
        var tableClass: ClassDef? = null
        classDefForEach { candidate ->
            if (tableClass != null) return@classDefForEach
            val initializer = candidate.methods.firstOrNull { it.name == "<clinit>" } ?: return@classDefForEach
            val strings = initializer.stringConstants()
            if (SANS_SERIF in strings && SANS_SERIF_SOURCE in strings && MONOSPACE_SOURCE in strings) {
                tableClass = candidate
            }
        }
        val table = tableClass ?: throw PatchException("Generic font family table not found")
        val familyType = table.type

        // The field the initializer fills right after building the "sans-serif" family.
        val initializer = table.methods.first { it.name == "<clinit>" }
        val instructions = initializer.instrs()
        val sansSerifStart = instructions.indexOfFirst { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == SANS_SERIF
        }
        if (sansSerifStart < 0) throw PatchException("Generic sans-serif family not found")
        val sansSerifField = instructions.drop(sansSerifStart).firstNotNullOfOrNull { instruction ->
            (instruction as? ReferenceInstruction)?.reference as? FieldReference
        } ?: throw PatchException("Generic sans-serif family field not found")
        if (sansSerifField.definingClass != familyType) {
            throw PatchException("Generic sans-serif family field has an unexpected owner")
        }
        val systemFont = "${sansSerifField.definingClass}->${sansSerifField.name}:${sansSerifField.type}"

        // The one family holder the typography reads, picked by locale in its own initializer.
        var holderClass: ClassDef? = null
        classDefForEach { candidate ->
            if (holderClass != null) return@classDefForEach
            val initializer = candidate.methods.firstOrNull { it.name == "<clinit>" } ?: return@classDefForEach
            val calls = initializer.instrs().mapNotNull { it.referenceText() }
            if (calls.none { it.contains("->$ENGLISH_FAMILY_GETTER") }) return@classDefForEach
            if (calls.none { it.contains("->$ARABIC_FAMILY_GETTER") }) return@classDefForEach
            if (candidate.methods.any { it.name == LOCALE_FAMILY_GETTER && it.returnType == familyType }) {
                holderClass = candidate
            }
        }
        val holder = holderClass ?: throw PatchException("Locale font family holder not found")

        val getter = mutableClassDefBy(holder).methods.first {
            it.name == LOCALE_FAMILY_GETTER && it.returnType == familyType
        }
        val familyReadIndex = getter.instrs().indexOfFirst { instruction ->
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            instruction.opcode == Opcode.SGET_OBJECT &&
                field?.definingClass == holder.type && field.type == familyType
        }
        if (familyReadIndex < 0) throw PatchException("Locale font family read not found")

        // Point the locale family at the generic sans-serif family, i.e. the device font.
        val register = getter.getInstruction<OneRegisterInstruction>(familyReadIndex).registerA
        getter.replaceInstruction(familyReadIndex, "sget-object v$register, $systemFont")
    }
}
