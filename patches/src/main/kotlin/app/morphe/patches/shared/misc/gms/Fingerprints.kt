/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/shared/misc/gms/Fingerprints.kt
 */
package app.morphe.patches.shared.misc.gms

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

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
 * The GNP request header filters. With an API key, X-Android-Package is set to
 * Context.getPackageName() and X-Android-Cert to the SHA-1 of the app signature.
 */
private fun gnpAndroidHeadersFilters() = listOf(
    methodCall(smali = "Landroid/content/Context;->getPackageName()Ljava/lang/String;"),
    opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
    // Sets the X-Android-Package header.
    methodCall(
        parameters = listOf("L", "Ljava/lang/String;"),
        returnType = "V",
        location = MatchAfterWithin(3)
    ),
    // Sets the X-Android-Cert header.
    methodCall(
        parameters = listOf("L", "Ljava/lang/String;"),
        returnType = "V",
        location = MatchAfterWithin(5)
    )
)

/**
 * Adds the authentication headers of GNP registration requests (notifications-pa.googleapis.com).
 */
internal object GnpRegistrationHeadersFingerprint : Fingerprint(
    filters = gnpAndroidHeadersFilters(),
    strings = listOf("One of Account Name, Zwieback or API Key must be set.")
)

private object GnpRequestHeadersClassFingerprint : Fingerprint(
    strings = listOf("One of Account representation or API Key must be set.")
)

/**
 * Adds the authentication headers of the other GNP requests.
 */
internal object GnpRequestHeadersFingerprint : Fingerprint(
    classFingerprint = GnpRequestHeadersClassFingerprint,
    filters = gnpAndroidHeadersFilters()
)
