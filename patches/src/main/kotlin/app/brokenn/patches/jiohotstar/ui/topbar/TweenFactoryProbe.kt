package app.brokenn.patches.jiohotstar.ui.topbar

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val HEADER_WIDGET = "Lcom/hotstar/bff/models/widget/BffBrandedLogoHeaderWidget;"

internal fun BytecodePatchContext.discoverTweenFactory(): Pair<String, String> {
    val easingTypes = mutableSetOf<String>()
    classDefForEach { def ->
        val methods = def.methods.toList()
        if (methods.size == 1 && methods[0].returnType == "F" &&
            methods[0].parameterTypes.map { it.toString() } == listOf("F")
        ) easingTypes.add(def.type)
    }

    val specEasing = mutableMapOf<String, String>()
    classDefForEach { def ->
        val fields = def.fields.toList()
        if (fields.size != 3) return@classDefForEach
        val methodNames = def.methods.map { it.name }
        if ("equals" !in methodNames && "hashCode" !in methodNames) return@classDefForEach
        val easingField = fields.firstOrNull { it.type in easingTypes } ?: return@classDefForEach
        if (fields.count { it.type == "I" } != 2) return@classDefForEach
        specEasing[def.type] = easingField.type
    }

    val factories = mutableListOf<Pair<String, String>>()
    classDefForEach { def ->
        def.methods.forEach { method ->
            val impl = method.implementation ?: return@forEach
            val easing = specEasing[method.returnType] ?: return@forEach
            if (method.parameterTypes.map { it.toString() } != listOf("I", "I", easing, "I")) {
                return@forEach
            }
            if (!AccessFlags.STATIC.isSet(method.accessFlags)) return@forEach
            var buildsSpec = false
            var passesDuration = false
            impl.instructions.forEach { ins ->
                val ref = (ins as? ReferenceInstruction)?.reference
                when (ins.opcode) {
                    Opcode.NEW_INSTANCE ->
                        buildsSpec = (ref as? TypeReference)?.type == method.returnType
                    Opcode.INVOKE_DIRECT -> {
                        val mr = ref as? MethodReference
                        passesDuration = mr != null && mr.name == "<init>" &&
                            mr.definingClass == method.returnType &&
                            mr.parameterTypes.map { it.toString() } == listOf("I", "I", easing)
                    }
                    else -> {}
                }
            }
            if (buildsSpec && passesDuration) factories.add(def.type to method.name)
        }
    }

    if (factories.size != 1) {
        throw PatchException("TopBar: expected exactly one Compose tween factory, found ${factories.size}")
    }
    return factories.single()
}

internal fun BytecodePatchContext.findHeaderTransitionMethod(
    factoryClass: String,
    factoryMethod: String,
): MutableMethod {
    val matches = mutableListOf<Pair<String, String>>()
    classDefForEach { def ->
        def.methods.forEach { method ->
            if (method.returnType != "V") return@forEach
            val flags = method.accessFlags
            if (!AccessFlags.PUBLIC.isSet(flags) || !AccessFlags.STATIC.isSet(flags) ||
                !AccessFlags.FINAL.isSet(flags)
            ) return@forEach
            if (method.parameterTypes.none { it.toString() == HEADER_WIDGET }) return@forEach
            val impl = method.implementation ?: return@forEach
            val callsFactory = impl.instructions.any { ins ->
                ins.opcode == Opcode.INVOKE_STATIC &&
                    (ins as? ReferenceInstruction)?.reference.let { ref ->
                        ref is MethodReference && ref.definingClass == factoryClass &&
                            ref.name == factoryMethod
                    }
            }
            if (callsFactory) matches.add(def.type to method.name)
        }
    }

    if (matches.size != 1) {
        throw PatchException("TopBar: expected exactly one header transition method, found ${matches.size}")
    }
    val (classType, methodName) = matches.single()
    return mutableClassDefBy(classType).methods.first {
        it.name == methodName && it.returnType == "V"
    }
}
