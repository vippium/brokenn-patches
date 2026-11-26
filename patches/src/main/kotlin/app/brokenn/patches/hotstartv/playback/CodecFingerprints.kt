package app.brokenn.patches.hotstartv.playback

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal object Is4kSupportedFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        string("4k"),
    )
)

internal object IsCodecSupportedFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    filters = listOf(
        string("supportsCodec Picked from cache")
    )
)

internal object IsDolbyVisionSupportedFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;", "Z", "I", "Z"),
    filters = listOf(
        string("dv"),
        string("bklst"),
    )
)

internal object IsHdr10SupportedFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;", "Z", "I", "Z"),
    filters = listOf(
        string("hdr10"),
        string("bklst"),
    ),
    // The HDR10 check (codec hdrType 2) caches its verdict into static field
    // `i`; the sibling HLG check (hdrType 4) caches into `j`. Both carry the
    // same "hdr10"/"bklst" strings, so pin the match on the cache field.
    custom = { method, _ ->
        method.implementation?.instructions?.any { ins ->
            val ref = (ins as? ReferenceInstruction)?.reference as? FieldReference
            ref != null &&
                ref.definingClass == "Lcom/hotstar/player/utils/CapabilitiesUtils;" &&
                ref.name == "i"
        } == true
    }
)
