package app.brokenn.patches.jiohotstar.misc.settings

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

object HSBottomNavigationFingerprint : Fingerprint(
    custom = { method, _ -> method.containsString("\$this\$HSBottomNavigation") }
)

internal fun Method.containsString(value: String): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction is ReferenceInstruction &&
            (instruction.reference as? StringReference)?.string == value
    } == true
