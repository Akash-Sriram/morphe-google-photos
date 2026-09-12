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
        classDefForEach { classDef ->
            if (classDef.type.startsWith("Lapp/morphe/extension/")) return@classDefForEach

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

                // Cinematic/effect style capability
                "Lakot;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.name == "c" && method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        }
                    }
                }

                // Phenotype flag getters implementation (clwi)
                "Lclwj;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.name in CLWJ_BOOLEAN_METHODS &&
                            method.parameterTypes.isEmpty() &&
                            method.returnType == "Z"
                        ) {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        }
                    }
                }

                // Skottie CDN asset bundle version getter (cmen)
                "Lcmeo;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.name == "J" && method.parameterTypes.isEmpty() && method.returnType == "J") {
                            mutableClass.findMutableMethodOf(method).returnEarly(LONG_FLAG_VALUE)
                        }
                    }
                }

                // Skottie player animation view helper
                "Ltjh;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.name == "z" && method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        }
                    }
                }

                // Story / Memory font loading & resolution (bypasses GMS font provider certificate check)
                "Lbfxs;" -> {
                    val mutableClass = mutableClassDefBy(classDef)
                    val methodA = classDef.methods.find {
                        it.name == "a" && it.parameterTypes == listOf("Ljava/lang/String;", "Landroid/os/CancellationSignal;")
                    }
                    if (methodA != null) {
                        val clonedA = methodA.cloneMutable(additionalRegisters = 5)
                        clonedA.addInstructions(0, """
                            if-eqz p1, :cond_default_a
                            invoke-virtual { p1 }, Ljava/lang/String;->toLowerCase()Ljava/lang/String;
                            move-result-object v0
                            const-string v1, "serif"
                            invoke-virtual { v0, v1 }, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_default_a
                            const-string v0, "file:///system/fonts/NotoSerif-Italic.ttf"
                            goto :cond_parse_a
                            :cond_default_a
                            const-string v0, "file:///system/fonts/Roboto-Regular.ttf"
                            :cond_parse_a
                            invoke-static { v0 }, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
                            move-result-object v0
                            return-object v0
                        """.trimIndent())
                        mutableClass.methods.remove(methodA)
                        mutableClass.methods.add(clonedA)
                    }

                    val methodB = classDef.methods.find {
                        it.name == "b" && it.parameterTypes == listOf("Landroid/net/Uri;")
                    }
                    if (methodB != null) {
                        val clonedB = methodB.cloneMutable(additionalRegisters = 5)
                        clonedB.addInstructions(0, """
                            if-eqz p1, :cond_default_b
                            invoke-virtual { p1 }, Landroid/net/Uri;->getPath()Ljava/lang/String;
                            move-result-object v0
                            if-eqz v0, :cond_default_b
                            new-instance v1, Ljava/io/File;
                            invoke-direct { v1, v0 }, Ljava/io/File;-><init>(Ljava/lang/String;)V
                            invoke-virtual { v1 }, Ljava/io/File;->exists()Z
                            move-result v0
                            if-eqz v0, :cond_default_b
                            new-instance v0, Ljava/io/FileInputStream;
                            invoke-direct { v0, v1 }, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V
                            invoke-static { v0 }, Lcnxl;->h(Ljava/io/InputStream;)[B
                            move-result-object v1
                            invoke-virtual { v0 }, Ljava/io/FileInputStream;->close()V
                            return-object v1
                            :cond_default_b
                            new-instance v0, Ljava/io/File;
                            const-string v1, "/system/fonts/Roboto-Regular.ttf"
                            invoke-direct { v0, v1 }, Ljava/io/File;-><init>(Ljava/lang/String;)V
                            new-instance v1, Ljava/io/FileInputStream;
                            invoke-direct { v1, v0 }, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V
                            invoke-static { v1 }, Lcnxl;->h(Ljava/io/InputStream;)[B
                            move-result-object v0
                            invoke-virtual { v1 }, Ljava/io/FileInputStream;->close()V
                            return-object v0
                        """.trimIndent())
                        mutableClass.methods.remove(methodB)
                        mutableClass.methods.add(clonedB)
                    }
                }

                // Portrait Blur classifier provider (bypasses MDD build_id version check)
                "Lanqb;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.name == "a" && method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        }
                    }
                }

                // Portrait Segmenter model provider (bypasses MDD file lookup check)
                "Lanrk;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.name == "a" && method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        }
                    }
                }

                // Sky model provider (bypasses MDD file lookup check)
                "Laspz;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.name == "c" && method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        }
                    }
                }

                // ModelDownloadManager UI status and readiness gates
                "Larea;" -> {
                    val mutableClass = mutableClassDefBy(classDef)
                    val methodC = classDef.methods.find {
                        it.name == "c" && it.parameterTypes == listOf("Lchoo;") && it.returnType == "Laqta;"
                    }
                    if (methodC != null) {
                        val clonedC = methodC.cloneMutable(additionalRegisters = 2)
                        clonedC.addInstructions(0, """
                            if-eqz p1, :cond_orig
                            invoke-virtual { p1 }, Ljava/lang/Enum;->ordinal()I
                            move-result v0
                            const/16 v1, 8
                            if-eq v0, v1, :cond_loaded
                            const/16 v1, 14
                            if-eq v0, v1, :cond_loaded
                            const/16 v1, 16
                            if-eq v0, v1, :cond_loaded
                            const/16 v1, 17
                            if-eq v0, v1, :cond_loaded
                            const/16 v1, 19
                            if-eq v0, v1, :cond_loaded
                            const/16 v1, 34
                            if-eq v0, v1, :cond_loaded
                            const/16 v1, 52
                            if-eq v0, v1, :cond_loaded
                            goto :cond_orig
                            :cond_loaded
                            sget-object v0, Laqta;->e:Laqta;
                            return-object v0
                            :cond_orig
                        """.trimIndent())
                        mutableClass.methods.remove(methodC)
                        mutableClass.methods.add(clonedC)
                    }

                    val methodQ = classDef.methods.find {
                        it.name == "q" && it.parameterTypes == listOf("Lchoo;") && it.returnType == "Z"
                    }
                    if (methodQ != null) {
                        val clonedQ = methodQ.cloneMutable(additionalRegisters = 2)
                        clonedQ.addInstructions(0, """
                            if-eqz p1, :cond_orig_q
                            invoke-virtual { p1 }, Ljava/lang/Enum;->ordinal()I
                            move-result v0
                            const/16 v1, 8
                            if-eq v0, v1, :cond_true_q
                            const/16 v1, 14
                            if-eq v0, v1, :cond_true_q
                            const/16 v1, 16
                            if-eq v0, v1, :cond_true_q
                            const/16 v1, 17
                            if-eq v0, v1, :cond_true_q
                            const/16 v1, 19
                            if-eq v0, v1, :cond_true_q
                            const/16 v1, 34
                            if-eq v0, v1, :cond_true_q
                            const/16 v1, 52
                            if-eq v0, v1, :cond_true_q
                            goto :cond_orig_q
                            :cond_true_q
                            const/4 v0, 1
                            return v0
                            :cond_orig_q
                        """.trimIndent())
                        mutableClass.methods.remove(methodQ)
                        mutableClass.methods.add(clonedQ)
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
