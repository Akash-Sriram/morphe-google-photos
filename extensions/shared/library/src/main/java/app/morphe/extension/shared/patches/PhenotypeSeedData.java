package app.morphe.extension.shared.patches;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.patches.flags.PhotoFlagsRegistry;

/**
 * Minimal seed & sync provider for Google Photos (Non-Root).
 * Purely applies the exact 26 Curated Morphe UI flags and configures account files.
 * Completely stripped of all legacy 2.4k stock phenotype dump bloat.
 */
public final class PhenotypeSeedData {

    private static final String PREF_NAME = "com.google.android.apps.photos.phenotype";
    public static final String SEEDED_MARKER = "_morphe_flags_seeded";
    public static final int CURRENT_PRESET_VERSION = 4;

    private PhenotypeSeedData() {}

    public static void ensureSeeded(Context context) {
        if (context == null) return;
        String pkg = context.getPackageName();
        if (pkg == null || !pkg.contains("photos")) return;

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            int version = prefs.getInt("_morphe_curated_preset_version", 0);
            boolean needsSeeding = !prefs.contains(SEEDED_MARKER) || version < CURRENT_PRESET_VERSION;

            if (needsSeeding) {
                PhotoFlagsRegistry.applyAll26Defaults(prefs);
                prefs.edit().putInt("_morphe_curated_preset_version", CURRENT_PRESET_VERSION).apply();
                Logger.printInfo(() -> "ensureSeeded: successfully initialized curated modern UI and cutout flags (preset v4).");
            }

            GooglePhotosAccountAvatar.ensureOneGoogleFlagsConfigured(context);
            syncActiveAccount(context);
        } catch (Throwable t) {
            Logger.printException(() -> "Error in PhenotypeSeedData.ensureSeeded", t);
        }
    }

    public static void applyDefaultPresetFlags(SharedPreferences prefs) {
        PhotoFlagsRegistry.applyAll26Defaults(prefs);
    }

    public static void syncActiveAccount(Context context) {
        try {
            AccountManager am = AccountManager.get(context);
            Account[] accounts = am.getAccountsByType("com.google");
            if (accounts.length == 0) return;

            String activeEmail = accounts[0].name;
            if (activeEmail == null || activeEmail.isEmpty()) return;

            File dataDir = context.getFilesDir().getParentFile();
            if (dataDir == null || !dataDir.exists()) return;

            File sharedPrefsDir = new File(dataDir, "shared_prefs");
            if (!sharedPrefsDir.exists()) sharedPrefsDir.mkdirs();

            String accountPrefName = "com.google.android.apps.photos.phenotype#" + activeEmail + ".xml";
            File accountPrefFile = new File(sharedPrefsDir, accountPrefName);

            if (!accountPrefFile.exists() || accountPrefFile.length() == 0) {
                String xmlContent = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                        + "<map>\n"
                        + "    <string name=\"__phenotype_server_token\"></string>\n"
                        + "    <string name=\"__phenotype_snapshot_token\"></string>\n"
                        + "    <long name=\"__phenotype_configuration_version\" value=\"1789073700\" />\n"
                        + "</map>\n";

                try (FileOutputStream fos = new FileOutputStream(accountPrefFile)) {
                    fos.write(xmlContent.getBytes(StandardCharsets.UTF_8));
                }
                Logger.printInfo(() -> "Seeded phenotype account file for " + activeEmail);
            }
        } catch (Throwable t) {
            Logger.printException(() -> "Error syncing active account in PhenotypeSeedData", t);
        }
    }
}
