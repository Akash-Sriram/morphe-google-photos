package app.morphe.patches.googlephotos.misc.flags

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.returnEarly

@Suppress("unused")
val phenotypeFlagManagerPatch = bytecodePatch(
    name = "Flag manager & Toolkit",
    description = "Enables an in-app flag manager and Morphe toolkit in Photos Settings to toggle experimental UI redesigns, video editor tools, and feature flags.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)

    val componentsOption = stringOption(
        key = "components",
        default = "flags",
        values = mapOf(
            "flags" to "Flag manager only",
            "both" to "Both (Flag manager & Toolkit)",
            "toolkit" to "Toolkit only",
        ),
        title = "Enabled components",
        description = "Choose which components to show: Flag Manager, Morphe Toolkit, or Both.",
    )

    execute {
        SettingsActivityV2OnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static {p0}, Lapp/morphe/extension/shared/patches/PhenotypeFlagManager;->injectSettingsCard(Landroid/app/Activity;)V"
        )
        if (PhotosAboutSettingsActivityV2OnCreateFingerprint.methodOrNull != null) {
            PhotosAboutSettingsActivityV2OnCreateFingerprint.method.addInstruction(
                0,
                "invoke-static {p0}, Lapp/morphe/extension/shared/patches/PhenotypeFlagManager;->hookAboutActivity(Landroid/app/Activity;)V"
            )
        }
    }

    finalize {
        val selected = componentsOption.value ?: componentsOption.default ?: "flags"
        PhenotypeComponentsModeFingerprint.method.returnEarly(selected)
    }
}
