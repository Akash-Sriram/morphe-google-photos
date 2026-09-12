package app.morphe.extension.shared.patches;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import app.morphe.extension.shared.Logger;

/**
 * Automatically restores and persists Google Photos machine learning models (Magic Eraser, Portrait Blur, etc.)
 * and Mobile Data Download (MDD) metadata from an external persistent staging directory
 * (/storage/emulated/0/Android/media/<package>/) into app-internal storage.
 *
 * This guarantees that neural network models survive "Clear Data" and app updates without re-downloading.
 */
public final class PhotosModelSeeder {
    private static final Object LOCK = new Object();
    private static volatile boolean isSeeded = false;

    private static final String MDD_MODELS_REL_PATH = "datadownload/shared/public";
    private static final String PROTODB_REL_PATH = "protodb";
    private static final String SHARED_PREFS_DIR_NAME = "shared_prefs";
    private static final String MDD_GROUPS_XML = "gms_icing_mdd_groups.xml";

    private static final int MIN_REQUIRED_MODELS = 50;

    private PhotosModelSeeder() {}

    public static void ensureSeeded(Context context) {
        if (context == null) return;

        String pkg = context.getPackageName();
        if (pkg == null || !pkg.contains("photos")) {
            return;
        }

        if (isSeeded) {
            return;
        }

        synchronized (LOCK) {
            if (isSeeded) {
                return;
            }

            try {
                File filesDir = context.getFilesDir();
                File dataDir = new File(context.getApplicationInfo().dataDir);
                File prefsDir = new File(dataDir, SHARED_PREFS_DIR_NAME);
                File targetModelsDir = new File(filesDir, MDD_MODELS_REL_PATH);
                File targetProtodbDir = new File(filesDir, PROTODB_REL_PATH);

                boolean hasModels = countFilesInDir(targetModelsDir) >= MIN_REQUIRED_MODELS;
                File groupsXml = new File(prefsDir, MDD_GROUPS_XML);
                boolean hasGroups = groupsXml.exists() && groupsXml.length() > 2000;

                File persistentDir = findPersistentSourceDir(context);

                if (hasModels && hasGroups) {
                    isSeeded = true;
                    // Check if persistent backup needs to be created from current live data
                    if (persistentDir != null) {
                        ensureBackupExists(persistentDir, targetModelsDir, prefsDir, targetProtodbDir);
                    }
                    return;
                }

                if (persistentDir == null || !persistentDir.exists()) {
                    Logger.printDebug(() -> "PhotosModelSeeder: No persistent backup source found in Android/media");
                    return;
                }

                File srcModelsDir = new File(persistentDir, "models");
                File srcManifestsDir = new File(persistentDir, "manifests");
                File srcProtodbDir = new File(persistentDir, "protodb");

                if (!srcModelsDir.exists() || countFilesInDir(srcModelsDir) < MIN_REQUIRED_MODELS) {
                    Logger.printDebug(() -> "PhotosModelSeeder: Persistent models folder incomplete in " + persistentDir.getAbsolutePath());
                    return;
                }

                Logger.printInfo(() -> "PhotosModelSeeder: Restoring ML models & MDD manifests from " + persistentDir.getAbsolutePath());

                // 1. Copy Models
                if (!targetModelsDir.exists()) {
                    targetModelsDir.mkdirs();
                }
                copyDirectoryContents(srcModelsDir, targetModelsDir, true);

                // 2. Copy Manifests to shared_prefs
                if (!prefsDir.exists()) {
                    prefsDir.mkdirs();
                }
                copyDirectoryContents(srcManifestsDir, prefsDir, false);

                // 3. Copy Protodb
                if (srcProtodbDir.exists() && srcProtodbDir.isDirectory()) {
                    if (!targetProtodbDir.exists()) {
                        targetProtodbDir.mkdirs();
                    }
                    copyDirectoryContents(srcProtodbDir, targetProtodbDir, false);
                }

                isSeeded = true;
                Logger.printInfo(() -> "PhotosModelSeeder: Successfully seeded " + countFilesInDir(targetModelsDir) + " ML models & MDD manifests!");

            } catch (Throwable t) {
                Logger.printException(() -> "PhotosModelSeeder: Failed to seed models", t);
            }
        }
    }

    private static File findPersistentSourceDir(Context context) {
        List<File> candidates = new ArrayList<>();

        try {
            File[] mediaDirs = context.getExternalMediaDirs();
            if (mediaDirs != null) {
                for (File dir : mediaDirs) {
                    if (dir != null) {
                        candidates.add(dir);
                    }
                }
            }
        } catch (Throwable ignored) {}

        String pkg = context.getPackageName();
        if (pkg != null) {
            candidates.add(new File("/storage/emulated/0/Android/media/" + pkg));
            candidates.add(new File("/sdcard/Android/media/" + pkg));
        }
        candidates.add(new File("/storage/emulated/0/Android/media/app.morphe.android.apps.photos"));
        candidates.add(new File("/sdcard/Android/media/app.morphe.android.apps.photos"));

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isDirectory()) {
                File models = new File(candidate, "models");
                if (models.exists() && countFilesInDir(models) >= MIN_REQUIRED_MODELS) {
                    return candidate;
                }
            }
        }

        // If no candidate with models was found, return the default external media dir for possible backup
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        return null;
    }

    private static void ensureBackupExists(File persistentDir, File liveModelsDir, File livePrefsDir, File liveProtodbDir) {
        try {
            File backupModelsDir = new File(persistentDir, "models");
            if (countFilesInDir(backupModelsDir) >= MIN_REQUIRED_MODELS) {
                return;
            }

            Logger.printInfo(() -> "PhotosModelSeeder: Backing up live models to persistent storage: " + persistentDir.getAbsolutePath());
            if (!backupModelsDir.exists()) {
                backupModelsDir.mkdirs();
            }
            copyDirectoryContents(liveModelsDir, backupModelsDir, false);

            File backupManifestsDir = new File(persistentDir, "manifests");
            if (!backupManifestsDir.exists()) {
                backupManifestsDir.mkdirs();
            }
            // Copy MDD xml files
            File[] xmlFiles = livePrefsDir.listFiles((dir, name) -> name != null && name.startsWith("gms_icing_mdd"));
            if (xmlFiles != null) {
                for (File f : xmlFiles) {
                    copyFile(f, new File(backupManifestsDir, f.getName()));
                }
            }

            if (liveProtodbDir.exists() && liveProtodbDir.isDirectory()) {
                File backupProtodbDir = new File(persistentDir, "protodb");
                if (!backupProtodbDir.exists()) {
                    backupProtodbDir.mkdirs();
                }
                copyDirectoryContents(liveProtodbDir, backupProtodbDir, false);
            }
        } catch (Throwable t) {
            Logger.printException(() -> "PhotosModelSeeder: Failed to backup live models", t);
        }
    }

    private static int countFilesInDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return 0;
        }
        String[] list = dir.list();
        return list == null ? 0 : list.length;
    }

    private static void copyDirectoryContents(File srcDir, File destDir, boolean executable) {
        File[] files = srcDir.listFiles();
        if (files == null) return;

        for (File srcFile : files) {
            if (srcFile.isFile()) {
                File destFile = new File(destDir, srcFile.getName());
                try {
                    copyFile(srcFile, destFile);
                    destFile.setReadable(true, false);
                    destFile.setWritable(true, false);
                    if (executable) {
                        destFile.setExecutable(true, false);
                    }
                } catch (IOException e) {
                    Logger.printException(() -> "Failed to copy " + srcFile.getName() + " to " + destFile.getAbsolutePath(), e);
                }
            }
        }
    }

    private static void copyFile(File src, File dest) throws IOException {
        if (dest.exists() && dest.length() == src.length()) {
            return;
        }
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest);
             FileChannel inChannel = fis.getChannel();
             FileChannel outChannel = fos.getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }
}
