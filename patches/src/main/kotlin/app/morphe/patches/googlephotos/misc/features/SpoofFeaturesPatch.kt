/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/googlephotos/misc/features/SpoofFeaturesPatch.kt
 */
package app.morphe.patches.googlephotos.misc.features

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.all.misc.transformation.transformInstructionsPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringsOption
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

@Suppress("unused")
val spoofFeaturesPatch = bytecodePatch(
    name = "Spoof features",
    description = "Spoofs the device to enable Google Pixel exclusive features, including unlimited storage and modern UI.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)

    dependsOn(
        spoofBuildInfoPatch,
        app.morphe.patches.googlephotos.misc.extension.sharedExtensionPatch,
        transformInstructionsPatch(
            filterMap = filterMap@{ classDef, _, instruction, instructionIndex ->
                if (classDef.type.startsWith("Lapp/morphe/extension/")) return@filterMap null
                if (instruction.opcode != Opcode.INVOKE_VIRTUAL && instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE) return@filterMap null

                val methodRef = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return@filterMap null

                if (methodRef.definingClass == "Landroid/media/MediaFormat;" &&
                    methodRef.name == "setInteger" &&
                    methodRef.returnType == "V" &&
                    methodRef.parameterTypes == listOf("Ljava/lang/String;", "I")) {
                    return@filterMap instructionIndex to instruction
                }

                null
            },
            transform = transform@{ mutableMethod, (index, instruction) ->
                val args = when (instruction) {
                    is Instruction35c -> with(instruction) {
                        arrayOf(registerC, registerD, registerE, registerF, registerG)
                            .take(registerCount).joinToString(", ") { "v$it" }
                    }
                    is Instruction3rc -> with(instruction) {
                        (startRegister until startRegister + registerCount).joinToString(", ") { "v$it" }
                    }
                    else -> return@transform
                }
                mutableMethod.replaceInstruction(
                    index,
                    "invoke-static { $args }, Lapp/morphe/extension/shared/patches/ExynosVideoFix;->setInteger(Landroid/media/MediaFormat;Ljava/lang/String;I)V",
                )
            },
        ),
    )

    val featuresToEnable by stringsOption(
        key = "featuresToEnable",
        default = listOf(
            "com.google.android.apps.photos.NEXUS_PRELOAD",
            "com.google.android.apps.photos.nexus_preload",
            "com.google.android.apps.photos.PIXEL_2017_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2018_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2019_MIDYEAR_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2019_PRELOAD",
            "com.google.android.feature.PIXEL_EXPERIENCE",
            "com.google.android.feature.PIXEL_2017_EXPERIENCE",
            "com.google.android.feature.PIXEL_2018_EXPERIENCE",
            "com.google.android.feature.PIXEL_2019_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2019_EXPERIENCE",
            "com.google.android.feature.PIXEL_2020_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2020_EXPERIENCE",
            "com.google.android.feature.PIXEL_2021_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2021_EXPERIENCE",
        ),
        title = "Features to enable",
        description = "Google Pixel exclusive features to enable.",
        required = true,
    )

    val featuresToDisable by stringsOption(
        key = "featuresToDisable",
        default = emptyList(),
        title = "Features to disable",
        description = "Google Pixel exclusive features to disable.",
        required = true,
    )

    execute {
        @Suppress("NAME_SHADOWING")
        val featuresToEnable = featuresToEnable!!.toSet()

        @Suppress("NAME_SHADOWING")
        val featuresToDisable = featuresToDisable!!.toSet()

        getAllClassesWithStrings().forEach { classDef ->
            val mutableClass by lazy { mutableClassDefBy(classDef) }

            classDef.methods.forEach classLoop@{ method ->
                val implementation = method.implementation ?: return@classLoop
                val mutableMethod by lazy { mutableClass.findMutableMethodOf(method) }

                implementation.instructions.forEachIndexed { index, instruction ->
                    val string = ((instruction as? Instruction21c)?.reference as? StringReference)?.string
                        ?: return@forEachIndexed

                    val transformedString = when (string) {
                        in featuresToEnable -> "android.hardware.wifi"
                        in featuresToDisable -> "dummy"
                        else -> return@forEachIndexed
                    }

                    mutableMethod.replaceInstruction(
                        index,
                        BuilderInstruction21c(
                            Opcode.CONST_STRING,
                            (instruction as OneRegisterInstruction).registerA,
                            ImmutableStringReference(transformedString),
                        ),
                    )
                }
            }
        }
    }
}
