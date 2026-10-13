package app.morphe.patches.googlephotos.misc.models

import app.morphe.patches.googlephotos.misc.extension.sharedExtensionPatch
import app.morphe.patches.googlephotos.misc.gms.HomeActivityOnCreateFingerprint
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findMutableMethodOf
import app.morphe.util.returnEarly
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
    description = "Bypasses the 0MB Mobile Data Download check for AI models and reports them as loaded.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)
    dependsOn(sharedExtensionPatch)

    execute {
        HomeActivityOnCreateFingerprint.method.addInstructions(
            0,
            """
            invoke-static/range { p0 .. p0 }, Lapp/morphe/extension/shared/patches/PhotosModelSeeder;->enableAndEnsureSeeded(Landroid/content/Context;)V
            """.trimIndent(),
        )

        val clinit = ModelDownloadManagerClinitFingerprint.methodOrNull
        if (clinit != null) {
            val classDef = mutableClassDefBy(clinit.definingClass)
            
            // Find method that returns the State Enum (taking 1 Model Enum param)
            val methodC = classDef.methods.firstOrNull {
                it.parameterTypes.size == 1 && 
                it.parameterTypes[0].startsWith("L") && 
                it.returnType.startsWith("L") &&
                it.returnType != it.parameterTypes[0] &&
                !it.returnType.startsWith("Ljava/") // not returning String or Object
            }
            
            if (methodC != null) {
                val returnTypeObj = methodC.returnType
                classDef.findMutableMethodOf(methodC).addInstructions(0, """
                    sget-object v0, $returnTypeObj->e:$returnTypeObj
                    return-object v0
                """.trimIndent())
            }

            // Find method that returns boolean (taking 1 Model Enum param)
            val methodQ = classDef.methods.firstOrNull {
                it.returnType == "Z" &&
                it.parameterTypes.size == 1 && 
                it.parameterTypes[0].startsWith("L")
            }
            
            if (methodQ != null) {
                classDef.findMutableMethodOf(methodQ).returnEarly(true)
            }
        }
    }
}
