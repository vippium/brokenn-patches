package app.brokenn.patches.octogram.content

import app.brokenn.patches.octogram.shared.Constants.COMPATIBILITY_OCTOGRAM
import app.brokenn.patches.octogram.shared.stringConstants
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.ClassDef

// Locates MessagesStorage by its message-delete SQL, then patches the
// markMessagesAsDeleted(long, ArrayList, boolean, boolean, int, int) wrapper.
// p4 = useQueue: true = user-initiated delete, false = server push.
@Suppress("unused")
val antiDeleteMessagesPatch = bytecodePatch(
    name = "Anti-delete messages",
    description = "Prevents server-deleted messages from being removed locally.",
) {
    compatibleWith(COMPATIBILITY_OCTOGRAM)

    execute {
        val storageClasses = mutableListOf<ClassDef>()
        classDefForEach { c ->
            if (c.methods.any { "DELETE FROM tag_message_id WHERE mid IN(%s)" in it.stringConstants() }) {
                storageClasses.add(c)
            }
        }
        if (storageClasses.isEmpty()) {
            throw PatchException("MessagesStorage class not found")
        }

        val expected = listOf("J", "Ljava/util/ArrayList;", "Z", "Z", "I", "I")
        var patched = 0
        for (c in storageClasses) {
            mutableClassDefBy(c).methods.filter {
                it.returnType == "Ljava/util/ArrayList;" && it.parameters.toList() == expected
            }.forEach { m ->
                // useQueue == false means server-initiated delete → keep the rows, return null.
                m.addInstructions(0, """
                    if-nez p4, :allow
                    const/4 v0, 0x0
                    return-object v0
                    :allow
                    nop
                """)
                patched++
            }
        }
        if (patched == 0) {
            throw PatchException("markMessagesAsDeleted wrapper not found in MessagesStorage")
        }
    }
}
