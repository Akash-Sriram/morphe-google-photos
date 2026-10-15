package app.morphe.patches.googlephotos.misc.models

import app.morphe.patcher.Fingerprint

internal object ModelDownloadManagerClinitFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("ModelDownloadManager"),
)
