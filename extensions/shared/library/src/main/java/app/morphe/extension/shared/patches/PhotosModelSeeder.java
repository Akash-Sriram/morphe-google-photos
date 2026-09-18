package app.morphe.extension.shared.patches;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.MorpheModelDownloadDialog;

public final class PhotosModelSeeder {
    private static final Object LOCK = new Object();
    private static volatile boolean isSeeded = false;

    private static final String MDD_MODELS_REL_PATH = "datadownload/shared/public";
    private static final String SHARED_PREFS_DIR_NAME = "shared_prefs";
    private static final String MDD_GROUPS_XML = "gms_icing_mdd_groups.xml";
    private static final String MDD_FILES_XML = "gms_icing_mdd_shared_files.xml";

    private static final Map<String, List<String>> FEATURE_TO_GROUPS = new HashMap<>();
    private static final Map<String, String> FEATURE_DISPLAY_NAMES = new HashMap<>();
    private static final Set<String> activeDownloads = Collections.synchronizedSet(new HashSet<>());
    private static volatile java.lang.ref.WeakReference<Activity> currentActivityRef = null;

    private static void mapFeature(List<String> keys, List<String> groups, String displayName) {
        for (String key : keys) {
            FEATURE_TO_GROUPS.put(key.toUpperCase(Locale.US), groups);
            FEATURE_DISPLAY_NAMES.put(key.toUpperCase(Locale.US), displayName);
        }
    }

    static {
        mapFeature(Arrays.asList("MAGIC_ERASER", "UDON", "C", "T", "ERASE", "V"), Arrays.asList("udon", "buttercup"), "Magic Eraser");
        mapFeature(Arrays.asList("UNBLUR", "W"), Arrays.asList("unblur_v2_gpu", "unblur_v1_cpu"), "Photo Unblur");
        mapFeature(Arrays.asList("PORTRAIT", "PORTRAIT_BLUR", "BLUR", "DEPTH", "I", "O", "PORTRAIT_RELIGHTING", "K", "GROUNDHOG_ONLY"), Arrays.asList("groundhog", "portrait_preprocessed_image", "portrait_segmenter", "preprocessed7_image"), "Portrait Blur / Light");
        mapFeature(Arrays.asList("SKY_PALETTE_TRANSFER", "R"), Arrays.asList("sky_preprocessed3_image"), "Sky Palette");
        mapFeature(Arrays.asList("EEVEE", "M", "MOVE", "U"), Arrays.asList("eevee"), "Magic Editor");
        mapFeature(Arrays.asList("FACE_RETOUCH", "Z"), Arrays.asList("face_retouch"), "Portrait Retouch");
        mapFeature(Arrays.asList("NINJASK", "Q"), Arrays.asList("ninjask"), "Video Unblur");
        mapFeature(Arrays.asList("HDRNET"), Arrays.asList("landscape_preprocessed2_image"), "HDR Enhance");
        mapFeature(Arrays.asList("SPOTLIGHT"), Arrays.asList("spotlight"), "Spotlight");
        mapFeature(Arrays.asList("CUBELUT", "AE"), Arrays.asList("cubelut"), "Color Presets");
        mapFeature(Arrays.asList("GRAINY_FILM", "AB"), Arrays.asList("grainy_film"), "Film Grain");
        mapFeature(Arrays.asList("LIGHT_LEAK", "AF"), Arrays.asList("light_leak"), "Light Leak");
        mapFeature(Arrays.asList("TONEFIX", "AA"), Arrays.asList("psyduck_gpu", "mochi_cpu", "tonefix"), "Tone Fix");
    }

    public static Activity getCurrentActivity() {
        if (currentActivityRef != null) {
            Activity a = currentActivityRef.get();
            if (a != null && !a.isFinishing() && !a.isDestroyed()) {
                return a;
            }
        }
        return Utils.getActivity();
    }

    private PhotosModelSeeder() {}

    public static void ensureSeeded(Context context) {
        if (context == null) return;
        String pkg = context.getPackageName();
        if (pkg == null || !pkg.contains("photos")) return;

        if (context instanceof Application) {
            ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityResumed(Activity activity) { currentActivityRef = new java.lang.ref.WeakReference<>(activity); }
                @Override public void onActivityCreated(Activity a, Bundle b) { currentActivityRef = new java.lang.ref.WeakReference<>(a); }
                @Override public void onActivityStarted(Activity a) { currentActivityRef = new java.lang.ref.WeakReference<>(a); }
                @Override public void onActivityPaused(Activity a) {}
                @Override public void onActivityStopped(Activity a) {}
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                @Override public void onActivityDestroyed(Activity a) {}
            });
        }
        if (isSeeded) return;

        synchronized (LOCK) {
            if (isSeeded) return;

            try {
                File filesDir = context.getFilesDir();
                File dataDir = new File(context.getApplicationInfo().dataDir);
                File prefsDir = new File(dataDir, SHARED_PREFS_DIR_NAME);
                File targetModelsDir = new File(filesDir, MDD_MODELS_REL_PATH);

                File groupsXml = new File(prefsDir, MDD_GROUPS_XML);
                boolean hasGroups = groupsXml.exists() && groupsXml.length() > 25000;

                // Inject manifest registry into shared_prefs so Google Photos knows group definitions
                if (!hasGroups) {
                    Logger.printInfo(() -> "PhotosModelSeeder: Injecting MDD manifests into shared_prefs");
                    unlockDirectory(prefsDir);
                    injectManifests(prefsDir, pkg);
                    patchMddManifests(prefsDir, pkg);
                }

                isSeeded = true;
                Logger.printInfo(() -> "PhotosModelSeeder: Startup manifest initialization complete.");

            } catch (Throwable t) {
                Logger.printInfo(() -> "PhotosModelSeeder: Failed in ensureSeeded: " + t.getMessage());
            }
        }
    }

    public static boolean isModelReady(Object modelEnumObj) {
        if (modelEnumObj == null) return true;
        String name = getEnumName(modelEnumObj);
        if (name == null) return true;
        String upperName = name.toUpperCase(Locale.US);

        List<String> groups = FEATURE_TO_GROUPS.get(upperName);
        if (groups == null || groups.isEmpty()) {
            android.util.Log.i("morphe: PhotosModelSeeder", "isModelReady: unmapped feature " + name + ", defaulting to true");
            return true;
        }

        Context context = Utils.getContext();
        if (context == null) return true;

        File targetModelsDir = new File(context.getFilesDir(), MDD_MODELS_REL_PATH);
        if (!targetModelsDir.exists()) {
            android.util.Log.i("morphe: PhotosModelSeeder", "isModelReady: " + name + " -> false (models dir does not exist)");
            return false;
        }

        Map<String, List<ModelEntry>> groupMap = parseAllGroupsFromManifests();
        for (String group : groups) {
            List<ModelEntry> entries = groupMap.get(group);
            if (entries == null || entries.isEmpty()) continue;
            for (ModelEntry entry : entries) {
                File dest = new File(targetModelsDir, entry.filename);
                if (!dest.exists() || dest.length() == 0) {
                    android.util.Log.i("morphe: PhotosModelSeeder", "isModelReady: " + name + " -> false (missing " + entry.filename + ")");
                    return false;
                }
            }
        }
        android.util.Log.i("morphe: PhotosModelSeeder", "isModelReady: " + name + " -> true (all files present)");
        return true;
    }

    public static boolean isModelDownloading(Object modelEnumObj) {
        if (modelEnumObj == null) return false;
        String name = getEnumName(modelEnumObj);
        if (name == null) return false;
        return activeDownloads.contains(name.toUpperCase(Locale.US));
    }

    public static void triggerOnDemandDownload(Object modelEnumObj) {
        if (modelEnumObj == null) return;
        String rawName = getEnumName(modelEnumObj);
        if (rawName == null) return;
        final String featureName = rawName.toUpperCase(Locale.US);

        android.util.Log.i("morphe: PhotosModelSeeder", "triggerOnDemandDownload triggered for: " + featureName);

        final List<String> groups = FEATURE_TO_GROUPS.get(featureName);
        if (groups == null || groups.isEmpty()) {
            android.util.Log.i("morphe: PhotosModelSeeder", "No ML models mapped for feature " + featureName);
            return;
        }

        if (isModelReady(modelEnumObj)) {
            android.util.Log.i("morphe: PhotosModelSeeder", "Feature " + featureName + " models are already present on disk.");
            return;
        }

        if (activeDownloads.contains(featureName)) {
            android.util.Log.i("morphe: PhotosModelSeeder", "Download for " + featureName + " is already running.");
            return;
        }

        final String displayName = FEATURE_DISPLAY_NAMES.containsKey(featureName)
                ? FEATURE_DISPLAY_NAMES.get(featureName)
                : featureName;

        downloadFeatureWithDialog(featureName, displayName, groups);
    }

    public static void downloadFeatureWithDialog(String featureName, String displayName, List<String> groups) {
        activeDownloads.add(featureName);

        new Handler(Looper.getMainLooper()).post(() -> {
            Activity activity = getCurrentActivity();
            android.util.Log.i("morphe: PhotosModelSeeder", "downloadFeatureWithDialog: showing dialog for " + displayName + " on activity " + activity);
            final MorpheModelDownloadDialog[] dialogHolder = new MorpheModelDownloadDialog[1];

            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                dialogHolder[0] = new MorpheModelDownloadDialog(activity, displayName, () -> {
                    android.util.Log.i("morphe: PhotosModelSeeder", "Download cancelled by user for " + featureName);
                    activeDownloads.remove(featureName);
                });
                dialogHolder[0].show();
            } else {
                showToast(Utils.getContext(), "Downloading " + displayName + " AI models...");
            }

            new Thread(() -> {
                Context context = Utils.getContext();
                if (context == null) {
                    activeDownloads.remove(featureName);
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                    return;
                }

                try {
                    File filesDir = context.getFilesDir();
                    File targetModelsDir = new File(filesDir, MDD_MODELS_REL_PATH);
                    if (!targetModelsDir.exists()) targetModelsDir.mkdirs();

                    unlockDirectory(targetModelsDir);

                    Map<String, List<ModelEntry>> groupMap = parseAllGroupsFromManifests();
                    List<ModelEntry> toDownload = new ArrayList<>();

                    for (String grp : groups) {
                        List<ModelEntry> entries = groupMap.get(grp);
                        if (entries != null) {
                            for (ModelEntry entry : entries) {
                                File dest = new File(targetModelsDir, entry.filename);
                                if (!dest.exists() || dest.length() == 0) {
                                    toDownload.add(entry);
                                }
                            }
                        }
                    }

                    if (toDownload.isEmpty()) {
                        Logger.printInfo(() -> "PhotosModelSeeder: All files for " + featureName + " already present.");
                        activeDownloads.remove(featureName);
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        return;
                    }

                    android.util.Log.i("morphe: PhotosModelSeeder", "Downloading/restoring " + toDownload.size() + " models on-demand for " + featureName);

                    // Compute total expected bytes
                    long totalBytes = 0;
                    for (ModelEntry entry : toDownload) {
                        long size = getRemoteFileSize(entry.url);
                        if (size > 0) totalBytes += size;
                    }
                    if (totalBytes <= 0) totalBytes = 15 * 1024 * 1024;

                    long downloadedSoFar = 0;
                    boolean success = true;

                    for (ModelEntry entry : toDownload) {
                        if (dialogHolder[0] != null && dialogHolder[0].isCancelled()) {
                            success = false;
                            break;
                        }

                        File dest = new File(targetModelsDir, entry.filename);
                        final long baseDownloaded = downloadedSoFar;
                        final long finalTotal = totalBytes;
                        final String fileName = entry.filename;

                        DownloadProgressListener listener = new DownloadProgressListener() {
                            @Override
                            public void onProgress(long fileDownloaded, long currentFileTotal) {
                                long currentTotalDownloaded = baseDownloaded + fileDownloaded;
                                int percent = finalTotal > 0 ? (int) Math.min(99, (currentTotalDownloaded * 100) / finalTotal) : 0;
                                if (dialogHolder[0] != null) {
                                    dialogHolder[0].updateProgress(percent, currentTotalDownloaded, finalTotal, "Downloading " + displayName + "...");
                                }
                            }

                            @Override
                            public boolean isCancelled() {
                                return dialogHolder[0] != null && dialogHolder[0].isCancelled();
                            }
                        };

                        android.util.Log.i("morphe: PhotosModelSeeder", "Downloading " + entry.filename + " from CDN " + entry.url);
                        boolean fileObtained = downloadFile(entry.url, dest, listener);

                        if (fileObtained) {
                            dest.setReadable(true, false);
                            dest.setWritable(true, false);
                            dest.setExecutable(true, false);
                            downloadedSoFar += dest.length();
                        } else {
                            android.util.Log.e("morphe: PhotosModelSeeder", "Failed to obtain model file " + entry.filename);
                            success = false;
                            break;
                        }
                    }

                    if (success) {
                        lockModels(targetModelsDir);
                        Logger.printInfo(() -> "PhotosModelSeeder: Successfully downloaded all models for " + featureName);
                        if (dialogHolder[0] != null) {
                            dialogHolder[0].setComplete(displayName + " ready!");
                            try { Thread.sleep(800); } catch (Exception ignored) {}
                            dialogHolder[0].dismiss();
                        }
                        showToast(context, displayName + " models downloaded!");
                    } else {
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        if (dialogHolder[0] == null || !dialogHolder[0].isCancelled()) {
                            showToast(context, "Download failed for " + displayName + ".");
                        }
                    }

                } catch (Throwable t) {
                    Logger.printInfo(() -> "PhotosModelSeeder: Error in on-demand download for " + featureName + ": " + t.getMessage());
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                } finally {
                    activeDownloads.remove(featureName);
                }
            }, "PhotosModelFeatureDownloader_" + featureName).start();
        });
    }

    public static class ModelEntry {
        public final String url;
        public final String filename;

        public ModelEntry(String url, String filename) {
            this.url = url;
            this.filename = filename;
        }
    }

    public interface DownloadProgressListener {
        void onProgress(long bytesRead, long totalBytes);
        boolean isCancelled();
    }

    private static String getEnumName(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Enum<?>) {
            return ((Enum<?>) obj).name();
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        try {
            Method m = obj.getClass().getMethod("name");
            return (String) m.invoke(obj);
        } catch (Throwable ignored) {
            return obj.toString();
        }
    }

    private static long getRemoteFileSize(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Android/14; GooglePhotos");
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    conn.disconnect();
                    conn = (HttpURLConnection) new URL(loc).openConnection();
                    conn.setRequestMethod("HEAD");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestProperty("User-Agent", "Android/14; GooglePhotos");
                    code = conn.getResponseCode();
                }
            }
            if (code >= 200 && code < 300) {
                return conn.getContentLengthLong();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public static Map<String, String> parseShaToFileMapping() {
        Map<String, String> shaToFile = new HashMap<>();
        String sharedFilesXml = MddManifests.MANIFESTS.get(MDD_FILES_XML);
        if (sharedFilesXml == null) return shaToFile;

        Pattern pattern = Pattern.compile("<string name=\"([0-9a-f]{40})\\|0\">([^<]+)</string>");
        Matcher matcher = pattern.matcher(sharedFilesXml);
        Pattern filePattern = Pattern.compile("datadownloadfile_\\d+");

        while (matcher.find()) {
            String sha = matcher.group(1);
            String base64Val = matcher.group(2).trim();
            try {
                byte[] decoded = Base64.decode(base64Val, Base64.DEFAULT);
                String decodedStr = new String(decoded, "ISO-8859-1");
                Matcher fm = filePattern.matcher(decodedStr);
                if (fm.find()) {
                    shaToFile.put(sha, fm.group());
                }
            } catch (Exception ignored) {}
        }
        return shaToFile;
    }

    public static Map<String, String> parseUrlToFileMapping() {
        Map<String, String> urlToFile = new LinkedHashMap<>();
        Map<String, List<ModelEntry>> groupMap = parseAllGroupsFromManifests();
        for (List<ModelEntry> entries : groupMap.values()) {
            for (ModelEntry entry : entries) {
                urlToFile.put(entry.url, entry.filename);
            }
        }
        return urlToFile;
    }

    public static Map<String, List<ModelEntry>> parseAllGroupsFromManifests() {
        Map<String, List<ModelEntry>> groupMap = new LinkedHashMap<>();
        Map<String, String> shaToFile = parseShaToFileMapping();
        String groupsXml = MddManifests.MANIFESTS.get(MDD_GROUPS_XML);
        if (groupsXml == null || shaToFile.isEmpty()) return groupMap;

        Pattern entryPattern = Pattern.compile("<string name=\"([^\"]+)\">([^<]+)</string>");
        Matcher entryMatcher = entryPattern.matcher(groupsXml);
        Pattern urlPattern = Pattern.compile("https?://[^\\s\\u0000-\\u001f\"<>()]+");
        Pattern shaPattern = Pattern.compile("[0-9a-f]{40}");

        while (entryMatcher.find()) {
            String keyB64 = entryMatcher.group(1).trim();
            String valB64 = entryMatcher.group(2).trim();

            String groupName = "unknown";
            try {
                byte[] rawKey = Base64.decode(keyB64, Base64.DEFAULT);
                if (rawKey.length > 2 && rawKey[0] == 0x0a) {
                    int len = rawKey[1] & 0xff;
                    if (rawKey.length >= 2 + len) {
                        groupName = new String(rawKey, 2, len, "ISO-8859-1");
                    }
                }
                if ("unknown".equals(groupName)) {
                    String decKey = new String(rawKey, "ISO-8859-1");
                    Matcher m = Pattern.compile("^[\\u0000-\\u001f]*([a-zA-Z0-9_\\-]+)").matcher(decKey);
                    if (m.find()) groupName = m.group(1);
                }
            } catch (Exception ignored) {}

            try {
                byte[] rawVal = Base64.decode(valB64, Base64.DEFAULT);
                String decVal = new String(rawVal, "ISO-8859-1");

                Matcher um = urlPattern.matcher(decVal);
                List<Integer> urlIndices = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                while (um.find()) {
                    urls.add(um.group());
                    urlIndices.add(um.start());
                }

                List<ModelEntry> entries = new ArrayList<>();
                for (int i = 0; i < urls.size(); i++) {
                    String url = urls.get(i);
                    int start = urlIndices.get(i) + url.length();
                    int end = (i + 1 < urlIndices.size()) ? urlIndices.get(i + 1) : decVal.length();
                    String sub = decVal.substring(start, end);

                    Matcher sm = shaPattern.matcher(sub);
                    if (sm.find()) {
                        String sha = sm.group();
                        String filename = shaToFile.get(sha);
                        if (filename != null) {
                            entries.add(new ModelEntry(url, filename));
                        }
                    }
                }

                if (!entries.isEmpty()) {
                    groupMap.put(groupName, entries);
                }
            } catch (Exception ignored) {}
        }
        return groupMap;
    }

    private static void unlockDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File parent = dir;
        while (parent != null && parent.getAbsolutePath().contains("datadownload")) {
            parent.setExecutable(true, false);
            parent = parent.getParentFile();
        }
        dir.setWritable(true, false);
        File[] children = dir.listFiles();
        if (children != null) {
            for (File f : children) f.setWritable(true, false);
        }
    }

    private static void lockModels(File targetModelsDir) {
        if (targetModelsDir == null || !targetModelsDir.exists()) return;
        File parent = targetModelsDir;
        while (parent != null && parent.getAbsolutePath().contains("datadownload")) {
            parent.setReadable(true, false);
            parent.setWritable(true, false);
            parent.setExecutable(true, false);
            parent = parent.getParentFile();
        }
        targetModelsDir.setReadable(true, false);
        targetModelsDir.setWritable(true, false);
        targetModelsDir.setExecutable(true, false);
        File[] files = targetModelsDir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.setReadable(true, false);
                f.setWritable(true, false);
                f.setExecutable(true, false);
            }
        }
    }

    private static void injectManifests(File destDir, String newPackageName) {
        if (!destDir.exists()) destDir.mkdirs();
        for (Map.Entry<String, String> entry : MddManifests.MANIFESTS.entrySet()) {
            String filename = entry.getKey();
            String content = entry.getValue();
            File xml = new File(destDir, filename);
            try (FileOutputStream fos = new FileOutputStream(xml)) {
                fos.write(content.getBytes("UTF-8"));
            } catch (Exception e) {
                Logger.printInfo(() -> "Failed to write manifest " + filename + ": " + e.getMessage());
            }
        }
        patchMddManifests(destDir, newPackageName);
    }

    private static boolean downloadFile(String urlStr, File dest, DownloadProgressListener listener) {
        for (int i = 0; i < 3; i++) {
            if (listener != null && listener.isCancelled()) return false;
            try {
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "Android/14; GooglePhotos");

                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    String loc = conn.getHeaderField("Location");
                    if (loc != null) {
                        conn.disconnect();
                        url = new URL(loc);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(60000);
                        conn.setRequestProperty("User-Agent", "Android/14; GooglePhotos");
                        status = conn.getResponseCode();
                    }
                }

                if (status >= 200 && status < 300) {
                    long expectedSize = conn.getContentLength();
                    long downloadedSize = 0;
                    try (InputStream is = new BufferedInputStream(conn.getInputStream());
                         FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buffer = new byte[32768];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            if (listener != null && listener.isCancelled()) {
                                fos.close();
                                dest.delete();
                                return false;
                            }
                            fos.write(buffer, 0, len);
                            downloadedSize += len;
                            if (listener != null) {
                                listener.onProgress(downloadedSize, expectedSize);
                            }
                        }
                        fos.flush();
                    }

                    if (expectedSize > 0 && downloadedSize != expectedSize) {
                        Logger.printInfo(() -> "PhotosModelSeeder: Download truncated for " + urlStr + ". Retrying...");
                        dest.delete();
                        Thread.sleep(1500);
                        continue;
                    }

                    dest.setReadable(true, false);
                    dest.setWritable(true, false);
                    dest.setExecutable(true, false);
                    return true;
                } else {
                    final int finalStatus = status;
                    Logger.printInfo(() -> "PhotosModelSeeder: Download HTTP Error " + finalStatus + " for " + urlStr);
                }
            } catch (Exception e) {
                final int attempt = i + 1;
                Logger.printInfo(() -> "PhotosModelSeeder: Download error for " + urlStr + " (attempt " + attempt + "/3): " + e.getMessage());
                dest.delete();
                try { Thread.sleep(1500); } catch (Exception ignored) {}
            }
        }
        if (dest.exists()) dest.delete();
        return false;
    }

    private static void patchMddManifests(File manifestsDir, String newPackageName) {
        File[] xmlFiles = manifestsDir.listFiles();
        if (xmlFiles == null) return;
        String oldPackage = "com.google.android.apps.photos";
        if (oldPackage.length() != newPackageName.length()) return;

        for (File xml : xmlFiles) {
            if (!xml.getName().endsWith(".xml")) continue;
            try {
                String content = readFileToString(xml);
                boolean changed = false;
                Matcher m = Pattern.compile(">([^<]+)</string>").matcher(content);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    String base64 = m.group(1);
                    try {
                        byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
                        String decodedStr = new String(decoded, "ISO-8859-1");
                        if (decodedStr.contains(oldPackage)) {
                            decodedStr = decodedStr.replace(oldPackage, newPackageName);
                            String newBase64 = Base64.encodeToString(decodedStr.getBytes("ISO-8859-1"), Base64.NO_WRAP);
                            m.appendReplacement(sb, ">" + newBase64 + "</string>");
                            changed = true;
                            continue;
                        }
                    } catch (Exception ignored) {}
                    m.appendReplacement(sb, ">" + base64 + "</string>");
                }
                m.appendTail(sb);

                if (changed) {
                    try (FileOutputStream fos = new FileOutputStream(xml)) {
                        fos.write(sb.toString().getBytes("UTF-8"));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static File findPersistentSourceDir(Context context) {
        List<File> candidates = new ArrayList<>();
        try {
            File[] mediaDirs = context.getExternalMediaDirs();
            if (mediaDirs != null) {
                for (File dir : mediaDirs) {
                    if (dir != null) candidates.add(dir);
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

        candidates.add(new File("/storage/emulated/0/Download/morphe-models"));
        candidates.add(new File("/sdcard/Download/morphe-models"));

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isDirectory()) {
                File models = new File(candidate, "models");
                if (models.exists() && countModelsInDir(models) > 0) {
                    return candidate;
                }
                if (countModelsInDir(candidate) > 0) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static File findModelFileInSources(Context context, String filename) {
        if (context == null || filename == null) return null;
        List<File> searchDirs = new ArrayList<>();
        try {
            File[] mediaDirs = context.getExternalMediaDirs();
            if (mediaDirs != null) {
                for (File d : mediaDirs) {
                    if (d != null) {
                        searchDirs.add(new File(d, "models"));
                        searchDirs.add(d);
                    }
                }
            }
        } catch (Throwable ignored) {}

        String pkg = context.getPackageName();
        if (pkg != null) {
            searchDirs.add(new File("/storage/emulated/0/Android/media/" + pkg + "/models"));
            searchDirs.add(new File("/sdcard/Android/media/" + pkg + "/models"));
            searchDirs.add(new File("/storage/emulated/0/Android/media/" + pkg));
            searchDirs.add(new File("/sdcard/Android/media/" + pkg));
        }
        searchDirs.add(new File("/storage/emulated/0/Android/media/app.morphe.android.apps.photos/models"));
        searchDirs.add(new File("/sdcard/Android/media/app.morphe.android.apps.photos/models"));
        searchDirs.add(new File("/storage/emulated/0/Download/morphe-models"));
        searchDirs.add(new File("/sdcard/Download/morphe-models"));

        for (File dir : searchDirs) {
            if (dir.exists() && dir.isDirectory()) {
                File f = new File(dir, filename);
                if (f.exists() && f.length() > 0) return f;
            }
        }
        return null;
    }

    private static boolean copyFileWithProgress(File src, File dest, DownloadProgressListener listener) {
        long total = src.length();
        long copied = 0;
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (listener != null && listener.isCancelled()) {
                    out.close();
                    dest.delete();
                    return false;
                }
                out.write(buffer, 0, read);
                copied += read;
                if (listener != null) {
                    listener.onProgress(read, total);
                }
                try { Thread.sleep(4); } catch (Exception ignored) {}
            }
            out.flush();
            return true;
        } catch (Exception e) {
            android.util.Log.e("morphe: PhotosModelSeeder", "copyFileWithProgress error: " + e.getMessage());
            return false;
        }
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
                    if (executable) destFile.setExecutable(true, false);
                } catch (IOException ignored) {}
            }
        }
    }

    private static void copyFile(File src, File dest) throws IOException {
        if (dest.exists() && dest.length() == src.length()) return;
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest);
             FileChannel inChannel = fis.getChannel();
             FileChannel outChannel = fos.getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }

    private static String readFileToString(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return new String(data, "UTF-8");
        }
    }

    private static void showToast(Context context, String msg) {
        if (context == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {}
        });
    }

    private static int countModelsInDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles((d, name) -> name != null && name.startsWith("datadownloadfile_"));
        return files == null ? 0 : files.length;
    }
}
