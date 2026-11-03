package app.brokenn.patches.jiohotstar.topbar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val removeTopBarSwitcherPatch = bytecodePatch(
    name = "Remove top bar switcher",
    description = "Removes the JioHotstar / Tadka mode switcher pills, shrinks the empty space they leave behind to 8dp, and makes the top area behave like the old app's scrolling header: it only comes back at the very top of the feed, never on a scroll up mid-feed or partway through the first item."
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    execute {
        // v6 — three changes, all in the same patch:
        //
        // 1. e(): hide the pills (v3/v4) and shrink their height to 8dp
        //    (v5 was 16dp, user asked for 8dp).
        //    In Ldn/j0;->e(): j() at 48, move-result at 49, Modifier.height
        //    at 50. 0x41000000 is the IEEE-754 bit pattern for 8.0f.
        //
        // 2. b(): shrink the container itself to 8dp. This matters
        //    because on a modern device (Ve/c.a true) b() takes its static
        //    branch and sizes the container from its own calculation
        //    (2*12dp + j() = 78dp), not from e()'s height — so changing
        //    e() alone would leave a 78dp container around an 8dp child.
        //    Overwriting the add-float result (v7) with 8.0f fixes the
        //    container; the later "if hidden, v7 = 0" step still runs
        //    after this point, so a hidden bar stays 0.
        //
        // 3. j$a (BottomNavController scroll listener): stop the bar
        //    reappearing on a mid-feed scroll up. Its logic is:
        //      index == 0 -> show/hide within the top item (kept)
        //      index != 0, delta > j0 -> hide (kept)
        //      index != 0, delta < -50 -> SHOW (removed: forced false)
        //    Only the last z2 call is touched — the one after the -50
        //    literal — by inserting const/4 <arg register>, 0x0 right
        //    before it. y2 is left exactly as it was (false in that
        //    branch), so bottom-nav behaviour is unchanged.
        //    Result: while scrolling mid-feed, content scrolls naturally
        //    from behind the status bar; the small gap only comes back
        //    when the user is back on the first item (top of the feed),
        //    which is also where it is present on launch.
        //
        // ORDER MATTERS in e(): remove first, then insert. The removal
        // is at index 108, after the height instructions, so it does
        // not shift the height indices. Inserting first would shift the
        // content call to 109 and a stale index would remove the wrong
        // instruction.
        //
        // History: v1 forced s2() to 1, which is the render branch
        // (if-nez branches when NOT zero) and kept the switcher on
        // screen. v2 forced s2() to 0, which hid the switcher but also
        // removed its height entirely. v3/v4 hid only e()'s content and
        // kept the full height; v4 fixed v3's fingerprint filter order
        // (j() at 48 comes before the -673482817 literal at 53).
        // Do not reintroduce any of those directions.

        // --- 1. e(): remove content, shrink height to 8dp ---
        val contentMethod = TopBarContentFingerprint.method
        val contentMatches = TopBarContentFingerprint.instructionMatches

        val contentIndex = contentMatches.last().index
        contentMethod.removeInstruction(contentIndex)

        val heightCallIndex = contentMatches[1].index
        val heightMoveResultIndex = heightCallIndex + 1
        val heightRegister =
            contentMethod.getInstruction<OneRegisterInstruction>(heightMoveResultIndex).registerA
        contentMethod.addInstructions(
            heightMoveResultIndex + 1,
            // 0x41000000 = 8.0f bits. Dalvik registers are untyped, so a
            // const with those bits is read as 8.0f by Modifier.height.
            "const v$heightRegister, 0x41000000"
        )

        // --- 2. b(): shrink the container height to 8dp ---
        val containerMethod = TopBarContainerFingerprint.method
        val containerMatches = TopBarContainerFingerprint.instructionMatches
        val containerHeightCallIndex = containerMatches[2].index

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
            "const v$containerHeightRegister, 0x41000000"
        )

        // --- 3. j$a: mid-feed scroll-up must not show the bar ---
        val scrollMethod = TopBarScrollFingerprint.method
        val scrollMatches = TopBarScrollFingerprint.instructionMatches
        val scrollShowIndex = scrollMatches[1].index

        // The z2 invoke takes (object, boolean); the boolean is the
        // second register used. Overwrite it with 0 just before the
        // invoke, so this one call becomes z2(false). The preceding
        // if-ge branches over both this invoke and the y2 after it,
        // so the inserted const is skipped exactly when the invoke is.
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
        // first-item branch in the same j$a method is:
        //   index == 0:
        //     if (delta >= threshold) hide
        //     else if (delta <= -threshold || previousOffset == 0) show
        // The delta arm is what brings the bar back partway through
        // the first item, before the offset is actually 0 — the same
        // "padding comes up too early" feeling as the mid-feed arm.
        // Removing only that arm (nop-ing its if-le) leaves the
        // previousOffset == 0 arm untouched, so the bar still comes
        // back when the feed genuinely reaches the top, and it does
        // nothing else: if neither arm fires, z2 is simply left
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
