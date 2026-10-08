package app.morphe.patches.googlephotos.misc.features

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.googlephotos.misc.extension.sharedExtensionPatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val localCreationDownloaderPatch = bytecodePatch(
    name = "Local creation downloader",
    description = "Intercepts saving memory collages and creations, exporting them to the device Google Photos folder (DCIM/Google Photos) for quota-free backup instead of direct cloud library commits.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)
    dependsOn(sharedExtensionPatch)

    execute {
        var patchedSaveMixin = false
        var patchedMfyMixin = false
        var patchedMemoriesController = false

        classDefForEach { classDef ->
            if (classDef.type.startsWith("Lapp/morphe/")) return@classDefForEach

            val stringConstants = buildSet {
                classDef.methods.forEach { method ->
                    method.implementation?.instructions?.forEach { instruction ->
                        if (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) {
                            ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string?.let { add(it) }
                        }
                    }
                }
            }

            // 1. Identify SaveCreationMixin: class containing string constant "SaveCreationMixin"
            if ("SaveCreationMixin" in stringConstants) {
                val saveMethod = classDef.methods.find { method ->
                    method.returnType == "Z" &&
                    method.parameterTypes.size == 2 &&
                    method.implementation?.instructions?.any { instruction ->
                        (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
                        ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == "SavePendingItemsOptimisticTask"
                    } == true
                }

                if (saveMethod != null) {
                    val mutableClass = mutableClassDefBy(classDef)
                    val mutableMethod = mutableClass.findMutableMethodOf(saveMethod)

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

                    val isSavedMethod = classDef.methods.find { method ->
                        method.returnType == "Z" && method.parameterTypes.size == 1
                    }
                    if (isSavedMethod != null) {
                        val mutableIsSavedMethod = mutableClass.findMutableMethodOf(isSavedMethod)
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

            // 2. Identify MFYCreationMixin: Made-For-You creations in Create tab
            if ("MFYCreationMixin" in stringConstants) {
                val mfySaveMethod = classDef.methods.find { method ->
                    method.returnType == "V" &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[1] == "Ljava/lang/String;" &&
                    method.parameterTypes[2] == "Z"
                }
                if (mfySaveMethod != null) {
                    val mutableClass = mutableClassDefBy(classDef)
                    val mutableMethod = mutableClass.findMutableMethodOf(mfySaveMethod)
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
            }

            // 3. Identify Memories Controller (Lamfd): Create tab hero "Save" button and memory collections
            if ("Failed to load the MemoriesKeyFeature to the collection." in stringConstants) {
                val mutableClass = mutableClassDefBy(classDef)

                // Collection save method: f(int, account, collection)
                val collSaveMethod = classDef.methods.find { method ->
                    method.returnType == "V" &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == "I"
                }
                if (collSaveMethod != null) {
                    val mutableCollMethod = mutableClass.findMutableMethodOf(collSaveMethod)
                    mutableCollMethod.addInstructions(
                        0,
                        """
                        invoke-static/range { p0 .. p3 }, Lapp/morphe/extension/shared/patches/LocalCreationDownloader;->onMemoriesCollectionSaveRequested(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :cond_orig_mem_coll
                        return-void
                        :cond_orig_mem_coll
                        """.trimIndent(),
                    )
                }

                // Item save method: e(int, account, mediaItem, collection, boolean)
                val itemSaveMethod = classDef.methods.find { method ->
                    method.returnType == "V" &&
                    method.parameterTypes.size == 5 &&
                    method.parameterTypes[0] == "I" &&
                    method.parameterTypes[4] == "Z"
                }
                if (itemSaveMethod != null) {
                    val mutableItemMethod = mutableClass.findMutableMethodOf(itemSaveMethod)
                    mutableItemMethod.addInstructions(
                        0,
                        """
                        invoke-static/range { p0 .. p4 }, Lapp/morphe/extension/shared/patches/LocalCreationDownloader;->onMemoriesItemSaveRequested(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :cond_orig_mem_item
                        return-void
                        :cond_orig_mem_item
                        """.trimIndent(),
                    )
                }

                if (collSaveMethod != null || itemSaveMethod != null) {
                    patchedMemoriesController = true
                }
            }

            // 4. Identify MFYSectionDelegate: Create tab hero card presenter
            if ("MFYSectionDelegate" in stringConstants) {
                val cardMethod = classDef.methods.find { method ->
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == "Ljava/lang/String;" &&
                    method.returnType != "V" &&
                    method.returnType != "Z"
                }
                if (cardMethod != null) {
                    val mutableClass = mutableClassDefBy(classDef)
                    val mutableCardMethod = mutableClass.findMutableMethodOf(cardMethod)
                    mutableCardMethod.addInstructions(
                        0,
                        """
                        invoke-static/range { p0 .. p2 }, Lapp/morphe/extension/shared/patches/LocalCreationDownloader;->onCheckHeroCardSaved(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V
                        """.trimIndent(),
                    )
                }
            }
        }

        if (!patchedSaveMixin) {
            throw PatchException("Could not find SaveCreationMixin or its save execution method.")
        }
        if (!patchedMfyMixin) {
            throw PatchException("Could not find MFYCreationMixin or its save method.")
        }
        if (!patchedMemoriesController) {
            throw PatchException("Could not find MemoriesSaveController or its save methods.")
        }
    }
}
