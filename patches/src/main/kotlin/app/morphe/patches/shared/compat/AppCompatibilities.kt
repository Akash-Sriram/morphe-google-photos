package app.morphe.patches.shared.compat

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

internal object AppCompatibilities {

    val GOOGLE_PHOTOS = Compatibility(
        name = "Google Photos",
        packageName = "com.google.android.apps.photos",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFC3F3C,
        targets = listOf(
            AppTarget(
                version = "7.96.0.993165104",
                versionCodes = mapOf(
                    SupportedAbi.ARM64_V8A to 52511205,
                    SupportedAbi.ARMEABI_V7A to 52511175,
                ),
            ),
        ),
    )
}
