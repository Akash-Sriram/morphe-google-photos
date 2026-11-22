/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/googlephotos/misc/gms/GmsCoreSupportPatch.kt
 */
package app.morphe.patches.googlephotos.misc.gms

import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability
import app.morphe.patches.googlephotos.misc.extension.sharedExtensionPatch
import app.morphe.patches.googlephotos.misc.gms.Constants.MORPHE_PHOTOS_PACKAGE_NAME
import app.morphe.patches.googlephotos.misc.gms.Constants.PHOTOS_PACKAGE_NAME
import app.morphe.patches.googlephotos.misc.gms.Constants.PHOTOS_PACKAGE_SIGNATURE
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.shared.misc.gms.PackageNameConfig
import app.morphe.patches.shared.misc.gms.gmsCoreSupportPatch
import app.morphe.patches.shared.misc.settings.preference.BasePreferenceScreen
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.util.asSequence
import app.morphe.util.getNode
import org.w3c.dom.Element

@Suppress("unused")
val gmsCoreSupportPatch = gmsCoreSupportPatch(
    fromPackageName = PHOTOS_PACKAGE_NAME,
    toPackageName = MORPHE_PHOTOS_PACKAGE_NAME,
    spoofedPackageSignature = PHOTOS_PACKAGE_SIGNATURE,
    mainActivityOnCreateFingerprint = HomeActivityOnCreateFingerprint,
    extensionPatch = sharedExtensionPatch,
    gmsCoreSupportResourcePatchFactory = ::gmsCoreSupportResourcePatch,
    executeBlock = {
        // Modular internal subsystems
        applyGmsCoreAvatar()
        applyGmsCoreMaps()
        applyGmsCoreBackup()
        applyGmsCoreMars()
        applyGmsCoreSystemCompat()
    },
    finalizeBlock = {
        applyGmsCoreMarsFinalize()
    },
) {
    availability { installer, _ ->
        when (installer) {
            InstallerType.MOUNT -> PatchAvailability.UNAVAILABLE
            InstallerType.STANDARD, InstallerType.SHIZUKU -> PatchAvailability.REQUIRED
        }
    }

    dependsOn(
        deviceComplianceCheckPatch,
    )

    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)
}

/**
 * Minimal preference screen used only to satisfy the shared GmsCore support
 * resource patch API. Google Photos does not currently expose a dedicated
 * Morphe settings UI, so the committed screen is intentionally a no-op.
 */
private object DummyPreferenceScreen : BasePreferenceScreen() {
    val SCREEN = Screen(
        key = "morphe_settings_googlephotos_screen_1_misc",
        summaryKey = null,
    )

    override fun commit(screen: PreferenceScreenPreference) {
        // No-op: Google Photos does not have a dedicated Morphe settings screen yet.
    }
}

private fun gmsCoreSupportResourcePatch() =
    app.morphe.patches.shared.misc.gms.gmsCoreSupportResourcePatch(
        fromPackageName = PHOTOS_PACKAGE_NAME,
        toPackageName = MORPHE_PHOTOS_PACKAGE_NAME,
        spoofedPackageSignature = PHOTOS_PACKAGE_SIGNATURE,
        screen = DummyPreferenceScreen.SCREEN,
    ) {
        finalize {
            if (PackageNameConfig.isPackageNameChangeEnabled) {
                val targetPackageName = PackageNameConfig.effectivePackageName.ifEmpty { MORPHE_PHOTOS_PACKAGE_NAME }

                document("AndroidManifest.xml").use { document ->
                    val manifest = document.getNode("manifest") as Element
                    val originalPackageName = manifest.getAttribute("package")

                    manifest.setAttribute("package", targetPackageName)

                    val permissions = manifest.getElementsByTagName("permission").asSequence()
                    val usesPermissions = manifest.getElementsByTagName("uses-permission").asSequence()

                    (permissions + usesPermissions)
                        .map { it as Element }
                        .forEach { elem ->
                            val name = elem.getAttribute("android:name")
                            if (name.startsWith("$originalPackageName.")) {
                                elem.setAttribute("android:name", name.replace(originalPackageName, targetPackageName))
                            }
                        }

                    val components = (
                        manifest.getElementsByTagName("activity").asSequence() +
                        manifest.getElementsByTagName("service").asSequence() +
                        manifest.getElementsByTagName("provider").asSequence() +
                        manifest.getElementsByTagName("receiver").asSequence()
                    ).map { it as Element }

                    for (comp in components) {
                        val perm = comp.getAttribute("android:permission")
                        if (perm.startsWith("$originalPackageName.")) {
                            comp.setAttribute("android:permission", perm.replace(originalPackageName, targetPackageName))
                        }
                    }

                    val providers = manifest.getElementsByTagName("provider").asSequence()
                    for (node in providers) {
                        val provider = node as Element
                        val authorities = provider.getAttribute("android:authorities")
                        if (authorities.contains("api.mars")) {
                            provider.setAttribute("android:authorities", "$targetPackageName.api.mars")
                        } else if (authorities.startsWith("$originalPackageName.")) {
                            provider.setAttribute("android:authorities", authorities.replace(originalPackageName, targetPackageName))
                        }
                    }
                }
            }

            document("AndroidManifest.xml").use { document ->
                val manifest = document.getNode("manifest") as Element
                val targetPackageName = if (PackageNameConfig.isPackageNameChangeEnabled) {
                    PackageNameConfig.effectivePackageName.ifEmpty { MORPHE_PHOTOS_PACKAGE_NAME }
                } else {
                    PHOTOS_PACKAGE_NAME
                }

                // Fix for Issue #80: On Android < 13, AndroidX dynamic receivers require this permission in manifest.
                val dynamicReceiverPerm = "$targetPackageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
                val existingPerms = manifest.getElementsByTagName("permission").asSequence()
                    .map { (it as Element).getAttribute("android:name") }
                    .toSet()
                if (dynamicReceiverPerm !in existingPerms) {
                    val permNode = document.createElement("permission").apply {
                        setAttribute("android:name", dynamicReceiverPerm)
                        setAttribute("android:protectionLevel", "signature")
                    }
                    manifest.appendChild(permNode)
                }

                val existingUsesPerms = manifest.getElementsByTagName("uses-permission").asSequence()
                    .map { (it as Element).getAttribute("android:name") }
                    .toSet()
                if (dynamicReceiverPerm !in existingUsesPerms) {
                    val usesPermNode = document.createElement("uses-permission").apply {
                        setAttribute("android:name", dynamicReceiverPerm)
                    }
                    manifest.appendChild(usesPermNode)
                }

                if ("android.permission.MANAGE_EXTERNAL_STORAGE" !in existingUsesPerms) {
                    val permNode = document.createElement("uses-permission").apply {
                        setAttribute("android:name", "android.permission.MANAGE_EXTERNAL_STORAGE")
                    }
                    manifest.appendChild(permNode)
                }
            }

            // Bundle official phenotype baseline assets into APK assets
            val phenotypeAssets = listOf(
                "com.google.android.apps.photos.phenotype.xml",
                "phenotype_account_file.xml",
            )
            for (name in phenotypeAssets) {
                val stream = object {}.javaClass.classLoader.getResourceAsStream("app/morphe/patches/googlephotos/phenotype/$name")
                if (stream != null) {
                    val target = this["assets/phenotype/$name", false]
                    target.parentFile?.mkdirs()
                    stream.use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            // Bundle authentic Google Sans font assets into APK assets
            val fontAssets = listOf(
                "Google_Sans_Text-Bold.ttf",
                "Google_Sans_Text-Medium.ttf",
                "Google_Sans_Text-Regular.ttf",
            )
            for (name in fontAssets) {
                val stream = object {}.javaClass.classLoader.getResourceAsStream("app/morphe/patches/googlephotos/fonts/$name")
                if (stream != null) {
                    val target = this["assets/fonts/$name", false]
                    target.parentFile?.mkdirs()
                    stream.use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
