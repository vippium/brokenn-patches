package app.brokenn.patches.jiohotstar.ui.navbar

import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import app.brokenn.patches.shared.firstInstructionIndex
import app.brokenn.patches.shared.instructionIndices
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val NAV_HOOK_CLASS = "Lapp/brokenn/extension/jiohotstar/navbar/NavHook;"
private const val LIST_FIELD_READ = "Lkf/r;->a:Ljava/util/List;"
private const val LIST_WRAP_CALL = "Lkf/q;->c("

/**
 * Two independent hook points keep this working across app updates:
 *
 * - Render side (primary): the bottom bar composable reads its item list right before
 *   iterating it. Processing there always matches what is displayed, whatever code
 *   path built the list.
 * - Assembler side (fallback): the coroutine that builds the item list before wrapping
 *   it into the nav state. Used when the render anchor is not found.
 *
 * Only one of them has to exist; the patch fails loudly only when neither does.
 */
@Suppress("unused")
val navTabsPatch = bytecodePatch(
    name = "Customize navigation bar",
    description = "Hides and reorders buttons in the bottom navigation bar."
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    execute {
        var hooked = false

        val renderMethod = runCatching { NavRenderFingerprint.method }.getOrNull()
        if (renderMethod != null) {
            val listReadIndex = renderMethod.firstInstructionIndex { ins ->
                ins.opcode.name == "iget-object" &&
                    (ins as? ReferenceInstruction)?.reference?.toString()
                        ?.contains(LIST_FIELD_READ) == true
            }
            if (listReadIndex >= 0) {
                val listReg = renderMethod
                    .getInstruction<TwoRegisterInstruction>(listReadIndex).registerA
                renderMethod.addInstruction(
                    listReadIndex + 1,
                    "invoke-static {v$listReg}, $NAV_HOOK_CLASS->processNavItems(Ljava/lang/Object;)V"
                )
                hooked = true
            }
        }

        val assemblerMethod = runCatching { NavItemsAssemblerFingerprint.method }.getOrNull()
        if (assemblerMethod != null) {
            val wrapIndices = assemblerMethod.instructionIndices { ins ->
                (ins.opcode.name == "invoke-static" || ins.opcode.name == "invoke-static/range") &&
                    (ins as? ReferenceInstruction)?.reference?.toString()
                        ?.contains(LIST_WRAP_CALL) == true
            }

            for (index in wrapIndices.sortedDescending()) {
                val invoke = assemblerMethod.getInstruction<FiveRegisterInstruction>(index)
                assemblerMethod.addInstruction(
                    index,
                    "invoke-static {v${invoke.registerC}}, $NAV_HOOK_CLASS->" +
                        "applyNavItems(Ljava/lang/Object;)V"
                )
            }
            if (wrapIndices.isNotEmpty()) hooked = true
        }

        require(hooked) { "Customize navigation bar: no navigation hook point found" }
    }
}
