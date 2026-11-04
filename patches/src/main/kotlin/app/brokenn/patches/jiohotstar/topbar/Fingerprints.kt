package app.brokenn.patches.jiohotstar.topbar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * UNIVERSAL (version-agnostic) TopBar fingerprints.
 *
 * History: the dev.5 fingerprints were derived from 26.08.17.3 and
 * failed on 26.10.12.2, and the 26.10 fingerprints would in turn fail
 * on 26.08. The change between those builds was almost pure R8
 * renaming, not logic:
 *   26.08: Ldn/j0; / Lr0/c;->c / LEm/q;->z2/y2 / LEm/j$a;
 *   26.10: Lvn/e0; / Lr0/d;->h / LUm/n;->B2/A2 / LUm/g$a;
 * while the patch logic, constants and instruction layout stayed
 * identical (verified by dex dump of both base APKs: content e()
 * is 117 instructions in both, container b() is 131 in both,
 * scroll invokeSuspend is 39 in both, at the same indices).
 *
 * So these fingerprints deliberately contain NO obfuscated class or
 * method names. They anchor only on things R8 does not rename:
 * Compose restart-group keys, a distinctive literal, a stable
 * androidx layout call, and the method signature / access flags.
 * The patch body then finds the version-specific pieces (the pills
 * call, the height call, the show call) by scanning the resolved
 * method, instead of trusting a hardcoded index or name.
 *
 * Verified unique (full-dex scan of 26.08.17.3; same methods confirmed
 * by dump in 26.10.12.2):
 *   - literal 1673754903: exactly 1 method (the content method)
 *   - literals 1673754903 + -673482817 together: exactly 1 method
 *   - literal 1318644515: exactly 1 method (the container method)
 *
 * What is still name-dependent, and therefore the next thing to
 * break on a future build (in order of likelihood):
 *   - the pills call's package prefix "Lr0/" (stable across 26.08/26.10)
 *   - the height method name "j" (stable across 26.08/26.10)
 *   - the float field name "d" (stable across 26.08/26.10)
 *   - the animation helpers LA/n;->c, LA/g;->b and the string
 *     "TopBarHeight" (stable across 26.08/26.10)
 * If a future build renames any of those, the patch fails loudly
 * (a require() in the body) instead of patching the wrong method.
 * That is intentional: a universal fingerprint must never silently
 * match the wrong method.
 *
 * Filters MUST be in method order — Morphe matches them sequentially.
 */

/**
 * The TopBar content container — the layout that holds the
 * JioHotstar / Tadka pills. It is called only from the two TopBar
 * content renderers, so patching it touches nothing else.
 *
 * Why patch this method, and not the TopBar method f (v2)?
 * v2 forced f to return early, which removed the pills — but it also
 * removed the whole TopBar container, including the height and the
 * status-bar padding it provided. The feed below then slid up under
 * the status bar. Patching e() instead keeps the container and its
 * height, and only skips the pills inside it, so the gap stays
 * exactly as before.
 *
 * Instruction layout (identical in 26.08 and 26.10, 0-based):
 *   0   const 1673754903         <- Compose restart-group key for e()
 *   47  invoke TopBar;->j()F     <- TopBar height: 54dp portrait, 64dp otherwise
 *   49  invoke layout/i;->g(...)  <- Modifier.height(that value)
 *   52  const -673482817
 *   108 invoke Lr0/*;->c/h(...)  <- the content call that renders the pills
 *                                  (result unused, not a branch target, so
 *                                  removing it is safe; the layout node
 *                                  is still closed at 109)
 *
 * The patch does NOT use instructionMatches.last() for the content
 * call any more: with literal-only filters, the matches are just the
 * two literals. Instead it scans e() for the single invoke whose
 * defining class starts with "Lr0/" and removes that one.
 */
object TopBarContentFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    // First parameter is an obfuscated type, matched generically ("L");
    // the Compose runtime parameter and the int flag are stable.
    parameters = listOf("L", "Landroidx/compose/runtime/b;", "I"),
    filters = listOf(
        literal(1673754903),
        literal(-673482817)
    )
)

/**
 * The TopBar container — what actually sizes the top-bar area.
 * Its smali (0-based, identical in 26.08 and 26.10):
 *   8   const 1318644515          <- restart-group key
 *   40  invoke state-provider;->a(...)  <- TopBar state (name changed, not used)
 *   43  invoke TopBar;->j()F      <- old height (54 / 64dp)
 *   44  move-result v5
 *   46  int-to-float v7, 2
 *   47  sget v12, TopBar;->d F    <- 12.0f
 *   48  mul-float v7, v7, v12     <- v7 = 2 * 12dp = 24dp
 *   49  add-float/2addr v7, v5    <- v7 = 2*12dp + j() = 78dp (or 0 if hidden)
 *                                     NOTE: this is ADD_FLOAT_2ADDR, not
 *                                     ADD_FLOAT — v6 scanned only for the
 *                                     latter and ran off the end of the
 *                                     method (IndexOutOfBounds at the scan
 *                                     loop). The scan below checks both,
 *                                     bounded.
 *                                     The mul at 48 is NOT an add, so the
 *                                     first ADD_FLOAT after j() is still
 *                                     the one at 49 — the scan is unchanged.
 *   62  invoke layout/i;->g(..., v7) <- Modifier.height(v7) in the static branch
 *                                     (state.a true, e.g. Android 15)
 *
 * The patch overwrites v7 with 25.0f right after the add-float, so
 * the container itself is 25dp when shown. The later "if hidden, v7 = 0"
 * step still runs after this insertion point, so a hidden bar stays 0.
 *
 * The first parameter changed between builds (LVe/c; -> LWe/c;), so
 * it is matched generically ("L") here, like the content method's.
 * The layout call is androidx (not obfuscated) and is kept as a filter.
 */
object TopBarContainerFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("L", "Z", "L", "Landroidx/compose/runtime/b;", "I"),
    filters = listOf(
        literal(1318644515),
        methodCall(definingClass = "Landroidx/compose/foundation/layout/i;", name = "g")
    )
)

/**
 * The bottom-nav scroll listener (BottomNavController$connect$1$1).
 * Its smali (0-based, identical in 26.08 and 26.10, 39 instructions):
 *   if (index == 0) { ...show/hide within the top item... }
 *   else if (delta > j0) { show(false); hide-state(false); }  // scrolling down: hide
 *   else if (delta < -50) { show(true); hide-state(false); }  // scrolling up: SHOW
 *
 * That last branch is the reported bug: scrolling up mid-feed makes the
 * (now empty) top bar reappear and push content down, before the user
 * is anywhere near the top. The patch forces that one show call to
 * false, so the bar only comes back via the index == 0 branch — i.e.
 * when the user is back on the first item — never mid-feed.
 *
 * The state method names changed (z2/y2 -> B2/A2) and so did their
 * class, so they are NOT fingerprint filters any more. The fingerprint
 * is the method name "invokeSuspend" (a Kotlin coroutine name, not an
 * R8 short name, so it survives renaming), the (Object)Object
 * signature, and the exact -50 literal. The patch body then finds
 * the show call as the first invoke after the -50 literal, and the
 * first-item branch as the neg-int + if-le pattern — both scans are
 * name-agnostic, exactly as in the original patch.
 *
 * Caution: -50 as a substring appears in other literals (e.g. -500),
 * but Morphe's literal(-50) is an exact integer match, not a
 * substring. If a future build gains another invokeSuspend method
 * with an exact -50, this fingerprint will match more than one
 * method and Morphe will fail the patch instead of patching the
 * wrong one. That failure is the signal to add a distinguishing
 * filter for that build — it is not a silent mis-patch.
 */
object TopBarScrollFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    name = "invokeSuspend",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        literal(-50)
    )
)

// NOTE: there is deliberately no TopBarClinitFingerprint any more.
// The old clinit step zeroed the TopBar's d:F field (12dp) in
// <clinit>, but <clinit> can only be fingerprinted by its defining
// class — exactly the name that changes every build. The same effect
// is now achieved inside the container method itself: the patch
// finds the single sget of field "d" (type F) in b() and inserts
// const <that register>, 0 right after it. In both analysed builds
// d's uses are all inside b() (the 2*12dp height term and the
// static-path padding), so zeroing the sget result in b() is
// equivalent to zeroing the field, with no class name to keep in
// sync. If a future build uses d outside b(), that use will keep
// its 12dp — a visible difference the device test will catch, and
// the fix then is a clinit variant for that build, not a silent
// change to this one.
