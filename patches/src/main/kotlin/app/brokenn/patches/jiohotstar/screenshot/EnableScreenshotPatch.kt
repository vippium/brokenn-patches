package app.brokenn.patches.jiohotstar.screenshot

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.brokenn.patches.jiohotstar.shared.Constants.COMPATIBILITY_JIOHOTSTAR
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

private val logger = Logger.getLogger("app.brokenn.jiohotstar.screenshot")

private fun MutableMethod.disableSurfaceSecure(): Boolean {
    val instructions = instructionsOrNull?.toList() ?: return false

    val targetIndex = instructions.indexOfFirst { instruction ->
        instruction is ReferenceInstruction &&
            (instruction.reference as? MethodReference)?.let { reference ->
                reference.name == "setSecure" &&
                    reference.definingClass == "Landroid/view/SurfaceView;"
            } == true
    }
    if (targetIndex < 0) return false

    removeInstruction(targetIndex)
    return true
}

@Suppress("unused")
val enableScreenshotPatch = bytecodePatch(
    name = "Enable screenshots",
    description = "Allows screenshots and screen recording, including during playback. " +
        "- Will not work on DRM-protected content (Widevine L1).",
) {
    compatibleWith(COMPATIBILITY_JIOHOTSTAR)

    execute {
        logger.info("[screenshot] app version ${packageMetadata.versionName}")

        // Layer 1: the Activity window FLAG_SECURE (0x2000). Absent means nothing to do.
        runCatching {
            ComposeFlagSecureFingerprint.method.addInstructions(
                0,
                """
                    return-void
                """,
            )
            logger.info("[screenshot] window FLAG_SECURE setter neutralised")
        }.onFailure {
            logger.warning("[screenshot] window FLAG_SECURE setter not found (skipped): $it")
        }

        // Layer 2: the app's own AB switch that gates the legacy secure surface.
        runCatching {
            UseSecureViewFingerprint.method.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """,
            )
            logger.info("[screenshot] ABConfig.useSecureView forced false")
        }.onFailure {
            logger.warning("[screenshot] ABConfig.useSecureView not found (skipped): $it")
        }

        // Layer 3: every method that marks a SurfaceView as secure. Matched by behaviour, so
        // new player paths (Exo, Media3, tiledmedia) are covered without renaming anything.
        val candidates = SurfaceSetSecureFingerprint.matchAllOrNull().orEmpty()
        if (candidates.isEmpty()) {
            logger.warning(
                "[screenshot] no SurfaceView.setSecure call site found " +
                    "(patch skipped - player may have changed)",
            )
            return@execute
        }

        var patched = 0
        candidates.forEach { candidate ->
            val method = candidate.method
            val methodRef = "${method.definingClass}->${method.name}"
            runCatching {
                if (method.disableSurfaceSecure()) {
                    patched++
                    logger.info("[screenshot] surface secure disabled in $methodRef")
                } else {
                    logger.warning("[screenshot] $methodRef matched but call not located")
                }
            }.onFailure {
                logger.warning("[screenshot] $methodRef: failed to patch: $it")
            }
        }

        logger.info(
            "[screenshot] disabled surface secure in $patched of ${candidates.size} call site(s)",
        )
    }
}
