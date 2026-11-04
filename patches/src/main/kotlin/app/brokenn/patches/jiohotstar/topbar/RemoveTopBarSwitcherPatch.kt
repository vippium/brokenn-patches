package app.brokenn.patches.jiohotstar.topbar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR_TOPBAR
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val removeTopBarSwitcherPatch = bytecodePatch(
    name = "Remove top bar switcher",
    description = "Removes the JioHotstar/Tadka mode-switcher row from the top of Home."
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR_TOPBAR)

    execute {
        // Universal: no obfuscated names hardcoded. Version-specific
        // targets are found by scanning (see Fingerprints.kt); any
        // scan miss fails loudly via require().

        // 1. Remove the pills call (the single Lr0/ invoke in e()),
        // keeping the row height. Its result must be unused.
        val contentMethod = TopBarContentFingerprint.method
        var topBarClass: String? = null
        var contentCallIndex = -1
        var scanContent = 0
        while (scanContent < 200) {
            val instr = try {
                contentMethod.getInstruction<Instruction>(scanContent)
            } catch (e: Exception) {
                break
            }
            val ref = (instr as? ReferenceInstruction)?.reference as? MethodReference
            if (ref != null) {
                if (instr.opcode == Opcode.INVOKE_STATIC &&
                    ref.name == "j" && ref.returnType == "F"
                ) {
                    require(topBarClass == null || topBarClass == ref.definingClass) {
                        "TopBar content: multiple distinct j()F calls in e() — ambiguous"
                    }
                    topBarClass = ref.definingClass
                }
                if (ref.definingClass.startsWith("Lr0/")) {
                    require(contentCallIndex < 0) {
                        "TopBar content: more than one Lr0/ call in e() — ambiguous, refusing to guess"
                    }
                    contentCallIndex = scanContent
                }
            }
            scanContent++
        }
        require(topBarClass != null) {
            "TopBar content: no TopBar;->j() call found in e() (fingerprint matched the wrong method?)"
        }
        require(contentCallIndex >= 0) {
            "TopBar content: no Lr0/ pills call found in e() (fingerprint matched the wrong method?)"
        }
        val afterContent = contentMethod.getInstruction<Instruction>(contentCallIndex + 1)
        require(afterContent.opcode != Opcode.MOVE_RESULT &&
            afterContent.opcode != Opcode.MOVE_RESULT_OBJECT &&
            afterContent.opcode != Opcode.MOVE_RESULT_WIDE
        ) {
            "TopBar content: pills call result is used — removing it is not safe"
        }
        contentMethod.removeInstruction(contentCallIndex)

        // 2. Set the container height to 25dp. b() sizes itself
        // (2*12dp + j() = 78dp), not from e(), so e() alone is not
        // enough. A hidden bar stays 0 via its later step.
        val containerMethod = TopBarContainerFingerprint.method
        var containerHeightCallIndex = -1
        var scanContainer = 0
        while (scanContainer < 200) {
            val instr = try {
                containerMethod.getInstruction<Instruction>(scanContainer)
            } catch (e: Exception) {
                break
            }
            val ref = (instr as? ReferenceInstruction)?.reference as? MethodReference
            if (ref != null && instr.opcode == Opcode.INVOKE_STATIC &&
                ref.name == "j" && ref.returnType == "F" &&
                ref.definingClass == topBarClass
            ) {
                containerHeightCallIndex = scanContainer
                break
            }
            scanContainer++
        }
        require(containerHeightCallIndex >= 0) {
            "TopBar container: no TopBar;->j() call found in b() (fingerprint matched the wrong method?)"
        }

        // First float add after j(), handling both ADD_FLOAT and
        // ADD_FLOAT_2ADDR, bounded to 20 instructions.
        var addFloatIndex = containerHeightCallIndex
        var scanned = 0
        while (scanned <= 20) {
            val opcode = containerMethod.getInstruction<Instruction>(addFloatIndex).opcode
            if (opcode == Opcode.ADD_FLOAT || opcode == Opcode.ADD_FLOAT_2ADDR) {
                break
            }
            addFloatIndex++
            scanned++
        }
        require(scanned <= 20) {
            "TopBar container: no ADD_FLOAT after j() call (fingerprint matched the wrong method?)"
        }
        val addFloatInstruction = containerMethod.getInstruction<Instruction>(addFloatIndex)
        val containerHeightRegister = when (addFloatInstruction) {
            is ThreeRegisterInstruction -> addFloatInstruction.registerA
            is TwoRegisterInstruction -> addFloatInstruction.registerA
            else -> throw IllegalStateException(
                "TopBar container: float add is neither 3-register nor 2addr form"
            )
        }
        containerMethod.addInstructions(
            addFloatIndex + 1,
            "const v$containerHeightRegister, 0x41C80000" // 25.0f
        )

        // Animated path: retarget the expanded 1.0f branch to 0.46f
        // (~25dp), keeping the collapsed 0.0f branch. Replace, don't
        // insert, so later indices stay stable.
        val expandedTargetBits = 0x3EEB851F // 0.46f
        var topBarHeightIndex = addFloatIndex + 1
        var scannedHeight = 0
        while (scannedHeight <= 60) {
            val instr = containerMethod.getInstruction<Instruction>(topBarHeightIndex)
            if (instr.opcode == Opcode.CONST_STRING &&
                (instr as ReferenceInstruction).reference is StringReference &&
                ((instr as ReferenceInstruction).reference as StringReference).string ==
                "TopBarHeight"
            ) {
                break
            }
            topBarHeightIndex++
            scannedHeight++
        }
        require(scannedHeight <= 60) {
            "TopBar container: TopBarHeight animation call not found after ADD_FLOAT"
        }
        val oneFloatIndices = buildList {
            var i = addFloatIndex + 1
            while (i < topBarHeightIndex) {
                val instr = containerMethod.getInstruction<Instruction>(i)
                if (instr.opcode == Opcode.CONST_HIGH16 &&
                    (instr as NarrowLiteralInstruction).narrowLiteral == 0x3f800000
                ) {
                    add(i)
                }
                i++
            }
        }
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

        // Zero animation durations (LA/n;->c) so height changes are
        // instant. Insert descending so earlier indices stay valid.
        val durationCallIndices = buildList {
            var i = addFloatIndex + 1
            while (i < topBarHeightIndex) {
                val instr = containerMethod.getInstruction<Instruction>(i)
                if (instr.opcode == Opcode.INVOKE_STATIC) {
                    val ref = (instr as ReferenceInstruction).reference as? MethodReference
                    if (ref != null && ref.definingClass == "LA/n;" && ref.name == "c") {
                        add(i)
                    }
                }
                i++
            }
        }
        require(durationCallIndices.isNotEmpty()) {
            "TopBar container: no animation duration calls found (LA/n renamed?)"
        }
        durationCallIndices.sortedDescending().forEach { index ->
            containerMethod.addInstructions(index, "const/4 v5, 0x0")
        }

        // Zero the 12dp padding: the single sget of d:F in b().
        // Runs last for b() on purpose — its index precedes all
        // edits above, so it is unchanged by them.
        var sgetDIndex = -1
        var scannedSget = 0
        while (scannedSget < 200) {
            val instr = try {
                containerMethod.getInstruction<Instruction>(scannedSget)
            } catch (e: Exception) {
                break
            }
            if (instr.opcode == Opcode.SGET) {
                val fieldRef = (instr as ReferenceInstruction).reference as? FieldReference
                if (fieldRef != null && fieldRef.name == "d" && fieldRef.type == "F") {
                    require(sgetDIndex < 0) {
                        "TopBar container: more than one sget of d:F in b() — ambiguous, refusing to guess"
                    }
                    sgetDIndex = scannedSget
                }
            }
            scannedSget++
        }
        require(sgetDIndex >= 0) {
            "TopBar container: sget of d:F not found in b()"
        }
        val sgetDRegister = containerMethod
            .getInstruction<OneRegisterInstruction>(sgetDIndex).registerA
        containerMethod.addInstructions(sgetDIndex + 1, "const v$sgetDRegister, 0x00000000")

        // 3. Mid-feed scroll-up: force the show call (first invoke
        // after the -50 literal) to false, leaving its pair call
        // untouched. The inserted const is skipped exactly when the
        // invoke is, by the preceding if-ge branch.
        val scrollMethod = TopBarScrollFingerprint.method
        val scrollMatches = TopBarScrollFingerprint.instructionMatches
        val scrollMinus50Index = scrollMatches[0].index
        var scrollShowIndex = -1
        var scanScroll = scrollMinus50Index + 1
        while (scanScroll < 200) {
            val instr = try {
                scrollMethod.getInstruction<Instruction>(scanScroll)
            } catch (e: Exception) {
                break
            }
            if (instr.opcode.name.startsWith("INVOKE")) {
                scrollShowIndex = scanScroll
                break
            }
            scanScroll++
        }
        require(scrollShowIndex >= 0) {
            "TopBar scroll: no invoke after the -50 literal (fingerprint matched the wrong method?)"
        }
        val scrollInvoke =
            scrollMethod.getInstruction<FiveRegisterInstruction>(scrollShowIndex)
        val scrollUsedRegisters = listOf(
            scrollInvoke.registerC,
            scrollInvoke.registerD,
            scrollInvoke.registerE,
            scrollInvoke.registerF,
            scrollInvoke.registerG
        ).take(scrollInvoke.registerCount)
        val scrollArgRegister = scrollUsedRegisters[1]
        scrollMethod.addInstructions(
            scrollShowIndex,
            "const/4 v$scrollArgRegister, 0x0"
        )

        // 4. First-item scroll-up: nop only the delta arm
        // (neg-int + if-le). The previousOffset == 0 arm stays, so
        // the bar returns only at the very top. This insertion is
        // after the -50 branch, so it does not shift this scan.
        var firstItemShowBranchIndex = -1
        var scanIndex = 0
        while (scanIndex < 100) {
            val opcode = try {
                scrollMethod.getInstruction<Instruction>(scanIndex).opcode
            } catch (e: Exception) {
                break
            }
            if (opcode == Opcode.NEG_INT) {
                val nextOpcode = try {
                    scrollMethod.getInstruction<Instruction>(scanIndex + 1).opcode
                } catch (e: Exception) {
                    null
                }
                if (nextOpcode == Opcode.IF_LE) {
                    firstItemShowBranchIndex = scanIndex + 1
                    break
                }
            }
            scanIndex++
        }
        require(firstItemShowBranchIndex >= 0) {
            "TopBar scroll: no neg-int + if-le first-item show branch found (fingerprint matched the wrong method?)"
        }
        scrollMethod.replaceInstruction(firstItemShowBranchIndex, "nop")
    }
}
