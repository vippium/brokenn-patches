package app.brokenn.patches.jiohotstar.settings

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * The HSBottomNavigation composable. Anchored on the Compose compiler-emitted
 * composable name, which survives obfuscation.
 */
object HSBottomNavigationFingerprint : Fingerprint(
    custom = { method, _ -> method.containsString("\$this\$HSBottomNavigation") }
)

internal fun Method.containsString(value: String): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction is ReferenceInstruction &&
            (instruction.reference as? StringReference)?.string == value
    } == true
