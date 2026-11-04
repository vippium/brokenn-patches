package app.brokenn.patches.jiohotstar.topbar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

// Universal fingerprints: no obfuscated class/method names.
// The 26.08 -> 26.10 change was pure R8 renaming; the code itself
// is identical in both builds. Anchors are restart-group keys,
// distinctive literals, a stable androidx call, and signatures.
// The patch body finds version-specific targets by scanning, and
// fails loudly (require) on a scan miss — never a silent mis-patch.
// Filters must be in method order; Morphe matches sequentially.
// Verified unique by full-dex scan in both builds: the content
// literal pair and the container restart key each match 1 method.

// Content layout holding the JioHotstar/Tadka pills. Patching it
// (not the whole TopBar) keeps the container height and padding.
// No clinit fingerprint: d is zeroed via its sget in b() instead,
// since <clinit> can only be found by its (renamed) class name.
object TopBarContentFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("L", "Landroidx/compose/runtime/b;", "I"),
    filters = listOf(
        literal(1673754903),
        literal(-673482817)
    )
)

// Container sizing the top-bar area. First parameter is generic
// ("L") because it changed between builds (LVe/c; -> LWe/c;).
object TopBarContainerFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("L", "Z", "L", "Landroidx/compose/runtime/b;", "I"),
    filters = listOf(
        literal(1318644515),
        methodCall(definingClass = "Landroidx/compose/foundation/layout/i;", name = "g")
    )
)

// Bottom-nav scroll listener. State method names changed
// (z2/y2 -> B2/A2), so only the Kotlin name "invokeSuspend",
// the (Object)Object signature, and the exact -50 literal are used.
// Morphe's literal is an exact match, not a substring.
object TopBarScrollFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    name = "invokeSuspend",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        literal(-50)
    )
)
