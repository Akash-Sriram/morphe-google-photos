package app.morphe.patches.googlephotos.misc.gms

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patches.googlephotos.misc.gms.Constants.PHOTOS_PACKAGE_NAME
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal fun BytecodePatchContext.applyGmsCoreSystemCompat() {
    // 1. Fast metadata scan: Google Play Services availability & signature check hooks
    classDefForEach { classDef ->
        val mutableClass by lazy { mutableClassDefBy(classDef) }

        classDef.methods.forEach classLoop@{ method ->
            // Check A: Google Play Services availability check
            val isAvailabilityCheck = method.returnType == "I" &&
                (
                    (method.parameterTypes.size == 2 && method.parameterTypes[0] == "Landroid/content/Context;" && method.parameterTypes[1] == "I") ||
                    (method.parameterTypes.size == 1 && method.parameterTypes[0] == "Landroid/content/Context;")
                )
            if (isAvailabilityCheck) {
                val impl = method.implementation
                if (impl != null && impl.instructions.any { instr ->
                    val str = (instr.getReference<StringReference>())?.string
                    str?.contains("android.gms") == true || str?.contains("GooglePlayServices") == true
                }) {
                    mutableClass.findMutableMethodOf(method).returnEarly(0)
                    return@classLoop
                }
            }

            // Check B: PackageInfo signature verification
            val isSignatureCheck = method.returnType == "Z" &&
                method.parameterTypes.size in 1..2 &&
                method.parameterTypes[0] == "Landroid/content/pm/PackageInfo;"
            if (isSignatureCheck) {
                mutableClass.findMutableMethodOf(method).returnEarly(true)
                return@classLoop
            }
        }
    }

    // 2. Disable the AccountValidityMonitor check that runs on resume
    val avmClass = mutableClassDefBy(AccountValidityMonitorFingerprint.method.definingClass)
    val checkAccountMethod = avmClass.methods.first {
        it.name == "d" && it.parameterTypes.isEmpty() && it.returnType == "V"
    }
    checkAccountMethod.addInstruction(
        0,
        "return-void",
    )

    // 3. Keep the frictionless eligibility result intact, but prevent the
    //    MicroG failure path from clearing the selected account.
    val frictionlessClass = mutableClassDefBy(FrictionlessEligibilityTaskFingerprint.method.definingClass)
    val eligibilityMethod = frictionlessClass.methods.first {
        it.name == "f" && it.parameterTypes.isEmpty() && it.returnType == "Z"
    }
    eligibilityMethod.apply {
        val clearSelectedAccountIndex = indexOfFirstInstructionOrThrow {
            getReference<MethodReference>()?.let { ref ->
                ref.name == "o" &&
                    ref.returnType == "V" &&
                    ref.parameterTypes.toList() == listOf("I")
            } == true
        }
        val accountHandlerClass = getInstruction(clearSelectedAccountIndex)
            .getReference<MethodReference>()!!
            .definingClass

        replaceInstruction(clearSelectedAccountIndex, "invoke-virtual {p0}, $accountHandlerClass->p()V")
    }

    // 4. Bypass concurrent permission request crash guard (Cannot request permissions X when already requested Y)
    val permCoordMethod = PermissionCoordinatorRequestPermissionsFingerprint.methodOrNull
    if (permCoordMethod != null) {
        val mutableClass = mutableClassDefBy(permCoordMethod.definingClass)
        val mutableMethod = mutableClass.findMutableMethodOf(permCoordMethod)
        val instructions = permCoordMethod.implementation?.instructions?.toList()
        if (instructions != null) {
            val sizeIndex = instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_VIRTUAL &&
                    (it as? ReferenceInstruction)?.reference?.toString()?.contains("SparseArray;->size()I") == true
            }
            if (sizeIndex != -1) {
                val branchRel = instructions.drop(sizeIndex).indexOfFirst {
                    it.opcode.name.startsWith("if-")
                }
                if (branchRel != -1) {
                    val branchIndex = sizeIndex + branchRel
                    mutableMethod.replaceInstruction(branchIndex, "nop")
                }
            }
        }
    }

    // 5. Google Lens Activity Binder caller_package spoofing for renamed packages
    val lensBinderMethod = LensActivityBinderHandshakeFingerprint.methodOrNull
    if (lensBinderMethod != null) {
        val mutableClass = mutableClassDefBy(lensBinderMethod.definingClass)
        val mutableMethod = mutableClass.findMutableMethodOf(lensBinderMethod)
        val instructions = lensBinderMethod.implementation?.instructions?.toList()
        if (instructions != null) {
            val getPkgIndex = instructions.indexOfFirst {
                val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
                ref?.name == "getPackageName" && ref.definingClass == "Landroid/content/Context;"
            }
            if (getPkgIndex != -1 && getPkgIndex + 1 < instructions.size) {
                val moveResult = instructions[getPkgIndex + 1]
                if (moveResult.opcode == Opcode.MOVE_RESULT_OBJECT) {
                    val reg = (moveResult as OneRegisterInstruction).registerA
                    mutableMethod.replaceInstruction(
                        getPkgIndex,
                        "const-string v$reg, \"$PHOTOS_PACKAGE_NAME\"",
                    )
                    mutableMethod.replaceInstruction(
                        getPkgIndex + 1,
                        "nop",
                    )
                }
            }
        }
    }

    // 6. Suppress repeated 24-hour notification permission prompt after denial
    SecondChanceNotificationOptInFingerprint.methodOrNull?.let { clinitMethod ->
        val arktClass = mutableClassDefBy(clinitMethod.definingClass)
        for (method in arktClass.methods) {
            if (method.name == "go" && method.parameterTypes.toList() == listOf("Landroid/os/Bundle;")) {
                method.addInstructions(
                    0,
                    """
                    return-void
                    """.trimIndent(),
                )
            }
            val hasPostNotif = method.implementation?.instructions?.any { instr ->
                (instr as? ReferenceInstruction)?.reference?.let { ref ->
                    (ref as? StringReference)?.string == "android.permission.POST_NOTIFICATIONS"
                } == true
            } == true
            if (hasPostNotif) {
                method.addInstructions(
                    0,
                    """
                    return-void
                    """.trimIndent(),
                )
            }
        }
    }
}
