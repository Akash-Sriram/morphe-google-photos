package app.morphe.patches.googlephotos.misc.flags

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.cloneMutable
import app.morphe.util.findMutableMethodOf
import app.morphe.util.returnEarly

val modelReadinessGatesPatch = bytecodePatch(
    name = "Model Readiness Gates",
    description = "Bypasses the 0MB Mobile Data Download check for AI models and reports them as loaded.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)

    execute {
        getAllClasses().forEach { classDef ->
            when {
                classDef.type == "Laspz;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.name == "c" && method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        }
                    }
                }

                classDef.type == "Larea;" -> {
                    val mutableClass = mutableClassDefBy(classDef)
                    val methodC = classDef.methods.find {
                        it.name == "c" && it.parameterTypes == listOf("Lchoo;") && it.returnType == "Laqta;"
                    }
                    if (methodC != null) {
                        val clonedC = methodC.cloneMutable(additionalRegisters = 0)
                        clonedC.addInstructions(0, """
                            sget-object p1, Laqta;->e:Laqta;
                            return-object p1
                        """.trimIndent())
                        mutableClass.methods.remove(methodC)
                        mutableClass.methods.add(clonedC)
                    }

                    val methodQ = classDef.methods.find {
                        it.name == "q" && it.parameterTypes == listOf("Lchoo;") && it.returnType == "Z"
                    }
                    if (methodQ != null) {
                        mutableClass.findMutableMethodOf(methodQ).returnEarly(true)
                    }
                }
            }
        }
    }
}
