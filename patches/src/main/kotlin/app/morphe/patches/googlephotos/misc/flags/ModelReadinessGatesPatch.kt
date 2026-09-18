package app.morphe.patches.googlephotos.misc.flags

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

fun Method.containsStringConstant(string: String): Boolean {
    val implementation = this.implementation ?: return false
    return implementation.instructions.any { 
        (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == string 
    }
}

val modelReadinessGatesPatch = bytecodePatch(
    name = "Model Readiness Gates",
    description = "Enables intelligent on-demand AI model downloading and readiness gates for Google Photos.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)

    execute {
        classDefForEach { classDef ->
            val clinit = classDef.methods.find { it.name == "<clinit>" }
            if (clinit != null && clinit.containsStringConstant("ModelDownloadManager")) {
                val mutableClass = mutableClassDefBy(classDef)
                
                // Find method that returns the State Enum (taking 1 Model Enum param)
                val methodC = classDef.methods.find {
                    it.parameterTypes.size == 1 && 
                    it.parameterTypes[0].startsWith("L") && 
                    it.returnType.startsWith("L") &&
                    it.returnType != it.parameterTypes[0] &&
                    !it.returnType.startsWith("Ljava/") // not returning String or Object
                }
                
                if (methodC != null) {
                    val returnTypeObj = methodC.returnType
                    val modelEnumParam = methodC.parameterTypes[0]

                    // Return LOADED (:e) if files exist on disk, else NEEDS_DOWNLOAD (:b)
                    mutableClass.findMutableMethodOf(methodC).addInstructions(0, """
                        invoke-static { p1 }, Lapp/morphe/extension/shared/patches/PhotosModelSeeder;->isModelReady(Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :cond_needs_download
                        sget-object v0, $returnTypeObj->e:$returnTypeObj
                        return-object v0
                        :cond_needs_download
                        sget-object v0, $returnTypeObj->b:$returnTypeObj
                        return-object v0
                    """.trimIndent())

                    // Find method that initiates download: takes (ModelEnum, Continuation) and returns Object
                    val methodL = classDef.methods.find {
                        it.parameterTypes.size == 2 &&
                        it.parameterTypes[0] == modelEnumParam &&
                        it.returnType == "Ljava/lang/Object;" &&
                        it.containsStringConstant("ModelDownloadManager")
                    }

                    if (methodL != null) {
                        mutableClass.findMutableMethodOf(methodL).addInstructions(0, """
                            invoke-static { p1 }, Lapp/morphe/extension/shared/patches/PhotosModelSeeder;->triggerOnDemandDownload(Ljava/lang/Object;)V
                            const/4 v0, 0x0
                            return-object v0
                        """.trimIndent())
                    }
                }

                // Find method that returns boolean isReady (taking 1 Model Enum param)
                val methodQ = classDef.methods.find {
                    it.parameterTypes.size == 1 && 
                    it.parameterTypes[0].startsWith("L") && 
                    it.returnType == "Z"
                }
                
                if (methodQ != null) {
                    mutableClass.findMutableMethodOf(methodQ).addInstructions(0, """
                        invoke-static { p1 }, Lapp/morphe/extension/shared/patches/PhotosModelSeeder;->isModelReady(Ljava/lang/Object;)Z
                        move-result v0
                        return v0
                    """.trimIndent())
                }
            }
        }
    }
}
