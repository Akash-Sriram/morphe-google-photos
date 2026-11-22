package app.morphe.patches.googlephotos.misc.gms

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.googlephotos.misc.bento.BentoModelBuilderFingerprint
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal fun BytecodePatchContext.applyGmsCoreAvatar() {
    // 1. Account Avatar initialization
    HomeActivityOnCreateFingerprint.method.addInstructions(
        0,
        """
        invoke-static/range { p0 .. p0 }, Lapp/morphe/extension/shared/patches/GooglePhotosAccountAvatar;->install(Landroid/app/Activity;)V
        """.trimIndent(),
    )

    // 1.1 OneGoogle Blue Aura Ring for Avatar & Account Switcher
    classDefByOrNull("Lcom/google/android/libraries/onegoogle/account/disc/AccountParticleDisc;")?.let { apdClassDef ->
        val mutableApd = mutableClassDefBy(apdClassDef)

        // Hook 5-arg constructor: AccountParticleDisc(Context, AttributeSet, int, boolean, boolean)
        // Register p5 is the boolean flag for Aura Blue Ring (q).
        apdClassDef.methods.firstOrNull { method ->
            method.name == "<init>" && method.parameterTypes.toList() == listOf(
                "Landroid/content/Context;",
                "Landroid/util/AttributeSet;",
                "I",
                "Z",
                "Z",
            )
        }?.let { apdInit5 ->
            val mutableInit5 = mutableApd.findMutableMethodOf(apdInit5)
            val superIndex = mutableInit5.implementation?.instructions?.indexOfFirst {
                it.opcode == Opcode.INVOKE_DIRECT &&
                    (it as? ReferenceInstruction)?.reference is MethodReference &&
                    ((it as ReferenceInstruction).reference as MethodReference).name == "<init>"
            } ?: -1
            val insertIndex = if (superIndex != -1) superIndex + 1 else 0
            mutableInit5.addInstructions(
                insertIndex,
                """
                invoke-static {p1}, Lapp/morphe/extension/shared/patches/GooglePhotosAccountAvatar;->isBlueRingEnabled(Landroid/content/Context;)Z
                move-result p5
                """.trimIndent(),
            )
        }

        // Hook 4-arg constructor: AccountParticleDisc(Context, AttributeSet, int, boolean)
        // It calls csya.d(context) to pass z2 to the 5-arg constructor.
        // Dynamically locate csya.d(Context) from this call.
        apdClassDef.methods.firstOrNull { method ->
            method.name == "<init>" && method.parameterTypes.toList() == listOf(
                "Landroid/content/Context;",
                "Landroid/util/AttributeSet;",
                "I",
                "Z",
            )
        }?.let { apdInit4 ->
            val csyaCall = apdInit4.implementation?.instructions?.firstOrNull { instr ->
                val ref = (instr as? ReferenceInstruction)?.reference as? MethodReference
                instr.opcode == Opcode.INVOKE_STATIC &&
                    ref != null &&
                    ref.parameterTypes.toList() == listOf("Landroid/content/Context;") &&
                    ref.returnType == "Z"
            }
            val methodRef = (csyaCall as? ReferenceInstruction)?.reference as? MethodReference
            if (methodRef != null) {
                classDefByOrNull(methodRef.definingClass)?.let { csyaClassDef ->
                    val csyaMethod = csyaClassDef.methods.firstOrNull { it.name == methodRef.name }
                    if (csyaMethod != null) {
                        val mutableCsya = mutableClassDefBy(csyaClassDef).findMutableMethodOf(csyaMethod)
                        mutableCsya.addInstructions(
                            0,
                            """
                            invoke-static {p0}, Lapp/morphe/extension/shared/patches/GooglePhotosAccountAvatar;->isBlueRingEnabled(Landroid/content/Context;)Z
                            move-result v0
                            return v0
                            """.trimIndent(),
                        )
                    }
                }
            }
        }
    }

    // 2. Google One Bento Badge
    val targetBentoMethod = BentoModelBuilderFingerprint.methodOrNull
    if (targetBentoMethod != null) {
        val bentoClass = mutableClassDefBy(targetBentoMethod.definingClass)
        val bentoMethod = bentoClass.findMutableMethodOf(targetBentoMethod)
        val instructions = targetBentoMethod.implementation?.instructions?.toList()
        if (instructions != null) {
            val fallbackIndex = instructions.indexOfFirst { inst ->
                inst.opcode == Opcode.CONST_16 &&
                    (inst as? NarrowLiteralInstruction)?.narrowLiteral == 0 &&
                    instructions.getOrNull(instructions.indexOf(inst) + 1)?.let { nextInst ->
                        nextInst.opcode == Opcode.IF_EQZ &&
                            (nextInst as? OneRegisterInstruction)?.registerA == (inst as? OneRegisterInstruction)?.registerA
                    } == true
            }
            if (fallbackIndex != -1) {
                val targetReg = (instructions[fallbackIndex] as OneRegisterInstruction).registerA
                val valueOfIndex = (fallbackIndex - 1 downTo 0).firstOrNull { i ->
                    val inst = instructions[i]
                    inst.opcode == Opcode.INVOKE_STATIC &&
                        (inst as? ReferenceInstruction)?.reference?.toString()?.contains("Integer;->valueOf(I)Ljava/lang/Integer;") == true
                }
                if (valueOfIndex != null) {
                    val intReg = (instructions[valueOfIndex] as? FiveRegisterInstruction)?.registerC
                    val stringInitIndex = (valueOfIndex - 1 downTo 0).firstOrNull { i ->
                        val inst = instructions[i]
                        inst.opcode == Opcode.INVOKE_DIRECT &&
                            (inst as? ReferenceInstruction)?.reference?.toString()?.contains("<init>(Ljava/lang/String;)V") == true
                    }
                    if (stringInitIndex != null && intReg != null) {
                        val stringReg = (instructions[stringInitIndex] as? FiveRegisterInstruction)?.registerD
                        var entryIndex = -1
                        for (i in stringInitIndex - 1 downTo 0) {
                            if (instructions[i].opcode == Opcode.NEW_INSTANCE) {
                                entryIndex = i
                            } else if (entryIndex != -1) {
                                break
                            }
                        }
                        if (entryIndex != -1 && stringReg != null) {
                            var accountReg = 14
                            for (i in entryIndex - 1 downTo 0) {
                                if (instructions[i].opcode == Opcode.IGET_BOOLEAN) {
                                    accountReg = (instructions[i] as TwoRegisterInstruction).registerB
                                    break
                                }
                            }
                            bentoMethod.addInstructionsAtControlFlowLabel(
                                fallbackIndex,
                                """
                                invoke-static { v$accountReg }, Lapp/morphe/extension/shared/patches/BentoDecorationPatch;->getBadgeText(Ljava/lang/Object;)Ljava/lang/String;
                                move-result-object v$stringReg
                                if-eqz v$stringReg, :no_badge
                                const/4 v$intReg, 1
                                goto :build_badge
                                :no_badge
                                const/16 v$targetReg, 0
                                """.trimIndent(),
                                ExternalLabel("build_badge", bentoMethod.getInstruction(entryIndex)),
                            )
                        }
                    }
                }
            }
        }
    }
}
