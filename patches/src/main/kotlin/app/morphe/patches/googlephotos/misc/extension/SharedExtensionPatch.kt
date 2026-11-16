/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/googlephotos/misc/extension/SharedExtensionPatch.kt
 */
package app.morphe.patches.googlephotos.misc.extension

import app.morphe.patches.googlephotos.misc.gms.HomeActivityOnCreateFingerprint
import app.morphe.patches.googlephotos.misc.gms.PhotosApplicationOnCreateFingerprint
import app.morphe.patches.shared.misc.extension.ExtensionHook
import app.morphe.patches.shared.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.bytecodePatch
import org.w3c.dom.Element

private val diagnosticSharingPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val manifest = doc.getElementsByTagName("manifest").item(0) as Element
            val application = doc.getElementsByTagName("application").item(0) as Element
            val provider = doc.createElement("provider")
            provider.setAttribute("android:name", "app.morphe.extension.shared.diagnostics.DiagnosticProvider")
            provider.setAttribute("android:authorities", manifest.getAttribute("package") + ".morphe.diagnostics")
            provider.setAttribute("android:exported", "false")
            provider.setAttribute("android:grantUriPermissions", "true")
            application.appendChild(provider)
        }
    }
}

private class PhotosApplicationInitHook : ExtensionHook(
    fingerprint = PhotosApplicationOnCreateFingerprint,
    insertIndexResolver = { 0 },
    contextRegisterResolver = { "p0" },
)

private class HomeActivityInitHook : ExtensionHook(
    fingerprint = HomeActivityOnCreateFingerprint,
    insertIndexResolver = { 0 },
    contextRegisterResolver = { "p0" },
)

internal val photosApplicationInitHook: ExtensionHook = PhotosApplicationInitHook()
internal val homeActivityInitHook: ExtensionHook = HomeActivityInitHook()

private val baseSharedExtensionPatch = sharedExtensionPatch(
    isYouTubeOrYouTubeMusic = false,
    photosApplicationInitHook,
    homeActivityInitHook,
)


val sharedExtensionPatch = bytecodePatch {
    dependsOn(baseSharedExtensionPatch, diagnosticSharingPatch)
}
