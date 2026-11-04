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
        // UNIVERSAL — same patch logic as 26.10.12.2, with no
        // obfuscated class/method name hardcoded anywhere (see
        // Fingerprints.kt). The 26.08.17.3 -> 26.10.12.2 change was
        // pure R8 renaming (Ldn/j0; -> Lvn/e0;, Lr0/c;->c -> Lr0/d;->h,
        // LEm/q;->z2/y2 -> LUm/n;->B2/A2); the logic, constants and
        // instruction layout are identical in both builds. All
        // constants are unchanged from the 26.10 build: container
        // 25.0f, expanded target 0.46f, zeroed animation durations,
        // d (12dp) -> 0.0f, mid-feed show forced false, first-item
        // if-le nopped.
        //
        // How the version-specific pieces are found, without names:
        //   - TopBar class: the defining class of the j() call in e().
        //   - Pills call: the single invoke in e() whose defining
        //     class starts with "Lr0/".
        //   - Height call: the invoke-static j() in b() whose defining
        //     class is that TopBar class.
        //   - Mid-feed show call: the first invoke after the -50
        //     literal in the scroll listener.
        //   - 12dp padding: the single sget of field "d" (type F) in b().
        // Every one of these is checked with require() — if a future
        // build renames one of them, the patch fails loudly instead
        // of patching the wrong instruction.
        //
        // v6 — three changes, all in the same patch:
        //
        // 1. e(): hide the pills (v3/v4).
        //    In e(): j() at 47, move-result at 48, Modifier.height
        //    at 50. 0x00000000 is the IEEE-754 bit pattern for 0.0f.
        //
        // 2. b(): shrink the container itself. This matters
        //    because on a modern device (state.a true) b() takes its static
        //    branch and sizes the container from its own calculation
        //    (2*12dp + j() = 78dp), not from e()'s height — so changing
        //    e() alone would leave a 78dp container around a 1dp child.
        //    Overwriting the add-float result (v7) with 25.0f fixes the
        //    container; the later "if hidden, v7 = 0" step still runs
        //    after this point, so a hidden bar stays 0.
        //
        // 3. BottomNavController scroll listener: stop the bar
        //    reappearing on a mid-feed scroll up. Its logic is:
        //      index == 0 -> show/hide within the top item (kept)
        //      index != 0, delta > j0 -> hide (kept)
        //      index != 0, delta < -50 -> SHOW (removed: forced false)
        //    Only the last show call is touched — the one after the -50
        //    literal — by inserting const/4 <arg register>, 0x0 right
        //    before it. Its pair call is left exactly as it was
        //    (false in that branch), so bottom-nav behaviour is unchanged.
        //    Result: while scrolling mid-feed, content scrolls naturally
        //    from behind the status bar; the small gap only comes back
        //    when the user is back on the first item (top of the feed),
        //    which is also where it is present on launch.
        //
        // History: v1 forced s2() to 1, which is the render branch
        // (if-nez branches when NOT zero) and kept the switcher on
        // screen. v2 forced s2() to 0, which hid the switcher but also
        // removed its height entirely. v3/v4 hid only e()'s content and
        // kept the full height; v4 fixed v3's fingerprint filter order
        // (j() at 48 comes before the -673482817 literal at 53).
        // Do not reintroduce any of those directions.

        // --- 1. e(): remove the pills, KEEP the row height ---
        // e()'s Modifier.height(j()) sizes the row to 54/64dp. That height
        // must stay: the bar is a pure spacer now, and its total height is
        // fraction × child(= 24dp padding + this row). Shrinking the row
        // (v9-v12 did) starves the fraction math, so the strip can never
        // grow tall enough to push the feed's "For You" title below the
        // status bar.
        val contentMethod = TopBarContentFingerprint.method

        // TopBar class, derived — not hardcoded. e() calls its own
        // height method j() (static, returns F, one runtime param).
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
        // The pills call's result must be unused: the next instruction
        // must not be a move-result, or removing the call would leave
        // a dangling result register.
        val afterContent = contentMethod.getInstruction<Instruction>(contentCallIndex + 1)
        require(afterContent.opcode != Opcode.MOVE_RESULT &&
            afterContent.opcode != Opcode.MOVE_RESULT_OBJECT &&
            afterContent.opcode != Opcode.MOVE_RESULT_WIDE
        ) {
            "TopBar content: pills call result is used — removing it is not safe"
        }
        contentMethod.removeInstruction(contentCallIndex)

        // --- 2. b(): shrink the container height to 1dp ---
        val containerMethod = TopBarContainerFingerprint.method

        // Height call: invoke-static j() in the TopBar class itself
        // (not the state provider's a() call, which is earlier).
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

        // Scan forward from the j() call for the first float add
        // (v7 = 2*12dp + j()); do not hardcode its offset.
        //
        // v6 BUG, fixed here: v6 scanned only for Opcode.ADD_FLOAT,
        // but b() uses add-float/2addr (opcode ADD_FLOAT_2ADDR) at
        // instruction 49, so the scan ran past the end of the method
        // and Morphe reported IndexOutOfBoundsException at this loop
        // (RemoveTopBarSwitcherPatch.kt:91 in v6). The 2addr form is
        // also a different instruction type (TwoRegisterInstruction,
        // not ThreeRegisterInstruction), so the destination register
        // is read via whichever interface the found instruction has.
        // The scan is now bounded: if no float add is found within
        // 20 instructions, it fails with a clear message instead of
        // walking off the end of the method.
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
            // ~25dp — matches the animated expanded target: 0.46 × e() row (54dp).
            // static/animated path flip on tab switch caused an ~11dp ghost/bounce.
            "const v$containerHeightRegister, 0x41C80000" // 25.0f
        )

        // --- 2b. v11: force the animated TopBarHeight target to ~4dp ---
        // On devices where We/c.a is false, b() skips the static height
        // modifier entirely and takes the :cond_6 branch instead, which
        // animates the bar height via LA/g;->b(target, spec, "TopBarHeight",
        // ...) with target = 1.0f (expanded) or 0.0f (collapsed). The static
        // overwrite above never executes on that path — this is why v8-v10
        // (8dp/4dp/1dp/0dp) produced no visible change on those devices.
        //
        // dn/Z's layout lambda computes barHeightPx = childHeightPx * target,
        // where childHeightPx is the 54/64dp e() row. The branch consts set
        // target = 1.0f (expanded) or 0.0f (collapsed by the scroll handler).
        // Patching the EXPANDED branch to 0.46 keeps the two-state behaviour:
        // ~25dp spacer (status-bar clearance) at the top of the feed, full
        // collapse while scrolled — a pinned constant (v11-v14) stopped the
        // collapse entirely, leaving the bar's dark background visible as a
        // black strip over scrolled content.
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

        // --- 2c. v15: retarget the EXPANDED branch (1.0f -> 0.46f) ---
        // Two const/high16 1.0f consts live in this block: a scratch one for
        // the easing spec and the expanded-state animation target. Replace
        // the LAST one (the target branch); the collapsed branch stays 0.0f.
        // Replace (not insert) so no indices shift for the scans below.
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

        // --- 2d. v11: kill the collapse/expand animation (tab-switch bounce) ---
        // The animated path builds its tween via LA/n;->c(duration, delay, ...)
        // — 420ms when expanding, 240ms when collapsing. Tab switches can
        // retrigger these, bouncing the whole page. Zeroing the duration
        // register (v5) of each A/n.c call in this block makes every height
        // change instant. Collect all call sites first, then insert in
        // descending order so earlier indices stay valid.
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

        durationCallIndices.sortedDescending().forEach { index ->
            containerMethod.addInstructions(index, "const/4 v5, 0x0")
        }

        // --- 2e. v11, universal form: zero the 12dp padding (field d) ---
        // The static path applies .padding(vertical = d, horizontal = d)
        // AFTER its height modifier, so even at 0/4dp height it kept a 24dp
        // strip — the other half of the tab-switch bounce. Previously
        // this zeroed d in <clinit>, but <clinit> can only be found by
        // its defining class, which is exactly what changes every
        // build. Instead, zero the value where b() reads it: the single
        // sget of field "d" (type F) in b(). In both analysed builds
        // d's uses are all inside b() (the 2*12dp height term and the
        // static-path padding), so this is equivalent to zeroing the
        // field, with no class name to keep in sync.
        //
        // This runs AFTER the container edits above, on purpose: those
        // edits insert at or after the add-float (index 49), which is
        // after the sget (index 47), so the sget index is unchanged by
        // them. No container scan runs after this step, so the
        // insertion here cannot shift a later container index.
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

        // --- 3. scroll listener: mid-feed scroll-up must not show the bar ---
        val scrollMethod = TopBarScrollFingerprint.method
        val scrollMatches = TopBarScrollFingerprint.instructionMatches
        // instructionMatches[0] is the -50 literal (the fingerprint's
        // only filter). The show call is the first invoke after it —
        // the state method name (z2 in 26.08, B2 in 26.10) is NOT used
        // to find it, so this survives the next rename too.
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

        // The show invoke takes (object, boolean); the boolean is the
        // second register used. Overwrite it with 0 just before the
        // invoke, so this one call becomes show(false). The preceding
        // if-ge branches over both this invoke and the pair call after
        // it, so the inserted const is skipped exactly when the invoke is.
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

        // --- 4. v8, old-layout approach: first-item scroll-up must
        //    not show the bar early either ---
        // In the old app the header was a feed widget, not a fixed
        // container: it scrolled away with the page and only came
        // back when the feed was back at the top. The new app's
        // first-item branch in the same scroll method is:
        //   index == 0:
        //     if (delta >= threshold) hide
        //     else if (delta <= -threshold || previousOffset == 0) show
        // The delta arm is what brings the bar back partway through
        // the first item, before the offset is actually 0 — the same
        // "padding comes up too early" feeling as the mid-feed arm.
        // Removing only that arm (nop-ing its if-le) leaves the
        // previousOffset == 0 arm untouched, so the bar still comes
        // back when the feed genuinely reaches the top, and it does
        // nothing else: if neither arm fires, the show state is simply left
        // unchanged, exactly as the original code did.
        //
        // The arm is located by pattern, not by hardcoded index:
        // neg-int (threshold -> -threshold) followed immediately by
        // if-le. This runs after the mid-feed insertion above, but
        // that insertion is at a later index (the -50 branch), so
        // the indices searched here are unaffected by it. The scan
        // is bounded to the whole method, which is only ~39
        // instructions, and fails clearly if the pattern is absent.
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
            "TopBar scroll: no neg-int + if-le first-item show branch found in j\$a (fingerprint matched the wrong method?)"
        }
        scrollMethod.replaceInstruction(firstItemShowBranchIndex, "nop")
    }
}
