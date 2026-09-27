package app.morphe.patches.googlephotos.misc.toolkit

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.googlephotos.misc.flags.SettingsActivityV2OnCreateFingerprint
import app.morphe.patches.shared.compat.AppCompatibilities

@Suppress("unused")
val photosToolkitPatch = bytecodePatch(
    name = "Enable Photos Library Toolkit",
    description = "Enables an in-app library toolkit in Photos Settings to analyze storage quota, find photos not in any album, and detect duplicates.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)

    execute {
        SettingsActivityV2OnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static {p0}, Lapp/morphe/extension/shared/patches/toolkit/PhotosToolkitManager;->injectSettingsCard(Landroid/app/Activity;)V",
        )
    }
}
