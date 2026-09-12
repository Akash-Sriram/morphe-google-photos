package app.morphe.extension.shared.patches;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import app.morphe.extension.shared.Logger;

/**
 * Automatically restores, downloads, and persists Google Photos machine learning models
 * (Magic Eraser, Portrait Blur, Sky replacement, etc.) and Mobile Data Download (MDD) metadata.
 *
 * Capabilities:
 * 1. Checks local app sandbox (`files/datadownload/shared/public/`).
 * 2. If missing, restores from persistent external media storage (`/storage/emulated/0/Android/media/<pkg>/`).
 * 3. If missing from device entirely (fresh install on non-root S24/etc.), automatically downloads
 *    `photos_models.zip` in the background and sets up both the sandbox and persistent storage.
 */
public final class PhotosModelSeeder {
    private static final Object LOCK = new Object();
    private static volatile boolean isSeeded = false;
    private static volatile boolean isDownloading = false;

    private static final String REMOTE_MODELS_URL =
            "https://github.com/Akash-Sriram/GooglePhotos-Patched/releases/download/v1.0-models/photos_models.zip";

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
                    // Ensure external persistent backup is up-to-date
                    if (persistentDir != null) {
                        ensureBackupExists(persistentDir, targetModelsDir, prefsDir, targetProtodbDir);
                    }
                    return;
                }

                // 1. Try restoring from persistent external media directory
                if (persistentDir != null && persistentDir.exists()) {
                    File srcModelsDir = new File(persistentDir, "models");
                    File srcManifestsDir = new File(persistentDir, "manifests");
                    File srcProtodbDir = new File(persistentDir, "protodb");

                    if (srcModelsDir.exists() && countFilesInDir(srcModelsDir) >= MIN_REQUIRED_MODELS) {
                        Logger.printInfo(() -> "PhotosModelSeeder: Restoring ML models & MDD manifests from " + persistentDir.getAbsolutePath());

                        if (!targetModelsDir.exists()) targetModelsDir.mkdirs();
                        copyDirectoryContents(srcModelsDir, targetModelsDir, true);

                        if (!prefsDir.exists()) prefsDir.mkdirs();
                        copyDirectoryContents(srcManifestsDir, prefsDir, false);

                        if (srcProtodbDir.exists() && srcProtodbDir.isDirectory()) {
                            if (!targetProtodbDir.exists()) targetProtodbDir.mkdirs();
                            copyDirectoryContents(srcProtodbDir, targetProtodbDir, false);
                        }

                        isSeeded = true;
                        Logger.printInfo(() -> "PhotosModelSeeder: Successfully seeded " + countFilesInDir(targetModelsDir) + " ML models!");
                        return;
                    }
                }

                // 2. Models not on device: Trigger background downloader
                startBackgroundDownload(context, targetModelsDir, prefsDir, targetProtodbDir, persistentDir);

            } catch (Throwable t) {
                Logger.printException(() -> "PhotosModelSeeder: Failed to seed models", t);
            }
        }
    }

    private static void startBackgroundDownload(Context context, File targetModelsDir, File prefsDir,
                                                File targetProtodbDir, File persistentDir) {
        if (isDownloading) return;
        isDownloading = true;

        new Thread(() -> {
            Logger.printInfo(() -> "PhotosModelSeeder: Initiating background download of ML model pack...");
            showToast(context, "Google Photos: Downloading Magic Eraser & AI models...");

            try (InputStream is = openStreamWithRedirects(REMOTE_MODELS_URL);
                 ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {

                if (!targetModelsDir.exists()) targetModelsDir.mkdirs();
                if (!prefsDir.exists()) prefsDir.mkdirs();
                if (!targetProtodbDir.exists()) targetProtodbDir.mkdirs();

                File backupModelsDir = persistentDir != null ? new File(persistentDir, "models") : null;
                File backupManifestsDir = persistentDir != null ? new File(persistentDir, "manifests") : null;
                File backupProtodbDir = persistentDir != null ? new File(persistentDir, "protodb") : null;

                if (backupModelsDir != null && !backupModelsDir.exists()) backupModelsDir.mkdirs();
                if (backupManifestsDir != null && !backupManifestsDir.exists()) backupManifestsDir.mkdirs();
                if (backupProtodbDir != null && !backupProtodbDir.exists()) backupProtodbDir.mkdirs();

                byte[] buffer = new byte[16384];
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }

                    String name = entry.getName();
                    File dest = null;
                    File backup = null;
                    boolean executable = false;

                    if (name.startsWith("models/")) {
                        String filename = name.substring("models/".length());
                        dest = new File(targetModelsDir, filename);
                        if (backupModelsDir != null) backup = new File(backupModelsDir, filename);
                        executable = true;
                    } else if (name.startsWith("manifests/")) {
                        String filename = name.substring("manifests/".length());
                        dest = new File(prefsDir, filename);
                        if (backupManifestsDir != null) backup = new File(backupManifestsDir, filename);
                    } else if (name.startsWith("protodb/")) {
                        String filename = name.substring("protodb/".length());
                        dest = new File(targetProtodbDir, filename);
                        if (backupProtodbDir != null) backup = new File(backupProtodbDir, filename);
                    }

                    if (dest != null) {
                        try (FileOutputStream fos = new FileOutputStream(dest)) {
                            int len;
                            while ((len = zis.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                            fos.flush();
                        }
                        dest.setReadable(true, false);
                        dest.setWritable(true, false);
                        if (executable) dest.setExecutable(true, false);

                        if (backup != null) {
                            try {
                                copyFile(dest, backup);
                            } catch (Exception ignored) {}
                        }
                    }
                    zis.closeEntry();
                }

                isSeeded = true;
                Logger.printInfo(() -> "PhotosModelSeeder: Successfully downloaded and seeded " + countFilesInDir(targetModelsDir) + " models!");
                showToast(context, "Google Photos: Magic Eraser & AI models ready!");

            } catch (Throwable t) {
                Logger.printException(() -> "PhotosModelSeeder: Background download failed", t);
            } finally {
                isDownloading = false;
            }
        }, "PhotosModelDownloader").start();
    }

    private static InputStream openStreamWithRedirects(String urlStr) throws IOException {
        int redirects = 0;
        while (redirects < 6) {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            int status = conn.getResponseCode();

            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == 307 || status == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null && !newUrl.isEmpty()) {
                    urlStr = newUrl;
                    redirects++;
                    continue;
                }
            }
            if (status >= 200 && status < 300) {
                return conn.getInputStream();
            }
            throw new IOException("HTTP " + status + " while requesting " + urlStr);
        }
        throw new IOException("Too many redirects: " + urlStr);
    }

    private static void showToast(Context context, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {}
        });
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
