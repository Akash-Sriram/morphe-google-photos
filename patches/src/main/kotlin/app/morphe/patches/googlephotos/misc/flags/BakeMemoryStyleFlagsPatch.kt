package app.morphe.patches.googlephotos.misc.flags

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.cloneMutable
import app.morphe.util.findMutableMethodOf
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31i
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// Flag IDs whose default value (false) we want to override to true.
// These control the Styles in Memories / scrapbook cutout rendering pipeline.
private val BOOL_FLAGS_TO_ENABLE = setOf(
    "45477626", // clwj.aR()  — master MemoryCard rendering capability
    "45659276", // clwj.bt()  — master pop-out cutout capability
    "45662994", // clwj.cd()  — MemoryCard style templates
    "45785531", // clwj.S()   — pop-out animation templates
    "45741031", // clwj.bC()  — on-device "N Years Ago" generator
    "45737826", // clwj.bD()  — "Over the Years" delightful theme
    "45764779", // clwj.aW()  — multi-up collage engine
    "45659278", // clwj.aj()  — multi-up collage layout support
    "45742883", // clwj.bU()  — multi-up capability
)

// Long flag to override: Skottie asset bundle CDN version.
// Default in APK is 0L; the production value is 120480972L (0x72E89CCL).
private const val LONG_FLAG_ID = "3999"
private const val LONG_FLAG_VALUE = 120480972L

// Method names on clwj that return boolean flags to enable
private val CLWJ_BOOLEAN_METHODS = setOf(
    "aR", // master MemoryCard rendering capability
    "bt", // master pop-out cutout capability
    "cd", // MemoryCard style templates
    "S",  // pop-out animation templates
    "bC", // on-device "N Years Ago" generator
    "bD", // "Over the Years" delightful theme
    "aW", // multi-up collage engine
    "aj", // multi-up collage layout support
    "bU", // multi-up capability
)

@Suppress("unused")
val bakeMemoryStyleFlagsPatch = bytecodePatch(
    name = "Bake memory style flags",
    description = "Hard-codes the Styles in Memories feature flags into the DEX so they are " +
            "always enabled regardless of SharedPreferences / Phenotype state. " +
            "Enables scrapbook-style graphic borders, large typography number cutouts, " +
            "and depth pop-out effects in the Memories carousel.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)

    execute {
        // 1. Direct method overrides (bypasses Phenotype database, SharedPreferences, and server sync completely)
        getAllClasses().forEach { classDef ->
            if (classDef.type.startsWith("Lapp/morphe/extension/")) return@forEach

            when (classDef.type) {
                // Master feature gatekeeper for Google Photos stories/memories
                "Lakol;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            when (method.name) {
                                "E", // MemoryCard style capability
                                "J", // Pop-out cutout capability
                                "f"  // Skottie assets capability
                                -> mutableClass.findMutableMethodOf(method).returnEarly(true)
                            }
                        }
                    }
                }

                // Internal phenotype flag container class
                "Lclwj;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.parameterTypes.isEmpty() && method.returnType == "Z" && method.name in CLWJ_BOOLEAN_METHODS) {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        } else if (method.parameterTypes.isEmpty() && method.returnType == "J" && method.name == "j") { // Skottie CDN version flag
                            val mutableMethod = mutableClass.findMutableMethodOf(method)
                            mutableMethod.implementation = app.morphe.patcher.patch.MutableMethodImplementation(mutableMethod.implementation!!.registerCount).apply {
                                addInstructions(0, """
                                    const-wide v0, $LONG_FLAG_VALUE
                                    return-wide v0
                                """.trimIndent())
                            }
                        }
                    }
                }

                // StoryPlayer capability verification checks
                "Lcmeo;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            when (method.name) {
                                "o", // MemoryCard story player support
                                "u"  // Skottie capability check
                                -> mutableClass.findMutableMethodOf(method).returnEarly(true)
                            }
                        }
                    }
                }
            }
        }

        // 2. Also patch <clinit> defaults where flags are initialized
        getAllClassesWithStrings().forEach { classDef ->
            if (classDef.type.startsWith("Lapp/morphe/extension/")) return@forEach

            val mutableClass by lazy { mutableClassDefBy(classDef) }

            classDef.methods.forEach classLoop@{ method ->
                val implementation = method.implementation ?: return@classLoop
                val instructionList = implementation.instructions.toList()
                val mutableMethod by lazy { mutableClass.findMutableMethodOf(method) }

                instructionList.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.CONST_STRING &&
                        instruction.opcode != Opcode.CONST_STRING_JUMBO
                    ) return@forEachIndexed

                    val flagId =
                        ((instruction as? Instruction21c)?.reference as? StringReference)?.string
                            ?: return@forEachIndexed

                    when {
                        flagId in BOOL_FLAGS_TO_ENABLE -> {
                            for (lookahead in 1..3) {
                                val nextIdx = index + lookahead
                                if (nextIdx >= instructionList.size) break

                                val nextInst = instructionList[nextIdx]

                                if (nextInst is NarrowLiteralInstruction &&
                                    nextInst is OneRegisterInstruction
                                ) {
                                    val valRegister = nextInst.registerA
                                    val callIdx = nextIdx + 1
                                    if (callIdx < instructionList.size) {
                                        val callInst = instructionList[callIdx]
                                        if (callInst.opcode == Opcode.INVOKE_VIRTUAL) {
                                            val ref =
                                                (callInst as? ReferenceInstruction)?.reference as? MethodReference
                                            if (ref?.name == "h" &&
                                                ref.parameterTypes == listOf(
                                                    "Ljava/lang/String;",
                                                    "Z"
                                                )
                                            ) {
                                                mutableMethod.replaceInstruction(
                                                    nextIdx,
                                                    BuilderInstruction11n(
                                                        Opcode.CONST_4,
                                                        valRegister,
                                                        1,
                                                    ),
                                                )
                                                return@forEachIndexed
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        flagId == LONG_FLAG_ID -> {
                            for (lookahead in 1..3) {
                                val nextIdx = index + lookahead
                                if (nextIdx >= instructionList.size) break

                                val nextInst = instructionList[nextIdx]

                                if (nextInst is WideLiteralInstruction &&
                                    nextInst is OneRegisterInstruction
                                ) {
                                    val valRegister = nextInst.registerA
                                    val callIdx = nextIdx + 1
                                    if (callIdx < instructionList.size) {
                                        val callInst = instructionList[callIdx]
                                        if (callInst.opcode == Opcode.INVOKE_VIRTUAL) {
                                            val ref =
                                                (callInst as? ReferenceInstruction)?.reference as? MethodReference
                                            if (ref?.name == "f" &&
                                                ref.parameterTypes == listOf(
                                                    "Ljava/lang/String;",
                                                    "J"
                                                )
                                            ) {
                                                mutableMethod.replaceInstruction(
                                                    nextIdx,
                                                    BuilderInstruction31i(
                                                        Opcode.CONST_WIDE_32,
                                                        valRegister,
                                                        LONG_FLAG_VALUE.toInt(),
                                                    ),
                                                )
                                                return@forEachIndexed
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
