package app.morphe.patches.googlephotos.misc.flags

import app.morphe.patcher.Fingerprint

object SettingsActivityV2OnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/photos/settings/SettingsActivityV2;",
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
)

object PhotosAboutSettingsActivityV2OnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/photos/settings/PhotosAboutSettingsActivityV2;",
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
)




internal object SkottieFontUriResolverFingerprint : Fingerprint(
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Ljava/lang/String;", "Landroid/os/CancellationSignal;"),
)

internal object SkottieFontLoaderDispatcherFingerprint : Fingerprint(
    custom = { method, _ ->
        method.parameterTypes.size == 8 &&
            method.parameterTypes[0] == "Landroid/content/Context;" &&
            method.parameterTypes.any { it.contains("ConcurrentHashMap") } &&
            method.returnType != "V"
    },
)

