package app.morphe.patches.googlephotos.misc.flags

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
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

// v7.92 boolean flags for Styles in Memories, Pop-out cutouts, and scrapbooks
private val BOOL_FLAGS_TO_ENABLE = setOf(
    "45477626", // Master MemoryCard rendering capability
    "45659276", // Master pop-out cutout capability
    "45662994", // MemoryCard style templates
    "45785531", // Pop-out animation templates
    "45741031", // On-device "N Years Ago" generator
    "45737826", // "Over the Years" delightful theme
    "45764779", // Multi-up collage engine
    "45659278", // Multi-up collage layout support
    "45742883", // Multi-up capability
)

// Skottie asset bundle CDN version
private const val LONG_FLAG_ID = "3999"
private const val LONG_FLAG_VALUE = 120480972L

// Method names on Lcndy; (v7.92 Phenotype flags class) that return boolean flags to enable
private val CNDY_BOOLEAN_METHODS = setOf(
    "aQ", // 45477626 - master MemoryCard rendering capability
    "bs", // 45659276 - master pop-out cutout capability
    "cd", // 45662994 - MemoryCard style templates
    "T",  // 45785531 - pop-out animation templates
    "bB", // 45741031 - on-device "N Years Ago" generator
    "bC", // 45737826 - "Over the Years" delightful theme
    "aV", // 45764779 - multi-up collage engine
    "aj", // 45659278 - multi-up collage layout support
    "bT", // 45742883 - multi-up capability
)

val bakeMemoryStyleFlagsPatch = bytecodePatch(
    name = "Bake memory style flags",
    description = "Enables Pop-out cutouts, MemoryCard styles, and Skottie animations by bypassing device eligibility gates, forcing phenotype getters, and providing offline Google font resolution.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)

    execute {
        classDefForEach { classDef ->
            if (classDef.type.startsWith("Lapp/morphe/extension/")) return@classDefForEach

            when (classDef.type) {
                // Master feature gatekeeper for Google Photos stories/memories (v7.92: Lakyh;, legacy: Lakol;)
                "Lakyh;", "Lakol;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            when (method.name) {
                                "E", // MemoryCard style capability
                                "J", // Pop-out cutout capability (bypasses LOAD_GEN_AI_MEMORIES_ELIGIBILITY)
                                "f"  // Skottie assets capability
                                -> mutableClass.findMutableMethodOf(method).returnEarly(true)
                            }
                        }
                    }
                }

                // Cinematic/effect style capability (v7.92: Lakyp;, legacy: Lakot;)
                "Lakyp;", "Lakot;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        }
                    }
                }

                // Story capability flags container (MemoryCard, Pop-out cutout, Multi-up)
                "Lakwi;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.parameterTypes.isEmpty() && method.returnType == "Z") {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        }
                    }
                }


                // v7.92 Phenotype flag getters implementation (Lcndy;)
                "Lcndy;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.name in CNDY_BOOLEAN_METHODS &&
                            method.parameterTypes.isEmpty() &&
                            method.returnType == "Z"
                        ) {
                            mutableClass.findMutableMethodOf(method).returnEarly(true)
                        }
                    }
                }

                // v7.92 Skottie CDN asset bundle version getter (Lcnmd;)
                "Lcnmd;" -> {
                    val mutableClass by lazy { mutableClassDefBy(classDef) }
                    classDef.methods.forEach { method ->
                        if (method.name == "J" &&
                            method.parameterTypes.isEmpty() &&
                            method.returnType == "J"
                        ) {
                            mutableClass.findMutableMethodOf(method).returnEarly(LONG_FLAG_VALUE)
                        }
                    }
                }
            }

            // Font provider bypass: intercepts downloadable font requests and resolves via local Google Fonts / system fonts
            val methodA = classDef.methods.find {
                it.parameterTypes == listOf("Ljava/lang/String;", "Landroid/os/CancellationSignal;") &&
                it.returnType == "Landroid/net/Uri;"
            }

            val methodB = classDef.methods.find {
                it.parameterTypes == listOf("Landroid/net/Uri;") &&
                it.returnType == "[B"
            }

            if (methodA != null && methodB != null) {
                val mutableClass by lazy { mutableClassDefBy(classDef) }

                var streamReaderRef: MethodReference? = null
                methodB.implementation?.instructions?.forEach { instr ->
                    if (instr.opcode == Opcode.INVOKE_STATIC) {
                        val ref = (instr as? ReferenceInstruction)?.reference as? MethodReference
                        if (ref?.parameterTypes == listOf("Ljava/io/InputStream;") && ref.returnType == "[B") {
                            streamReaderRef = ref
                        }
                    }
                }

                if (streamReaderRef != null) {
                    val streamReaderClass = streamReaderRef!!.definingClass
                    val streamReaderMethod = streamReaderRef!!.name

                    val mutableMethodA = mutableClass.findMutableMethodOf(methodA)
                    mutableMethodA.addInstructions(0, """
                        if-eqz p1, :cond_default_a
                        invoke-virtual { p1 }, Ljava/lang/String;->toLowerCase()Ljava/lang/String;
                        move-result-object v0

                        # 1. Caveat (Handwritten titles)
                        const-string v1, "caveat"
                        invoke-virtual { v0, v1 }, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                        move-result v1
                        if-eqz v1, :cond_check_handlee
                        const-string v1, "/data/data/com.google.android.apps.photos/files/fonts/Caveat.ttf"
                        new-instance v2, Ljava/io/File;
                        invoke-direct { v2, v1 }, Ljava/io/File;-><init>(Ljava/lang/String;)V
                        invoke-virtual { v2 }, Ljava/io/File;->exists()Z
                        move-result v1
                        if-eqz v1, :cond_check_handlee
                        const-string v0, "file:///data/data/com.google.android.apps.photos/files/fonts/Caveat.ttf"
                        goto :cond_parse_a

                        # 2. Handlee (Playful captions)
                        :cond_check_handlee
                        const-string v1, "handlee"
                        invoke-virtual { v0, v1 }, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                        move-result v1
                        if-eqz v1, :cond_check_oswald
                        const-string v1, "/data/data/com.google.android.apps.photos/files/fonts/Handlee.ttf"
                        new-instance v2, Ljava/io/File;
                        invoke-direct { v2, v1 }, Ljava/io/File;-><init>(Ljava/lang/String;)V
                        invoke-virtual { v2 }, Ljava/io/File;->exists()Z
                        move-result v1
                        if-eqz v1, :cond_check_oswald
                        const-string v0, "file:///data/data/com.google.android.apps.photos/files/fonts/Handlee.ttf"
                        goto :cond_parse_a

                        # 3. Oswald (Bold numbers)
                        :cond_check_oswald
                        const-string v1, "oswald"
                        invoke-virtual { v0, v1 }, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                        move-result v1
                        if-eqz v1, :cond_check_montserrat
                        const-string v1, "/data/data/com.google.android.apps.photos/files/fonts/Oswald.ttf"
                        new-instance v2, Ljava/io/File;
                        invoke-direct { v2, v1 }, Ljava/io/File;-><init>(Ljava/lang/String;)V
                        invoke-virtual { v2 }, Ljava/io/File;->exists()Z
                        move-result v1
                        if-eqz v1, :cond_check_montserrat
                        const-string v0, "file:///data/data/com.google.android.apps.photos/files/fonts/Oswald.ttf"
                        goto :cond_parse_a

                        # 4. Montserrat (Modern titles)
                        :cond_check_montserrat
                        const-string v1, "montserrat"
                        invoke-virtual { v0, v1 }, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                        move-result v1
                        if-eqz v1, :cond_check_playfair
                        const-string v1, "/data/data/com.google.android.apps.photos/files/fonts/Montserrat.ttf"
                        new-instance v2, Ljava/io/File;
                        invoke-direct { v2, v1 }, Ljava/io/File;-><init>(Ljava/lang/String;)V
                        invoke-virtual { v2 }, Ljava/io/File;->exists()Z
                        move-result v1
                        if-eqz v1, :cond_check_playfair
                        const-string v0, "file:///data/data/com.google.android.apps.photos/files/fonts/Montserrat.ttf"
                        goto :cond_parse_a

                        # 5. Playfair Display (Serif covers)
                        :cond_check_playfair
                        const-string v1, "playfair"
                        invoke-virtual { v0, v1 }, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                        move-result v1
                        if-eqz v1, :cond_check_serif
                        const-string v1, "/data/data/com.google.android.apps.photos/files/fonts/PlayfairDisplay.ttf"
                        new-instance v2, Ljava/io/File;
                        invoke-direct { v2, v1 }, Ljava/io/File;-><init>(Ljava/lang/String;)V
                        invoke-virtual { v2 }, Ljava/io/File;->exists()Z
                        move-result v1
                        if-eqz v1, :cond_check_serif
                        const-string v0, "file:///data/data/com.google.android.apps.photos/files/fonts/PlayfairDisplay.ttf"
                        goto :cond_parse_a

                        # 6. Generic Serif fallback
                        :cond_check_serif
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

                    val mutableMethodB = mutableClass.findMutableMethodOf(methodB)
                    mutableMethodB.addInstructions(0, """
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
                        invoke-static { v0 }, $streamReaderClass->$streamReaderMethod(Ljava/io/InputStream;)[B
                        move-result-object v1
                        invoke-virtual { v0 }, Ljava/io/FileInputStream;->close()V
                        return-object v1

                        :cond_default_b
                        new-instance v0, Ljava/io/File;
                        const-string v1, "/system/fonts/Roboto-Regular.ttf"
                        invoke-direct { v0, v1 }, Ljava/io/File;-><init>(Ljava/lang/String;)V
                        new-instance v1, Ljava/io/FileInputStream;
                        invoke-direct { v1, v0 }, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V
                        invoke-static { v1 }, $streamReaderClass->$streamReaderMethod(Ljava/io/InputStream;)[B
                        move-result-object v0
                        invoke-virtual { v1 }, Ljava/io/FileInputStream;->close()V
                        return-object v0
                    """.trimIndent())
                }
            }
        }

        // Also patch fallback defaults where flags are initialized
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
                                            if (ref?.parameterTypes == listOf("Ljava/lang/String;", "Z") && ref.returnType == "Z") {
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
                                            if (ref?.parameterTypes == listOf("Ljava/lang/String;", "J") && ref.returnType == "J") {
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
