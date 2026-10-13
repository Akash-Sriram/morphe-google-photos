package app.morphe.patches.googlephotos.misc.backup

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.googlephotos.misc.extension.sharedExtensionPatch
import app.morphe.patches.googlephotos.misc.features.MfyCreationMixinFingerprint
import app.morphe.patches.googlephotos.misc.features.MfySectionDelegateFingerprint
import app.morphe.patches.googlephotos.misc.features.SaveCreationMixinFingerprint
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.cloneMutable
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val dcimAndBackupControlPatch = bytecodePatch(
    name = "DCIM & backup control",
    description = "Disables always on backup for the Camera and other DCIM folders, allowing individual folder control, " +
        "and exports memory collages and creations to DCIM/Google Photos for quota-free backup.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)
    dependsOn(sharedExtensionPatch)

    execute {
        // =========================================================================
        // 1. DCIM Folders Backup Control
        // =========================================================================
        val method = InCameraFolderSetterFingerprint.methodOrNull
        if (method != null) {
            val classDef = mutableClassDefBy(method.definingClass)

            // Find the filepath field name and descriptor type
            var filepathFieldName: String? = null
            var filepathFieldType: String? = null

            classDef.methods.forEach { m ->
                if (m.implementation?.instructions?.any {
                        it.getReference<StringReference>()?.string == "Null filepath"
                    } == true) {
                    m.implementation!!.instructions.forEach { instr ->
                        if (instr.opcode == Opcode.IPUT_OBJECT) {
                            val fieldRef = instr.getReference<FieldReference>()!!
                            filepathFieldName = fieldRef.name
                            filepathFieldType = fieldRef.type
                        }
                    }
                }
            }

            if (filepathFieldName != null && filepathFieldType != null) {
                val N = method.implementation!!.registerCount
                val isOptional = filepathFieldType.contains("Optional")

                val patchInstructions = buildList {
                    add("iget-object v$N, p0, ${classDef.type}->$filepathFieldName:$filepathFieldType")
                    add("if-eqz v$N, :cond_skip")
                    if (isOptional) {
                        add("invoke-virtual {v$N}, $filepathFieldType->isPresent()Z")
                        add("move-result v${N+1}")
                        add("if-eqz v${N+1}, :cond_skip")
                        add("invoke-virtual {v$N}, $filepathFieldType->get()Ljava/lang/Object;")
                        add("move-result-object v$N")
                    }
                    add("check-cast v$N, Ljava/lang/String;")
                    add("sget-object v${N+1}, Ljava/util/Locale;->US:Ljava/util/Locale;")
                    add("invoke-virtual {v$N, v${N+1}}, Ljava/lang/String;->toLowerCase(Ljava/util/Locale;)Ljava/lang/String;")
                    add("move-result-object v$N")
                    add("const-string v${N+1}, \"/dcim/\"")
                    add("invoke-virtual {v$N, v${N+1}}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z")
                    add("move-result v${N+1}")
                    add("if-nez v${N+1}, :cond_set_false")
                    add("const-string v${N+1}, \"/dcim\"")
                    add("invoke-virtual {v$N, v${N+1}}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z")
                    add("move-result v$N")
                    add("if-eqz v$N, :cond_skip")
                    add(":cond_set_false")
                    add("const/4 p1, 0")
                    add(":cond_skip")
                }

                val clonedMethod = method.cloneMutable(
                    additionalRegisters = 5
                )

                clonedMethod.addInstructions(0, patchInstructions.joinToString("\n"))

                classDef.methods.apply {
                    remove(method)
                    add(clonedMethod)
                }
            } else {
                throw PatchException("Could not find filepath field in Modern InCameraFolder class.")
            }
        }

        // 2. Patch the legacy DCIM check method
        val legacyMethod = LegacyDCIMCheckFingerprint.methodOrNull
        if (legacyMethod != null) {
            val legacyClassDef = mutableClassDefBy(legacyMethod.definingClass)

            val isStatic = AccessFlags.STATIC.isSet(legacyMethod.accessFlags)
            val pathRegister = if (isStatic) "p0" else "p1"

            val legacyN = legacyMethod.implementation!!.registerCount
            val legacyPatchInstructions = buildList {
                add("if-eqz $pathRegister, :cond_skip")
                add("move-object v$legacyN, $pathRegister")
                add("sget-object v${legacyN+1}, Ljava/util/Locale;->US:Ljava/util/Locale;")
                add("invoke-virtual {v$legacyN, v${legacyN+1}}, Ljava/lang/String;->toLowerCase(Ljava/util/Locale;)Ljava/lang/String;")
                add("move-result-object v$legacyN")
                add("const-string v${legacyN+1}, \"/dcim/\"")
                add("invoke-virtual {v$legacyN, v${legacyN+1}}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z")
                add("move-result v${legacyN+1}")
                add("if-nez v${legacyN+1}, :cond_return_false")
                add("const-string v${legacyN+1}, \"/dcim\"")
                add("invoke-virtual {v$legacyN, v${legacyN+1}}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z")
                add("move-result v$legacyN")
                add("if-eqz v$legacyN, :cond_skip")
                add(":cond_return_false")
                add("const/4 v$legacyN, 0")
                add("return v$legacyN")
                add(":cond_skip")
            }

            val clonedLegacyMethod = legacyMethod.cloneMutable(
                additionalRegisters = 4
            )

            clonedLegacyMethod.addInstructions(0, legacyPatchInstructions.joinToString("\n"))

            legacyClassDef.methods.apply {
                remove(legacyMethod)
                add(clonedLegacyMethod)
            }
        }

        // 3. Make BackupNotificationManager safe from null-pointer crashes during sync scheduling
        BackupNotificationManagerFingerprint.methodOrNull?.addInstructions(0, "return-void")

        // =========================================================================
        // 2. Local Creation Downloader
        // =========================================================================
        var patchedSaveMixin = false
        var patchedMfyMixin = false

        val saveMixinClassDef = SaveCreationMixinFingerprint.methodOrNull?.let { mutableClassDefBy(it.definingClass) }
        if (saveMixinClassDef != null) {
            val saveMethod = saveMixinClassDef.methods.find { m ->
                m.returnType == "Z" &&
                m.parameterTypes.size == 2 &&
                m.implementation?.instructions?.any { instruction ->
                    (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
                    ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == "SavePendingItemsOptimisticTask"
                } == true
            }

            if (saveMethod != null) {
                val mutableMethod = saveMixinClassDef.findMutableMethodOf(saveMethod)
                mutableMethod.addInstructions(
                    0,
                    """
                    invoke-static { p0, p1 }, Lapp/morphe/extension/shared/patches/LocalCreationDownloader;->onSaveRequested(Ljava/lang/Object;Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :cond_morphe_fallback
                    const/4 v0, 0x1
                    return v0
                    :cond_morphe_fallback
                    """.trimIndent(),
                )

                val isSavedMethod = saveMixinClassDef.methods.find { m ->
                    m.returnType == "Z" && m.parameterTypes.size == 1
                }
                if (isSavedMethod != null) {
                    val mutableIsSavedMethod = saveMixinClassDef.findMutableMethodOf(isSavedMethod)
                    mutableIsSavedMethod.addInstructions(
                        0,
                        """
                        invoke-static { p1 }, Lapp/morphe/extension/shared/patches/LocalCreationDownloader;->isCreationSaved(Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :cond_orig_is_saved
                        const/4 v0, 0x1
                        return v0
                        :cond_orig_is_saved
                        """.trimIndent(),
                    )
                }

                patchedSaveMixin = true
            }
        }

        val mfyMixinClassDef = MfyCreationMixinFingerprint.methodOrNull?.let { mutableClassDefBy(it.definingClass) }
        if (mfyMixinClassDef != null) {
            val mfySaveMethod = mfyMixinClassDef.methods.find { m ->
                m.returnType == "V" &&
                m.parameterTypes.size == 3 &&
                m.parameterTypes[1] == "Ljava/lang/String;" &&
                m.parameterTypes[2] == "Z"
            }
            if (mfySaveMethod != null) {
                val mutableMethod = mfyMixinClassDef.findMutableMethodOf(mfySaveMethod)
                mutableMethod.addInstructions(
                    0,
                    """
                    invoke-static/range { p0 .. p2 }, Lapp/morphe/extension/shared/patches/LocalCreationDownloader;->onMfySaveRequested(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :cond_orig_mfy
                    return-void
                    :cond_orig_mfy
                    """.trimIndent(),
                )
                patchedMfyMixin = true
            }

            val bindMethod = mfyMixinClassDef.methods.find { m ->
                m.name == "gc" || (m.parameterTypes.size == 3 && m.parameterTypes[0] == "Landroid/content/Context;" && m.parameterTypes[2] == "Landroid/os/Bundle;")
            }
            if (bindMethod != null) {
                val mutableBindMethod = mfyMixinClassDef.findMutableMethodOf(bindMethod)
                mutableBindMethod.addInstructions(
                    0,
                    """
                    invoke-static { p0 }, Lapp/morphe/extension/shared/patches/LocalCreationDownloader;->onMfyMixinBound(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }
        }

        val delegateClassDef = MfySectionDelegateFingerprint.methodOrNull?.let { mutableClassDefBy(it.definingClass) }
        if (delegateClassDef != null) {
            val cardMethod = delegateClassDef.methods.find { m ->
                m.parameterTypes.size == 2 &&
                m.parameterTypes[0] == "Ljava/lang/String;" &&
                m.returnType != "V" &&
                m.returnType != "Z"
            }
            if (cardMethod != null) {
                val mutableCardMethod = delegateClassDef.findMutableMethodOf(cardMethod)
                mutableCardMethod.addInstructions(
                    0,
                    """
                    invoke-static/range { p0 .. p2 }, Lapp/morphe/extension/shared/patches/LocalCreationDownloader;->onCheckHeroCardSaved(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            val heroSaveMethod = delegateClassDef.methods.find { m ->
                m.parameterTypes.size == 1 &&
                m.parameterTypes[0] != "Z" &&
                m.parameterTypes[0] != "I" &&
                m.parameterTypes[0] != "Ljava/lang/String;" &&
                cardMethod != null && m.parameterTypes[0] != cardMethod.returnType &&
                m.returnType == "V"
            }
            if (heroSaveMethod != null) {
                val mutableHeroSaveMethod = delegateClassDef.findMutableMethodOf(heroSaveMethod)
                mutableHeroSaveMethod.addInstructions(
                    0,
                    """
                    invoke-static/range { p0 .. p1 }, Lapp/morphe/extension/shared/patches/LocalCreationDownloader;->onCreateHeroSaveRequested(Ljava/lang/Object;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }
        }

        if (!patchedSaveMixin) {
            throw PatchException("Could not find SaveCreationMixin or its save execution method.")
        }
        if (!patchedMfyMixin) {
            throw PatchException("Could not find MFYCreationMixin or its save method.")
        }
    }
}
