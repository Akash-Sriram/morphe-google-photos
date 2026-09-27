package app.morphe.extension.shared.patches.toolkit;

import android.app.Activity;

import app.morphe.extension.shared.patches.PhenotypeFlagManager;

/**
 * Entry point coordinator for Morphe Photos Library Toolkit.
 * Delegates settings pill injection to the unified 3-button dock in PhenotypeFlagManager.
 */
public final class PhotosToolkitManager {

    private PhotosToolkitManager() {}

    /**
     * Injects the unified 3-button dock (Flags, Toolkit, Logs) into Google Photos Settings.
     */
    public static void injectSettingsCard(Activity activity) {
        PhenotypeFlagManager.injectSettingsCard(activity);
    }
}
