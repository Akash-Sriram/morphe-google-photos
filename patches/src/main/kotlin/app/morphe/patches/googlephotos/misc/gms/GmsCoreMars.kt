package app.morphe.patches.googlephotos.misc.gms

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patches.googlephotos.misc.gms.Constants.MORPHE_PHOTOS_PACKAGE_NAME
import app.morphe.patches.googlephotos.misc.gms.Constants.PHOTOS_PACKAGE_NAME
import app.morphe.patches.shared.misc.gms.PackageNameConfig
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

internal fun BytecodePatchContext.applyGmsCoreMars() {
    // MarsGridActivity.onCreate hook for private locked folder storage scanning
    classDefByOrNull("Lcom/google/android/apps/photos/mars/grid/MarsGridActivity;")?.let { classDef ->
        classDef.methods.find { it.name == "onCreate" }?.let { method ->
            mutableClassDefBy(classDef).findMutableMethodOf(method).addInstruction(
                0,
                "invoke-static {p0}, Lapp/morphe/extension/shared/patches/toolkit/StorageScanner;->onMarsGridActivityCreated(Landroid/app/Activity;)V",
            )
        }
    }
}

internal fun BytecodePatchContext.applyGmsCoreMarsFinalize() {
    if (PackageNameConfig.isPackageNameChangeEnabled) {
        val targetPackageName = PackageNameConfig.effectivePackageName.ifEmpty { MORPHE_PHOTOS_PACKAGE_NAME }
        val marsPackagePrefixes = listOf(
            "$PHOTOS_PACKAGE_NAME.mars.",
            "content://$PHOTOS_PACKAGE_NAME.mars.",
        )
        val marsLibPrefixes = listOf(
            "com.google.android.libraries.photos.api.mars",
            "content://com.google.android.libraries.photos.api.mars",
        )

        classDefForEach { classDef ->
            val mutableClass by lazy { mutableClassDefBy(classDef) }
            classDef.methods.forEach classLoop@{ method ->
                val implementation = method.implementation ?: return@classLoop
                val mutableMethod by lazy { mutableClass.findMutableMethodOf(method) }

                implementation.instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference
                    if (instruction is Instruction21c && ref is StringReference) {
                        val original = ref.string
                        if (!original.endsWith("Activity") && !original.contains("Activity$") &&
                            !original.endsWith("Task") && !original.contains("Task$") &&
                            !original.endsWith("Service") && !original.contains("Service$")) {
                            val transformed = when {
                                marsLibPrefixes.any { original.startsWith(it) } ->
                                    original.replace("com.google.android.libraries.photos.api.mars", "$targetPackageName.api.mars")
                                marsPackagePrefixes.any { original.startsWith(it) } ->
                                    original.replace(PHOTOS_PACKAGE_NAME, targetPackageName)
                                else -> null
                            }
                            if (transformed != null) {
                                mutableMethod.replaceInstruction(
                                    index,
                                    BuilderInstruction21c(
                                        Opcode.CONST_STRING,
                                        (instruction as OneRegisterInstruction).registerA,
                                        ImmutableStringReference(transformed),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
