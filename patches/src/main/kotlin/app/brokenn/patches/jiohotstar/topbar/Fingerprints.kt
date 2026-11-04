package app.brokenn.patches.jiohotstar.topbar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * The TopBar content container, in the analysed build:
 *   Ldn/j0;->e(Lr0/c; Landroidx/compose/runtime/b; I)V
 *   (public static final, classes11.dex, JioHotstar 26.08.17.3)
 *
 * This is the layout that holds the JioHotstar / Tadka pills. It is called
 * only from Ldn/j0;->g and Ldn/j0;->h (the two TopBar content renderers,
 * via their restart lambda Ldn/e0;), so patching it touches nothing else.
 *
 * Why patch this method, and not Ldn/j0;->f (v2)?
 * v2 forced f to return early, which removed the pills — but it also
 * removed the whole TopBar container, including the height and the
 * status-bar padding it provided. The feed below then slid up under the
 * status bar. Patching e() instead keeps the container and its height,
 * and only skips the pills inside it, so the gap stays exactly as before.
 *
 * Instruction layout in the analysed build (smali order, 0-based):
 *   0   const 1673754903         <- Compose restart-group key for e()
 *   48  invoke Ldn/j0;->j()F     <- TopBar height: 54dp portrait, 64dp otherwise
 *   50  invoke .../i;->g(...)    <- Modifier.height(that value)
 *   53  const -673482817
 *   108 invoke Lr0/c;->c(...)    <- the content call that renders the pills
 *                                  (result unused, not a branch target, so
 *                                  removing it is safe; the layout node
 *                                  is still closed at 109)
 *
 * Filters MUST be in method order — Morphe matches them sequentially.
 * v3 listed the -673482817 literal before the j() call, but in the
 * method j() comes first (48, then 53), so the fingerprint could never
 * match. That was the cause of the reported "Failed to match the
 * fingerprint" error on 26.08.17.3. v4 fixes only the order, nothing else.
 *
 * instructionMatches.last() is the content call. The patch removes
 * that one instruction. instructionMatches[1] is the j() call, whose
 * move-result the v5 patch overwrites with 16.0f to shrink the gap.
 */
object TopBarContentFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    // First parameter is an obfuscated type, matched generically ("L");
    // the Compose runtime parameter and the int flag are stable.
    parameters = listOf("L", "Landroidx/compose/runtime/b;", "I"),
    filters = listOf(
        literal(1673754903),
        methodCall(definingClass = "Ldn/j0;", name = "j"),
        literal(-673482817),
        methodCall(definingClass = "Lr0/c;", name = "c")
    )
)

/**
 * The TopBar container, in the analysed build:
 *   Ldn/j0;->b(LVe/c; Z Lr0/c; Landroidx/compose/runtime/b; I)V
 *   (public static final, classes11.dex, JioHotstar 26.08.17.3)
 *
 * b() is what actually sizes the top-bar area. Its smali (0-based):
 *   8   const 1318644515          <- restart-group key
 *   40  invoke LQm/a;->a(...)     <- TopBar state (Ve/c)
 *   43  invoke Ldn/j0;->j()F      <- old height (54 / 64dp)
 *   44  move-result v5
 *   49  add-float/2addr v7, v5    <- v7 = 2*12dp + j() = 78dp (or 0 if hidden)
 *                                     NOTE: this is ADD_FLOAT_2ADDR, not
 *                                     ADD_FLOAT — v6 scanned only for the
 *                                     latter and ran off the end of the
 *                                     method (IndexOutOfBounds at the scan
 *                                     loop). v7 scans for both, bounded.
 *   62  invoke .../i;->g(..., v7) <- Modifier.height(v7) in the static branch
 *                                     (Ve/c.a true, e.g. Android 15)
 *
 * The v6 patch overwrites v7 with 8.0f right after the add-float, so
 * the container itself is 8dp when shown. The later "if hidden, v7 = 0"
 * step still runs after this insertion point, so a hidden bar stays 0.
 * instructionMatches[2] is the j() call; the patch scans forward from
 * it for the first ADD_FLOAT instead of hardcoding the +6 offset.
 */
object TopBarContainerFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("LVe/c;", "Z", "L", "Landroidx/compose/runtime/b;", "I"),
    filters = listOf(
        literal(1318644515),
        methodCall(definingClass = "LQm/a;", name = "a"),
        methodCall(definingClass = "Ldn/j0;", name = "j"),
        methodCall(definingClass = "Landroidx/compose/foundation/layout/i;", name = "g")
    )
)

/**
 * The bottom-nav scroll listener, in the analysed build:
 *   LEm/j$a;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
 *   (public final, classes9.dex, JioHotstar 26.08.17.3)
 *   from com.hotstar.ui.bottomnav.BottomNavController$connect$1$1
 *
 * Jadx-readable logic (Si/c$a: c = firstVisibleItemIndex, a = scroll delta):
 *   if (index == 0) { ...show/hide within the top item... }
 *   else if (delta > j0) { z2(false); y2(false); }   // scrolling down: hide
 *   else if (delta < -50) { z2(true); y2(false); }   // scrolling up: SHOW
 *
 * That last branch is the reported bug: scrolling up mid-feed makes the
 * (now empty) top bar reappear and push content down, before the user
 * is anywhere near the top. The v6 patch forces that one z2 call to
 * false, so the bar only comes back via the index == 0 branch — i.e.
 * when the user is back on the first item — never mid-feed.
 *
 * Filters are in method order and start at the -50 literal, so the
 * z2 found after it is exactly the mid-feed one, not the index == 0
 * one (which comes earlier in the method and is left untouched).
 * instructionMatches[1] is that z2 call, [2] is its y2 pair.
 */
object TopBarScrollFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        literal(-50),
        methodCall(definingClass = "LEm/q;", name = "z2"),
        methodCall(definingClass = "LEm/q;", name = "y2")
    )
)

/**
 * Ldn/j0;-><clinit>()V — initialises the TopBar's dp constants as floats:
 *   a:F = 54.0f, b:F = 64.0f (bar heights), c:F = 16.0f,
 *   d:F = 12.0f (static-path vertical/horizontal padding)
 *
 * Method-only fingerprint; the patch scans the body for the d:F store.
 */
object TopBarClinitFingerprint : Fingerprint(
    definingClass = "Ldn/j0;",
    name = "<clinit>"
)
