/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/shared/misc/gms/Fingerprints.kt
 */
package app.morphe.patches.shared.misc.gms

import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal object GooglePlayUtilityFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "I",
    parameters = listOf("L", "I"),
    strings = listOf(
        "This should never happen.",
        "MetadataValueReader",
        "com.google.android.gms",
    )
)

internal object ServiceCheckFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("L", "I"),
    strings = listOf("Google Play Services not available")
)

internal object GmsCoreSupportFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getGmsCoreVendorGroupId",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf()
)

internal object OriginalPackageNameExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getOriginalPackageName",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf()
)

/**
 * Matches the GNP (Google Notifications Platform / Chime) HTTP request builder method
 * which adds headers to requests destined for notifications-pa.googleapis.com.
 */
internal object GnpRequestBuilderFingerprint : Fingerprint(
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    returnType = "V",
    custom = { _, classDef ->
        classDef.methods.any { m ->
            m.implementation?.instructions?.any { insn ->
                insn.getReference<StringReference>()?.string == "Property \"headers\" has not been set"
            } == true
        }
    },
)

/**
 * Matches io.grpc.Metadata.put(Metadata.Key, Object) method across Google apps.
 */
internal object GrpcMetadataPutFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, classDef ->
        method.parameterTypes.size == 2 &&
            method.parameterTypes[0].startsWith("L") &&
            method.parameterTypes[1] == "Ljava/lang/Object;" &&
            classDef.methods.any { m ->
                m.implementation?.instructions?.any { insn ->
                    insn.getReference<StringReference>()?.string == "Metadata("
                } == true
            }
    },
)

/**
 * Matches the GNP certificate hash calculation method in com.google.android.libraries.notifications
 * which queries PackageInfo.signatures and computes the SHA-1 digest.
 */
internal object GnpCertProviderFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    custom = { method, _ ->
        val instructions = method.implementation?.instructions
        if (instructions == null) {
            false
        } else {
            var hasSigs = false
            var hasSha1 = false
            for (insn in instructions) {
                val ref = insn.getReference<FieldReference>()
                if (ref?.name == "signatures") {
                    hasSigs = true
                }
                val strRef = insn.getReference<StringReference>()
                if (strRef?.string == "SHA-1") {
                    hasSha1 = true
                }
            }
            hasSigs && hasSha1
        }
    },
)

