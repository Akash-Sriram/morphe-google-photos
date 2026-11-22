package app.morphe.patches.googlephotos.misc.gms

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal fun BytecodePatchContext.applyGmsCoreBackup() {
    // 1. Backup Settings: Sanitize GMS Core backup settings (uyc) on MicroG/non-certified devices
    GmsCoreBackupAccountSyncFingerprint.methodOrNull?.let { syncMethod ->
        val syncClassDef = classDefBy(syncMethod.definingClass)
        val mutableSyncClass = mutableClassDefBy(syncClassDef)
        val mutableSyncMethod = mutableSyncClass.findMutableMethodOf(syncMethod)
        val instructions = syncMethod.implementation?.instructions?.toList()
        if (instructions != null) {
            val emailIgetInst = instructions.mapNotNull { inst ->
                if (inst.opcode == Opcode.IGET_OBJECT &&
                    ((inst as? ReferenceInstruction)?.reference as? FieldReference)?.type == "Ljava/lang/String;"
                ) inst else null
            }.firstOrNull()

            val emailFr = (emailIgetInst as? ReferenceInstruction)?.reference as? FieldReference
            val checkCastType = emailFr?.definingClass
            val gmsSettingsReg = (emailIgetInst as? TwoRegisterInstruction)?.registerB

            val emailIgetIndex = emailIgetInst?.let { instructions.indexOf(it) } ?: -1
            val checkCastIndex = if (emailIgetIndex != -1 && checkCastType != null) {
                (emailIgetIndex downTo 0).firstOrNull {
                    instructions[it].opcode == Opcode.CHECK_CAST &&
                        ((instructions[it] as? ReferenceInstruction)?.reference as? TypeReference)?.type == checkCastType
                }
            } else null

            val contextField = syncClassDef.fields.firstOrNull { it.type == "Landroid/content/Context;" }?.name
            val thisReg = syncMethod.implementation!!.registerCount - syncMethod.parameters.size - 1

            if (checkCastIndex != null && gmsSettingsReg != null && contextField != null && checkCastType != null) {
                mutableSyncMethod.addInstructions(
                    checkCastIndex + 1,
                    """
                    iget-object v5, v$thisReg, ${syncMethod.definingClass}->$contextField:Landroid/content/Context;
                    invoke-static {v$gmsSettingsReg, v5}, Lapp/morphe/extension/shared/patches/backup/PhotosBackupHelper;->sanitizeGmsBackupSettings(Ljava/lang/Object;Landroid/content/Context;)Ljava/lang/Object;
                    move-result-object v$gmsSettingsReg
                    check-cast v$gmsSettingsReg, $checkCastType
                    """.trimIndent(),
                )
            }

            // 2. Backup Preferences & State: Dynamically resolve Ludh and Luvh, and sanitize + trace
            val wMethod = syncClassDef.methods.firstOrNull {
                it.parameterTypes.size == 2 && it.parameterTypes[0] == "Ljava/lang/Boolean;"
            }
            val ludhClassName = wMethod?.parameterTypes?.getOrNull(1)
            val uvhMethodRef = wMethod?.implementation?.instructions?.mapNotNull {
                if (it.opcode == Opcode.INVOKE_STATIC) {
                    (it as? ReferenceInstruction)?.reference as? MethodReference
                } else null
            }?.firstOrNull()

            // Hook Entry of Luvh.a(Ludh, Boolean)Luvk
            if (uvhMethodRef != null && ludhClassName != null) {
                val uvhClassDef = classDefBy(uvhMethodRef.definingClass)
                val mutableUvhClass = mutableClassDefBy(uvhClassDef)
                val uvhMethod = uvhClassDef.methods.first { it.name == uvhMethodRef.name && it.parameterTypes == uvhMethodRef.parameterTypes }
                val mutableUvhMethod = mutableUvhClass.findMutableMethodOf(uvhMethod)
                val ludhParamReg = uvhMethod.implementation!!.registerCount - uvhMethod.parameters.size
                val boolParamReg = ludhParamReg + 1
                mutableUvhMethod.addInstructions(
                    0,
                    """
                    invoke-static/range {v$ludhParamReg .. v$boolParamReg}, Lapp/morphe/extension/shared/patches/backup/PhotosBackupHelper;->onLuvhAEntry(Ljava/lang/Object;Ljava/lang/Boolean;)Ljava/lang/Object;
                    move-result-object v$ludhParamReg
                    check-cast v$ludhParamReg, $ludhClassName
                    """.trimIndent(),
                )
            }
        }
    }

    // 3. Backup Settings: Observe & organically guard LaunchedEffect account chooser dialog
    BackupSettingsEntryLaunchedEffectFingerprint.methodOrNull?.let { effectMethod ->
        val effectClass = mutableClassDefBy(effectMethod.definingClass)
        val mutableEffectMethod = effectClass.findMutableMethodOf(effectMethod)
        val instructions = effectMethod.implementation?.instructions?.toList()
        if (instructions != null) {
            val isBackupEnabledIndices = instructions.mapIndexedNotNull { idx, inst ->
                if (inst.opcode == Opcode.INVOKE_INTERFACE &&
                    ((inst as? ReferenceInstruction)?.reference as? MethodReference)?.let { ref ->
                        ref.parameterTypes.isEmpty() && ref.returnType == "Z"
                    } == true
                ) idx else null
            }
            for (isBackupEnabledIndex in isBackupEnabledIndices.reversed()) {
                val moveResultRel = instructions.drop(isBackupEnabledIndex + 1).indexOfFirst {
                    it.opcode == Opcode.MOVE_RESULT
                }
                if (moveResultRel != -1) {
                    val moveResultIndex = isBackupEnabledIndex + 1 + moveResultRel
                    val reg = (instructions[moveResultIndex] as OneRegisterInstruction).registerA
                    mutableEffectMethod.addInstructions(
                        moveResultIndex + 1,
                        """
                        invoke-static {v$reg, p0}, Lapp/morphe/extension/shared/patches/backup/PhotosBackupHelper;->onCheckBackupAccountLinked(ZLjava/lang/Object;)Z
                        move-result v$reg
                        """.trimIndent(),
                    )
                }
            }
        }
    }
}
