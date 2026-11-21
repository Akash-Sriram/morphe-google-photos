/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/shared/misc/gms/GmsCoreSupportPatch.kt
 */
package app.morphe.patches.shared.misc.gms

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.ResourcePatchBuilder
import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.Option
import app.morphe.patcher.patch.PatchAvailability
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.stringOption
import app.morphe.util.asSequence
import app.morphe.util.getNode
import app.morphe.patches.shared.misc.gms.Constants.ACTIONS
import app.morphe.patches.shared.misc.gms.Constants.AUTHORITIES
import app.morphe.patches.shared.misc.gms.Constants.PERMISSIONS
import app.morphe.patches.shared.misc.settings.preference.BasePreferenceScreen
import app.morphe.patches.shared.misc.settings.preference.IntentPreference
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.iface.Method
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import org.w3c.dom.Element
import org.w3c.dom.Node

internal const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;"

/**
 * Old vendor id for maximum backwards compatibility.
 */
internal const val GMS_CORE_VENDOR_GROUP_ID = "app.revanced"

object PackageNameConfig {
    /**
     * If true, forces the official package name (com.google.android.apps.photos).
     * Defaults to false, so the mod package (app.morphe.android.apps.photos) is active by default.
     */
    var useOfficialPackageName: Boolean = false
    var customPackageName: String = ""

    val isPackageNameChangeEnabled: Boolean
        get() = !useOfficialPackageName

    val effectivePackageName: String
        get() = if (useOfficialPackageName) "" else customPackageName

    fun resolvePackageName(fromPackageName: String, fallbackPackageName: String): String {
        return if (useOfficialPackageName) {
            fromPackageName
        } else if (customPackageName.isNotEmpty()) {
            customPackageName
        } else {
            fallbackPackageName
        }
    }
}

/**
 * A patch that allows patched Google apps to run without root and under a different package name
 * by using GmsCore instead of Google Play Services.
 *
 * @param fromPackageName The package name of the original app.
 * @param toPackageName The package name to fall back to if no custom package name is specified in patch options.
 * @param primeMethodFingerprint The fingerprint of the "prime" method that needs to be patched.
 * @param earlyReturnFingerprints The fingerprints of methods that need to be returned early.
 * @param mainActivityOnCreateFingerprint The fingerprint of the main activity onCreate method.
 * @param extensionPatch The patch responsible for the extension.
 * @param executeBlock The additional execution block of the patch.
 * @param block The additional block to build the patch.
 */
fun gmsCoreSupportPatch(
    fromPackageName: String,
    toPackageName: String,
    primeMethodFingerprint: Fingerprint? = null,
    earlyReturnFingerprints: Set<Fingerprint> = setOf(),
    mainActivityOnCreateFingerprint: Fingerprint,
    extensionPatch: Patch<*>,
    gmsCoreSupportResourcePatchFactory: () -> Patch<*>,
    executeBlock: BytecodePatchContext.() -> Unit = {},
    finalizeBlock: BytecodePatchContext.() -> Unit = {},
    block: BytecodePatchBuilder.() -> Unit = {},
) = bytecodePatch(
    name = "GmsCore support",
    description = "Enables GmsCore / MicroG support and renames package for non-root installs. " +
        "Leave enabled for standard non-root installs; disable only for rooted system installs using genuine Google Play Services.",
    default = true,
) {
    val keepOfficialPackageNameOption = booleanOption(
        key = "keepOfficialPackageName",
        default = false,
        title = "Keep official package name",
        description = "Retains the official package name ($fromPackageName) instead of renaming to " +
            "$toPackageName. Use this on non-root devices where the app is not preinstalled by the OEM.",
    )

    availability { installer, _ ->
        when (installer) {
            InstallerType.MOUNT -> PatchAvailability.UNAVAILABLE
            InstallerType.STANDARD, InstallerType.SHIZUKU -> PatchAvailability.REQUIRED
        }
    }

    dependsOn(
        gmsCoreSupportResourcePatchFactory(),
        extensionPatch,
    )

    fun BytecodePatchContext.transformStringReferences(transform: (str: String) -> String?) = classDefForEach {
        val mutableClass by lazy {
            mutableClassDefBy(it)
        }

        it.methods.forEach classLoop@{ method ->
            val implementation = method.implementation ?: return@classLoop

            val mutableMethod by lazy {
                mutableClass.findMutableMethodOf(method)
            }

            implementation.instructions.forEachIndexed { index, instruction ->
                val string = ((instruction as? Instruction21c)?.reference as? StringReference)?.string
                    ?: return@forEachIndexed

                // Apply transformation.
                val transformedString = transform(string) ?: return@forEachIndexed

                mutableMethod.replaceInstruction(
                    index,
                    BuilderInstruction21c(
                        Opcode.CONST_STRING,
                        instruction.registerA,
                        ImmutableStringReference(transformedString),
                    ),
                )
            }
        }
    }

    fun commonTransform(referencedString: String): String? {
        // Fast exit: if string doesn't even contain relevant substrings, skip set lookups.
        if (!referencedString.startsWith("com.google") &&
            referencedString != "subscribedfeeds"
        ) {
            return null
        }

        return when (referencedString) {
            "com.google",
            "com.google.android.gms",
            in PERMISSIONS,
            in ACTIONS,
            in AUTHORITIES,
            -> referencedString.replace("com.google", GMS_CORE_VENDOR_GROUP_ID)

            // No vendor prefix for whatever reason...
            "subscribedfeeds" -> "$GMS_CORE_VENDOR_GROUP_ID.subscribedfeeds"
            else -> null
        }
    }

    fun contentUrisTransform(str: String): String? {
        // only when content:// uri
        if (str.startsWith("content://")) {
            // check if matches any authority
            for (authority in AUTHORITIES) {
                val uriPrefix = "content://$authority"
                if (str.startsWith(uriPrefix)) {
                    return str.replace(
                        uriPrefix,
                        "content://${authority.replace("com.google", GMS_CORE_VENDOR_GROUP_ID)}",
                    )
                }
            }

            // gms also has a 'subscribedfeeds' authority, check for that one too
            val subFeedsUriPrefix = "content://subscribedfeeds"
            if (str.startsWith(subFeedsUriPrefix)) {
                return str.replace(subFeedsUriPrefix, "content://$GMS_CORE_VENDOR_GROUP_ID.subscribedfeeds")
            }
        }

        return null
    }

    fun packageNameTransform(fromPackageName: String, toPackageName: String): (String) -> String? = { string ->
        when (string) {
            "$fromPackageName.SuggestionProvider",
            "$fromPackageName.fileprovider",
            -> string.replace(fromPackageName, toPackageName)

            else -> null
        }
    }

    fun BytecodePatchContext.transformPrimeMethod(packageName: String) {
        primeMethodFingerprint!!.method.apply {
            var register = 2

            val index = instructions.indexOfFirst {
                if (it.getReference<StringReference>()?.string != fromPackageName) return@indexOfFirst false

                register = (it as OneRegisterInstruction).registerA
                return@indexOfFirst true
            }

            replaceInstruction(index, "const-string v$register, \"$packageName\"")
        }
    }

    execute {
        PackageNameConfig.useOfficialPackageName = keepOfficialPackageNameOption.value == true

        val transformations: List<(String) -> String?> = listOf(
            ::commonTransform,
            ::contentUrisTransform,
        )
        transformStringReferences transform@{ string ->
            transformations.forEach { transform ->
                transform(string)?.let { transformedString -> return@transform transformedString }
            }

            return@transform null
        }

        // Return these methods early to prevent the app from crashing.
        earlyReturnFingerprints.forEach {
            it.method.apply {
                if (returnType == "Z") {
                    returnEarly(false)
                } else {
                    returnEarly()
                }
            }
        }
        ServiceCheckFingerprint.method.returnEarly()

        // Google Play Utility is not present in all apps, so we need to check if it's present.
        if (GooglePlayUtilityFingerprint.methodOrNull != null) {
            GooglePlayUtilityFingerprint.method.returnEarly(0)
        }

        // Set original and patched package names for extension to use.
        OriginalPackageNameExtensionFingerprint.method.returnEarly(fromPackageName)

        // Verify GmsCore is installed and whitelisted for power optimizations and background usage.
        mainActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS_DESCRIPTOR->" +
                    "checkGmsCore(Landroid/app/Activity;)V"
        )

        // Change the vendor of GmsCore in the extension.
        GmsCoreSupportFingerprint.method.returnEarly(
            GMS_CORE_VENDOR_GROUP_ID
        )

        // ─────────────────────────────────────────────────────────────────────────
        // Universal GNP / Chime Push Notification Support
        // ─────────────────────────────────────────────────────────────────────────

        // 1) GNP Certificate Provider Spoofing (SHA-1 hash returned to GNP network layer)
        GnpCertProviderFingerprint.methodOrNull?.let { certMethod ->
            val certClass = mutableClassDefBy(certMethod.definingClass)
            val mutableCertMethod = certClass.findMutableMethodOf(certMethod)
            mutableCertMethod.addInstructions(
                0,
                """
                invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->getEffectivePackageSignature()Ljava/lang/String;
                move-result-object v0
                return-object v0
                """.trimIndent(),
            )
        }

        // 2) GNP ("Chime") HTTP Request Builder Header Spoofing
        GnpRequestBuilderFingerprint.methodOrNull?.let { gnpHeaderMethod ->
            val gnpClass = mutableClassDefBy(gnpHeaderMethod.definingClass)

            // Hook all (headerKey, headerValue: String) setters in the builder
            gnpClass.methods.filter { m ->
                m.returnType == "V" &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[1] == "Ljava/lang/String;"
            }.forEach { headerMethod ->
                val mutableMethod = gnpClass.findMutableMethodOf(headerMethod)
                mutableMethod.addInstructions(
                    0,
                    """
                    invoke-static {p1, p2}, $EXTENSION_CLASS_DESCRIPTOR->spoofHeaderValue(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object p2
                    """.trimIndent(),
                )
            }

            // Hook all (metadataKey, metadataValue: Object) setters in the builder
            gnpClass.methods.filter { m ->
                m.returnType == "V" &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == "Ljava/lang/Object;" &&
                    m.parameterTypes[1] == "Ljava/lang/Object;"
            }.forEach { mapPutMethod ->
                val mutableMapMethod = gnpClass.findMutableMethodOf(mapPutMethod)
                mutableMapMethod.addInstructions(
                    0,
                    """
                    invoke-static {p1, p2}, $EXTENSION_CLASS_DESCRIPTOR->spoofMetadataValue(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object p2
                    """.trimIndent(),
                )
            }
        }

        // 3) gRPC Metadata Header Spoofing
        GrpcMetadataPutFingerprint.methodOrNull?.let { grpcPutMethod ->
            val grpcClass = mutableClassDefBy(grpcPutMethod.definingClass)
            val mutableGrpcPutMethod = grpcClass.findMutableMethodOf(grpcPutMethod)
            mutableGrpcPutMethod.addInstructions(
                0,
                """
                invoke-static {p1, p2}, $EXTENSION_CLASS_DESCRIPTOR->spoofMetadataValue(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object p2
                """.trimIndent(),
            )
        }

        // 4) Generic HTTP Request Header Spoofing (addRequestProperty, setRequestProperty, addHeader)
        classDefForEach { classDef ->
            val mutableClass by lazy { mutableClassDefBy(classDef) }
            classDef.methods.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                val instructions = implementation.instructions.toList()
                val indicesToPatch = mutableListOf<Triple<Int, Int, Int>>() // (index, keyReg, valReg)

                instructions.forEachIndexed { index, inst ->
                    if (inst.opcode == Opcode.INVOKE_VIRTUAL || inst.opcode == Opcode.INVOKE_VIRTUAL_RANGE ||
                        inst.opcode == Opcode.INVOKE_INTERFACE || inst.opcode == Opcode.INVOKE_INTERFACE_RANGE) {
                        val ref = (inst as? ReferenceInstruction)?.reference as? MethodReference
                        if (ref != null &&
                            (ref.name == "addRequestProperty" || ref.name == "setRequestProperty" || ref.name == "addHeader") &&
                            ref.parameterTypes.toList() == listOf("Ljava/lang/String;", "Ljava/lang/String;")) {
                            val regs = when (inst) {
                                is FiveRegisterInstruction -> Pair(inst.registerD, inst.registerE)
                                is RegisterRangeInstruction -> Pair(inst.startRegister + 1, inst.startRegister + 2)
                                else -> null
                            }
                            if (regs != null) {
                                indicesToPatch.add(Triple(index, regs.first, regs.second))
                            }
                        }
                    }
                }

                if (indicesToPatch.isNotEmpty()) {
                    val mutableMethod = mutableClass.findMutableMethodOf(method)
                    for ((idx, keyReg, valReg) in indicesToPatch.asReversed()) {
                        val invokeSmali = if (keyReg < 16 && valReg < 16) {
                            "invoke-static {v$keyReg, v$valReg}, $EXTENSION_CLASS_DESCRIPTOR->spoofHeaderValue(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;"
                        } else {
                            "invoke-static/range {v$keyReg .. v$valReg}, $EXTENSION_CLASS_DESCRIPTOR->spoofHeaderValue(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;"
                        }
                        mutableMethod.addInstructions(
                            idx,
                            """
                            $invokeSmali
                            move-result-object v$valReg
                            """.trimIndent(),
                        )
                    }
                }
            }
        }

        // 5) Cronet & GNP HTTP Response & Request Dispatch Hooks
        classDefForEach { classDef ->
            val mutableClass by lazy { mutableClassDefBy(classDef) }

            // Hook GnpHttpResponse constructor to log the response object
            val hasGnpHttpResponse = classDef.methods.any { m ->
                m.name == "toString" && m.implementation?.instructions?.any { inst ->
                    ((inst as? ReferenceInstruction)?.reference as? StringReference)?.string?.contains("GnpHttpResponse{statusCode=") == true
                } == true
            }
            if (hasGnpHttpResponse) {
                classDef.methods.firstOrNull { it.name == "<init>" && it.parameterTypes.size == 6 && it.parameterTypes[0] == "Ljava/lang/Integer;" }?.let { initMethod ->
                    val implementation = initMethod.implementation
                    if (implementation != null) {
                        val returnIdx = implementation.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
                        if (returnIdx != -1) {
                            val thisReg = implementation.registerCount - initMethod.parameters.size - 1
                            val invokeSmali = if (thisReg < 16) {
                                "invoke-static {v$thisReg}, $EXTENSION_CLASS_DESCRIPTOR->onGnpResponseObject(Ljava/lang/Object;)V"
                            } else {
                                "invoke-static/range {v$thisReg .. v$thisReg}, $EXTENSION_CLASS_DESCRIPTOR->onGnpResponseObject(Ljava/lang/Object;)V"
                            }
                            mutableClass.findMutableMethodOf(initMethod).addInstructions(returnIdx, invokeSmali)
                        }
                    }
                }
            }

            // Hook UrlRequest$Callback methods for Cronet network-level responses
            if (classDef.superclass == "Lorg/chromium/net/UrlRequest\$Callback;") {
                classDef.methods.forEach { method ->
                    val implementation = method.implementation ?: return@forEach
                    val mutableMethod by lazy { mutableClass.findMutableMethodOf(method) }
                    val rCount = implementation.registerCount
                    when (method.name) {
                        "onResponseStarted" -> {
                            val infoReg = rCount - 1
                            val invokeSmali = if (infoReg < 16) {
                                "invoke-static {v$infoReg}, $EXTENSION_CLASS_DESCRIPTOR->onCronetResponseStarted(Ljava/lang/Object;)V"
                            } else {
                                "invoke-static/range {v$infoReg .. v$infoReg}, $EXTENSION_CLASS_DESCRIPTOR->onCronetResponseStarted(Ljava/lang/Object;)V"
                            }
                            mutableMethod.addInstructions(0, invokeSmali)
                        }
                        "onSucceeded" -> {
                            val infoReg = rCount - 1
                            val invokeSmali = if (infoReg < 16) {
                                "invoke-static {v$infoReg}, $EXTENSION_CLASS_DESCRIPTOR->onCronetSucceeded(Ljava/lang/Object;)V"
                            } else {
                                "invoke-static/range {v$infoReg .. v$infoReg}, $EXTENSION_CLASS_DESCRIPTOR->onCronetSucceeded(Ljava/lang/Object;)V"
                            }
                            mutableMethod.addInstructions(0, invokeSmali)
                        }
                        "onFailed" -> {
                            val infoReg = rCount - 2
                            val errReg = rCount - 1
                            val invokeSmali = if (infoReg < 16 && errReg < 16) {
                                "invoke-static {v$infoReg, v$errReg}, $EXTENSION_CLASS_DESCRIPTOR->onCronetFailed(Ljava/lang/Object;Ljava/lang/Object;)V"
                            } else {
                                "invoke-static/range {v$infoReg .. v$errReg}, $EXTENSION_CLASS_DESCRIPTOR->onCronetFailed(Ljava/lang/Object;Ljava/lang/Object;)V"
                            }
                            mutableMethod.addInstructions(0, invokeSmali)
                        }
                    }
                }
            }

            // Hook Cronet UrlRequest dispatch in Labm to log URL and body payload
            classDef.methods.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                val hasNewUrlRequestBuilder = implementation.instructions.any {
                    it.opcode == Opcode.INVOKE_VIRTUAL &&
                        ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "newUrlRequestBuilder"
                }
                if (hasNewUrlRequestBuilder) {
                    val instructions = implementation.instructions.toList()
                    val checkCastIdx = instructions.indexOfFirst { inst ->
                        if (inst.opcode == Opcode.CHECK_CAST) {
                            val targetType = ((inst as? ReferenceInstruction)?.reference as? TypeReference)?.type
                            if (targetType != null) {
                                val def = classDefByOrNull(targetType)
                                def?.fields?.any { it.type == "Ljava/net/URL;" } == true
                            } else false
                        } else false
                    }
                    if (checkCastIdx != -1) {
                        val reg = (instructions[checkCastIdx] as OneRegisterInstruction).registerA
                        val invokeSmali = if (reg < 16) {
                            "invoke-static {v$reg}, $EXTENSION_CLASS_DESCRIPTOR->onCronetRequestStarting(Ljava/lang/Object;)V"
                        } else {
                            "invoke-static/range {v$reg .. v$reg}, $EXTENSION_CLASS_DESCRIPTOR->onCronetRequestStarting(Ljava/lang/Object;)V"
                        }
                        mutableClass.findMutableMethodOf(method).addInstructions(checkCastIdx + 1, invokeSmali)
                    }
                }
            }
        }

        // 6) Universal GNP Protobuf Target Package Name Spoofing
        // Locates methods calling Context.getPackageName() passed to a protobuf builder setter (e.g. ckzr.c)
        // and spoofs the package name to the target original package name.
        val targetMethodsToHook = mutableSetOf<Method>()
        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                val instructions = implementation.instructions.toList()
                for (i in 0 until instructions.size - 1) {
                    val inst = instructions[i]
                    val ref = (inst as? ReferenceInstruction)?.reference as? MethodReference
                    if (ref != null && ref.name == "getPackageName" && ref.returnType == "Ljava/lang/String;") {
                        val next = instructions[i + 1]
                        if (next.opcode == Opcode.MOVE_RESULT_OBJECT) {
                            val pkgReg = (next as OneRegisterInstruction).registerA
                            for (j in i + 2 until minOf(i + 15, instructions.size)) {
                                val checkInst = instructions[j]
                                if (checkInst.opcode == Opcode.INVOKE_STATIC && checkInst is FiveRegisterInstruction) {
                                    val targetMr = (checkInst as? ReferenceInstruction)?.reference as? MethodReference
                                    if (targetMr != null &&
                                        targetMr.parameterTypes.size == 2 &&
                                        targetMr.parameterTypes[0] == "Ljava/lang/String;" &&
                                        targetMr.returnType == "V" &&
                                        checkInst.registerC == pkgReg
                                    ) {
                                        classDefByOrNull(targetMr.definingClass)?.let { defClass ->
                                            defClass.methods.firstOrNull {
                                                it.name == targetMr.name && it.parameterTypes == targetMr.parameterTypes
                                            }?.let { targetMethod ->
                                                targetMethodsToHook.add(targetMethod)
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

        targetMethodsToHook.forEach { targetMethod ->
            val mutableDefClass = mutableClassDefBy(targetMethod.definingClass)
            val mutableTargetMethod = mutableDefClass.findMutableMethodOf(targetMethod)
            mutableTargetMethod.addInstructions(
                0,
                """
                invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->spoofPackageName(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
                """.trimIndent(),
            )
        }

        executeBlock()
    }

    finalize {
        if (PackageNameConfig.isPackageNameChangeEnabled) {
            val packageName = PackageNameConfig.resolvePackageName(fromPackageName, toPackageName)
            if (packageName != fromPackageName) {
                primeMethodFingerprint?.let { transformPrimeMethod(packageName) }
                transformStringReferences transform@{ string ->
                    packageNameTransform(fromPackageName, packageName)(string)
                }
            }
        }

        finalizeBlock()
    }

    block()
}

/**
 * A collection of permissions, intents and content provider authorities
 * that are present in GmsCore which need to be transformed.
 */
private object Constants {
    /**
     * All permissions.
     */
    val PERMISSIONS = setOf(
        "com.google.android.c2dm.permission.RECEIVE",
        "com.google.android.c2dm.permission.SEND",
        "com.google.android.gms.auth.api.phone.permission.SEND",
        "com.google.android.gms.permission.AD_ID",
        "com.google.android.gms.permission.AD_ID_NOTIFICATION",
        "com.google.android.gms.permission.CAR_FUEL",
        "com.google.android.gms.permission.CAR_INFORMATION",
        "com.google.android.gms.permission.CAR_MILEAGE",
        "com.google.android.gms.permission.CAR_SPEED",
        "com.google.android.gms.permission.CAR_VENDOR_EXTENSION",
        "com.google.android.googleapps.permission.GOOGLE_AUTH",
        "com.google.android.googleapps.permission.GOOGLE_AUTH.cp",
        "com.google.android.googleapps.permission.GOOGLE_AUTH.local",
        "com.google.android.googleapps.permission.GOOGLE_AUTH.mail",
        "com.google.android.googleapps.permission.GOOGLE_AUTH.writely",
        "com.google.android.gtalkservice.permission.GTALK_SERVICE",
        "com.google.android.providers.gsf.permission.READ_GSERVICES",
    )

    /**
     * All intent actions.
     */
    val ACTIONS = setOf(
        "com.google.android.c2dm.intent.RECEIVE",
        "com.google.android.c2dm.intent.REGISTER",
        "com.google.android.c2dm.intent.REGISTRATION",
        "com.google.android.c2dm.intent.UNREGISTER",
        "com.google.android.contextmanager.service.ContextManagerService.START",
        "com.google.android.gcm.intent.SEND",
        "com.google.android.location.GeocodeProvider",
        "com.google.android.location.internal.GoogleLocationManagerService.START",
        "com.google.android.location.reporting.service.START",
        "com.google.android.location.settings.GOOGLE_LOCATION_SETTINGS",
        "com.google.android.location.settings.LOCATION_REPORTING_SETTINGS",
        "com.google.android.gms.accounts.ACCOUNT_SERVICE",
        "com.google.android.gms.accountsettings.ACCOUNT_PREFERENCES_SETTINGS",
        "com.google.android.gms.accountsettings.action.BROWSE_SETTINGS",
        "com.google.android.gms.accountsettings.action.VIEW_SETTINGS",
        "com.google.android.gms.accountsettings.MY_ACCOUNT",
        "com.google.android.gms.accountsettings.PRIVACY_SETTINGS",
        "com.google.android.gms.accountsettings.SECURITY_SETTINGS",
        "com.google.android.gms.ads.gservice.START",
        "com.google.android.gms.ads.identifier.service.EVENT_ATTESTATION",
        "com.google.android.gms.ads.service.CACHE",
        "com.google.android.gms.ads.service.CONSENT_LOOKUP",
        "com.google.android.gms.ads.service.HTTP",
        "com.google.android.gms.analytics.service.START",
        "com.google.android.gms.app.settings.GoogleSettingsLink",
        "com.google.android.gms.appstate.service.START",
        "com.google.android.gms.appusage.service.START",
        "com.google.android.gms.asterism.service.START",
        "com.google.android.gms.audiomodem.service.AudioModemService.START",
        "com.google.android.gms.audit.service.START",
        "com.google.android.gms.auth.account.authapi.START",
        "com.google.android.gms.auth.account.authenticator.auto.service.START",
        "com.google.android.gms.auth.account.authenticator.chromeos.START",
        "com.google.android.gms.auth.account.authenticator.tv.service.START",
        "com.google.android.gms.auth.account.data.service.START",
        "com.google.android.gms.auth.api.credentials.PICKER",
        "com.google.android.gms.auth.api.credentials.service.START",
        "com.google.android.gms.auth.api.identity.service.authorization.START",
        "com.google.android.gms.auth.api.identity.service.credentialsaving.START",
        "com.google.android.gms.auth.api.identity.service.signin.START",
        "com.google.android.gms.auth.api.phone.service.InternalService.START",
        "com.google.android.gms.auth.api.signin.service.START",
        "com.google.android.gms.auth.be.appcert.AppCertService",
        "com.google.android.gms.auth.blockstore.service.START",
        "com.google.android.gms.auth.config.service.START",
        "com.google.android.gms.auth.cryptauth.cryptauthservice.START",
        "com.google.android.gms.auth.GOOGLE_SIGN_IN",
        "com.google.android.gms.auth.login.LOGIN",
        "com.google.android.gms.auth.proximity.devicesyncservice.START",
        "com.google.android.gms.auth.proximity.securechannelservice.START",
        "com.google.android.gms.auth.proximity.START",
        "com.google.android.gms.auth.service.START",
        "com.google.android.gms.backup.ACTION_BACKUP_SETTINGS",
        "com.google.android.gms.backup.G1_BACKUP",
        "com.google.android.gms.backup.G1_RESTORE",
        "com.google.android.gms.backup.GMS_MODULE_RESTORE",
        "com.google.android.gms.beacon.internal.IBleService.START",
        "com.google.android.gms.car.service.START",
        "com.google.android.gms.carrierauth.service.START",
        "com.google.android.gms.cast.firstparty.START",
        "com.google.android.gms.cast.remote_display.service.START",
        "com.google.android.gms.cast.service.BIND_CAST_DEVICE_CONTROLLER_SERVICE",
        "com.google.android.gms.cast_mirroring.service.START",
        "com.google.android.gms.checkin.BIND_TO_SERVICE",
        "com.google.android.gms.chromesync.service.START",
        "com.google.android.gms.clearcut.service.START",
        "com.google.android.gms.common.account.CHOOSE_ACCOUNT",
        "com.google.android.gms.common.download.START",
        "com.google.android.gms.common.service.START",
        "com.google.android.gms.common.telemetry.service.START",
        "com.google.android.gms.config.START",
        "com.google.android.gms.constellation.service.START",
        "com.google.android.gms.credential.manager.service.firstparty.START",
        "com.google.android.gms.deviceconnection.service.START",
        "com.google.android.gms.drive.ApiService.RESET_AFTER_BOOT",
        "com.google.android.gms.drive.ApiService.START",
        "com.google.android.gms.drive.ApiService.STOP",
        "com.google.android.gms.droidguard.service.INIT",
        "com.google.android.gms.droidguard.service.PING",
        "com.google.android.gms.droidguard.service.START",
        "com.google.android.gms.enterprise.loader.service.START",
        "com.google.android.gms.facs.cache.service.START",
        "com.google.android.gms.facs.internal.service.START",
        "com.google.android.gms.feedback.internal.IFeedbackService",
        "com.google.android.gms.fido.credentialstore.internal_service.START",
        "com.google.android.gms.fido.fido2.privileged.START",
        "com.google.android.gms.fido.fido2.regular.START",
        "com.google.android.gms.fido.fido2.zeroparty.START",
        "com.google.android.gms.fido.sourcedevice.service.START",
        "com.google.android.gms.fido.targetdevice.internal_service.START",
        "com.google.android.gms.fido.u2f.privileged.START",
        "com.google.android.gms.fido.u2f.thirdparty.START",
        "com.google.android.gms.fido.u2f.zeroparty.START",
        "com.google.android.gms.fitness.BleApi",
        "com.google.android.gms.fitness.ConfigApi",
        "com.google.android.gms.fitness.GoalsApi",
        "com.google.android.gms.fitness.GoogleFitnessService.START",
        "com.google.android.gms.fitness.HistoryApi",
        "com.google.android.gms.fitness.InternalApi",
        "com.google.android.gms.fitness.RecordingApi",
        "com.google.android.gms.fitness.SensorsApi",
        "com.google.android.gms.fitness.SessionsApi",
        "com.google.android.gms.fonts.service.START",
        "com.google.android.gms.freighter.service.START",
        "com.google.android.gms.games.internal.connect.service.START",
        "com.google.android.gms.games.PLAY_GAMES_UPGRADE",
        "com.google.android.gms.games.service.START",
        "com.google.android.gms.gass.START",
        "com.google.android.gms.gmscompliance.service.START",
        "com.google.android.gms.googlehelp.HELP",
        "com.google.android.gms.googlehelp.service.GoogleHelpService.START",
        "com.google.android.gms.growth.service.START",
        "com.google.android.gms.herrevad.services.LightweightNetworkQualityAndroidService.START",
        "com.google.android.gms.icing.INDEX_SERVICE",
        "com.google.android.gms.icing.LIGHTWEIGHT_INDEX_SERVICE",
        "com.google.android.gms.identity.service.BIND",
        "com.google.android.gms.inappreach.service.START",
        "com.google.android.gms.instantapps.START",
        "com.google.android.gms.kids.service.START",
        "com.google.android.gms.languageprofile.service.START",
        "com.google.android.gms.learning.internal.dynamitesupport.START",
        "com.google.android.gms.learning.intservice.START",
        "com.google.android.gms.learning.predictor.START",
        "com.google.android.gms.learning.trainer.START",
        "com.google.android.gms.learning.training.background.START",
        "com.google.android.gms.location.places.GeoDataApi",
        "com.google.android.gms.location.places.PlaceDetectionApi",
        "com.google.android.gms.location.places.PlacesApi",
        "com.google.android.gms.location.reporting.service.START",
        "com.google.android.gms.location.settings.LOCATION_HISTORY",
        "com.google.android.gms.location.settings.GOOGLE_LOCATION_SETTINGS",
        "com.google.android.gms.location.settings.LOCATION_REPORTING_SETTINGS",
        "com.google.android.gms.locationsharing.api.START",
        "com.google.android.gms.locationsharingreporter.service.START",
        "com.google.android.gms.lockbox.service.START",
        "com.google.android.gms.matchstick.lighter.service.START",
        "com.google.android.gms.mdm.services.DeviceManagerApiService.START",
        "com.google.android.gms.mdm.services.START",
        "com.google.android.gms.mdns.service.START",
        "com.google.android.gms.measurement.START",
        "com.google.android.gms.nearby.bootstrap.service.NearbyBootstrapService.START",
        "com.google.android.gms.nearby.connection.service.START",
        "com.google.android.gms.nearby.fastpair.START",
        "com.google.android.gms.nearby.messages.service.NearbyMessagesService.START",
        "com.google.android.gms.nearby.sharing.service.NearbySharingService.START",
        "com.google.android.gms.nearby.sharing.START_SERVICE",
        "com.google.android.gms.notifications.service.START",
        "com.google.android.gms.ocr.service.internal.START",
        "com.google.android.gms.ocr.service.START",
        "com.google.android.gms.oss.licenses.service.START",
        "com.google.android.gms.payse.service.BIND",
        "com.google.android.gms.people.contactssync.service.START",
        "com.google.android.gms.people.service.START",
        "com.google.android.gms.phenotype.service.START",
        "com.google.android.gms.photos.autobackup.service.START",
        "com.google.android.gms.playlog.service.START",
        "com.google.android.gms.plus.service.default.INTENT",
        "com.google.android.gms.plus.service.image.INTENT",
        "com.google.android.gms.plus.service.internal.START",
        "com.google.android.gms.plus.service.START",
        "com.google.android.gms.potokens.service.START",
        "com.google.android.gms.pseudonymous.service.START",
        "com.google.android.gms.rcs.START",
        "com.google.android.gms.reminders.service.START",
        "com.google.android.gms.romanesco.MODULE_BACKUP_AGENT",
        "com.google.android.gms.romanesco.service.START",
        "com.google.android.gms.safetynet.service.START",
        "com.google.android.gms.scheduler.ACTION_PROXY_SCHEDULE",
        "com.google.android.gms.search.service.SEARCH_AUTH_START",
        "com.google.android.gms.semanticlocation.service.START_ODLH",
        "com.google.android.gms.sesame.service.BIND",
        "com.google.android.gms.settings.EXPOSURE_NOTIFICATION_SETTINGS",
        "com.google.android.gms.setup.auth.SecondDeviceAuth.START",
        "com.google.android.gms.signin.service.START",
        "com.google.android.gms.smartdevice.d2d.SourceDeviceService.START",
        "com.google.android.gms.smartdevice.d2d.TargetDeviceService.START",
        "com.google.android.gms.smartdevice.directtransfer.SourceDirectTransferService.START",
        "com.google.android.gms.smartdevice.directtransfer.TargetDirectTransferService.START",
        "com.google.android.gms.smartdevice.postsetup.PostSetupService.START",
        "com.google.android.gms.smartdevice.setup.accounts.AccountsService.START",
        "com.google.android.gms.smartdevice.wifi.START_WIFI_HELPER_SERVICE",
        "com.google.android.gms.social.location.activity.service.START",
        "com.google.android.gms.speech.service.START",
        "com.google.android.gms.statementservice.EXECUTE",
        "com.google.android.gms.stats.ACTION_UPLOAD_DROPBOX_ENTRIES",
        "com.google.android.gms.tapandpay.service.BIND",
        "com.google.android.gms.telephonyspam.service.START",
        "com.google.android.gms.testsupport.service.START",
        "com.google.android.gms.thunderbird.service.START",
        "com.google.android.gms.trustagent.BridgeApi.START",
        "com.google.android.gms.trustagent.StateApi.START",
        "com.google.android.gms.trustagent.trustlet.trustletmanagerservice.BIND",
        "com.google.android.gms.trustlet.bluetooth.service.BIND",
        "com.google.android.gms.trustlet.connectionlessble.service.BIND",
        "com.google.android.gms.trustlet.face.service.BIND",
        "com.google.android.gms.trustlet.nfc.service.BIND",
        "com.google.android.gms.trustlet.onbody.service.BIND",
        "com.google.android.gms.trustlet.place.service.BIND",
        "com.google.android.gms.trustlet.voiceunlock.service.BIND",
        "com.google.android.gms.udc.service.START",
        "com.google.android.gms.update.START_API_SERVICE",
        "com.google.android.gms.update.START_SERVICE",
        "com.google.android.gms.update.START_SINGLE_USER_API_SERVICE",
        "com.google.android.gms.update.START_TV_API_SERVICE",
        "com.google.android.gms.usagereporting.service.START",
        "com.google.android.gms.userlocation.service.START",
        "com.google.android.gms.vehicle.cabin.service.START",
        "com.google.android.gms.vehicle.climate.service.START",
        "com.google.android.gms.vehicle.info.service.START",
        "com.google.android.gms.wallet.service.BIND",
        "com.google.android.gms.walletp2p.service.firstparty.BIND",
        "com.google.android.gms.walletp2p.service.zeroparty.BIND",
        "com.google.android.gms.wearable.BIND",
        "com.google.android.gms.wearable.BIND_LISTENER",
        "com.google.android.gms.wearable.DATA_CHANGED",
        "com.google.android.gms.wearable.MESSAGE_RECEIVED",
        "com.google.android.gms.wearable.NODE_CHANGED",
        "com.google.android.gsf.action.GET_GLS",
        "com.google.android.location.internal.GoogleLocationManagerService.START",
        "com.google.android.location.reporting.service.START",
        "com.google.android.location.settings.LOCATION_REPORTING_SETTINGS",
        "com.google.android.location.settings.GOOGLE_LOCATION_SETTINGS",
        "com.google.android.location.GeocodeProvider",
        "com.google.android.mdd.service.START",
        "com.google.android.mdh.service.listener.START",
        "com.google.android.mdh.service.START",
        "com.google.android.mobstore.service.START",
        "com.google.firebase.auth.api.gms.service.START",
        "com.google.firebase.dynamiclinks.service.START",
        "com.google.iid.TOKEN_REQUEST",
        "com.google.android.gms.location.places.ui.PICK_PLACE",
    )

    /**
     * All content provider authorities.
     */
    val AUTHORITIES = setOf(
        "com.google.android.gms.auth.accounts",
        "com.google.android.gms.chimera",
        "com.google.android.gms.fonts",
        "com.google.android.gms.people",
        "com.google.android.gms.people.avatar",
        "com.google.android.gms.people.profile",
        "com.google.android.gms.phenotype",
        "com.google.android.gsf.gservices",
        "com.google.settings",
        "com.google.android.gms.vtm.provider",
        "org.microg.gms.vtm.provider",
        "com.google.android.gms.maps_dynamite",
        "com.google.android.gms.maps_core_dynamite",
        "com.google.android.gms.maps_legacy_dynamite",
    )
}


/**
 * Abstract resource patch that allows Google apps to run without root and under a different package name
 * by using GmsCore instead of Google Play Services.
 *
 * @param fromPackageName The package name of the original app.
 * @param toPackageName The package name to fall back to if no custom package name is specified in patch options.
 * @param spoofedPackageSignature The signature of the package to spoof to.
 * @param executeBlock The additional execution block of the patch.
 * @param block The additional block to build the patch.
 */
fun gmsCoreSupportResourcePatch(
    fromPackageName: String,
    toPackageName: String,
    spoofedPackageSignature: String,
    screen: BasePreferenceScreen.Screen,
    executeBlock: ResourcePatchContext.() -> Unit = {},
    block: ResourcePatchBuilder.() -> Unit = {},
) = resourcePatch {
    execute {
        /**
         * Add metadata to manifest to support spoofing the package name and signature of GmsCore.
         */
        fun addSpoofingMetadata() {
            fun Node.adoptChild(
                tagName: String,
                block: Element.() -> Unit,
            ) {
                val child = ownerDocument.createElement(tagName)
                child.block()
                appendChild(child)
            }

            document("AndroidManifest.xml").use { document ->
                val applicationNode =
                    document
                         .getElementsByTagName("application")
                         .item(0)

                // Spoof package name and signature.
                applicationNode.adoptChild("meta-data") {
                    setAttribute("android:name", "$GMS_CORE_VENDOR_GROUP_ID.android.gms.SPOOFED_PACKAGE_NAME")
                    setAttribute("android:value", fromPackageName)
                }

                applicationNode.adoptChild("meta-data") {
                    setAttribute("android:name", "$GMS_CORE_VENDOR_GROUP_ID.android.gms.SPOOFED_PACKAGE_SIGNATURE")
                    setAttribute("android:value", spoofedPackageSignature)
                }

                // GmsCore presence detection in extension.
                applicationNode.adoptChild("meta-data") {
                    // TODO: The name of this metadata should be dynamic.
                    setAttribute("android:name", "app.revanced.MICROG_PACKAGE_NAME")
                    setAttribute("android:value", "$GMS_CORE_VENDOR_GROUP_ID.android.gms")
                }

                // Add REQUEST_INSTALL_PACKAGES permission for in-app updates
                val manifestNode = document.getElementsByTagName("manifest").item(0)
                val existingPermissions = document.getElementsByTagName("uses-permission")
                val alreadyAdded = (0 until existingPermissions.length).any { i ->
                    (existingPermissions.item(i) as? Element)?.getAttribute("android:name") == "android.permission.REQUEST_INSTALL_PACKAGES"
                }
                if (!alreadyAdded) {
                    val permissionNode = document.createElement("uses-permission")
                    permissionNode.setAttribute("android:name", "android.permission.REQUEST_INSTALL_PACKAGES")
                    manifestNode.appendChild(permissionNode)
                }
            }
        }

        /**
         * Patch the manifest to support GmsCore.
         */
        fun patchManifest() {
            val transformations = mutableMapOf(
                "com.google.android.c2dm" to "$GMS_CORE_VENDOR_GROUP_ID.android.c2dm",
                "</queries>" to "<package android:name=\"$GMS_CORE_VENDOR_GROUP_ID.android.gms\"/></queries>",
            )

            val manifest = get("AndroidManifest.xml")
            manifest.writeText(
                transformations.entries.fold(manifest.readText()) { acc, (from, to) ->
                    acc.replace(
                        from,
                        to,
                    )
                },
            )
        }

        patchManifest()
        addSpoofingMetadata()

        screen.addPreferences(
            IntentPreference(
                "microg_settings",
                intent = IntentPreference.Intent("", "org.microg.gms.ui.SettingsActivity") {
                    "$GMS_CORE_VENDOR_GROUP_ID.android.gms"
                }
            )
        )

        executeBlock()
    }

    block()
}


