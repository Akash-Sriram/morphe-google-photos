package app.morphe.extension.shared.patches;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Intercepts saving memory collages and creations in Google Photos.
 * Instead of committing the creation solely to Google's cloud server (which debits account storage quota),
 * this downloader exports the media stream directly to the device's DCIM/Google Photos folder.
 * Google Photos then detects the local file and backs it up under the Pixel XL quota-free exemption.
 *
 * It also hooks into SaveCreationMixin->e(bwel) to check if the creation has already been saved
 * locally. While the file exists in DCIM/Google Photos, the Save button is suppressed/hidden (matching
 * official behavior). If the user deletes the local file, the Save button reappears.
 */
public class LocalCreationDownloader {
    private static final String TAG = "LocalCreationDownloader";
    private static final String PREFS_NAME = "morphe_saved_creations";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static volatile Context sAppContext = null;

    /**
     * Interception entry point called directly from SaveCreationMixin (Lakxr->h).
     *
     * @param saveCreationMixin The SaveCreationMixin instance (this)
     * @param mediaList         The collection of media items (Collection<_1846>) to save
     * @return true if intercepted and handled locally; false to allow Google Photos standard cloud save
     */
    public static boolean onSaveRequested(Object saveCreationMixin, Object mediaList) {
        if (saveCreationMixin == null || mediaList == null) {
            return false;
        }

        try {
            Context context = extractContext(saveCreationMixin);
            if (context == null) {
                context = getApplicationContext();
            }
            if (context != null) {
                sAppContext = context.getApplicationContext();
            }

            Collection<?> items = (mediaList instanceof Collection)
                    ? (Collection<?>) mediaList
                    : null;
            if (items == null || items.isEmpty()) {
                return false;
            }

            // Check if all items in the request are already saved locally
            boolean allAlreadySaved = true;
            for (Object media : items) {
                if (media != null && !isCreationSaved(media)) {
                    allAlreadySaved = false;
                    break;
                }
            }

            if (allAlreadySaved) {
                Log.i(TAG, "Creation is already saved locally on device. Suppressing duplicate download.");
                notifySaveListeners(saveCreationMixin, mediaList);
                notifyStoryUi(saveCreationMixin);
                return true;
            }

            final Context appContext = sAppContext != null ? sAppContext : context.getApplicationContext();
            Log.i(TAG, "Intercepted creation save request for " + items.size() + " item(s). Redirecting to DCIM/Google Photos.");

            // Dispatch background save to avoid blocking the main UI thread
            EXECUTOR.execute(() -> {
                int successCount = 0;
                for (Object media : items) {
                    if (media == null) continue;
                    if (saveSingleItem(appContext, media)) {
                        successCount++;
                    }
                }

                final int saved = successCount;
                MAIN_HANDLER.post(() -> {
                    if (saved > 0) {
                        Log.i(TAG, "Successfully exported " + saved + " creation(s) to DCIM/Google Photos.");

                        // Notify save listeners AFTER download finishes so the UI updates
                        // button state to Saved and advances/dismisses story
                        notifySaveListeners(saveCreationMixin, mediaList);
                        notifyStoryUi(saveCreationMixin);
                    } else {
                        Log.w(TAG, "Failed to resolve local stream for creation item(s).");
                    }
                });
            });

            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error during onSaveRequested interception", t);
            return false;
        }
    }

    /**
     * Called directly from SaveCreationMixin->e(bwel) to check if an item is already saved.
     * When this returns true, the story button provider (Lakxp->c) returns null, causing
     * the Save button to vanish from the UI.
     * If the user deletes the local file from DCIM/Google Photos, this returns false, causing
     * the Save button to reappear.
     */
    public static boolean isCreationSaved(Object mediaItem) {
        if (mediaItem == null) return false;
        try {
            String key = extractItemKey(mediaItem);
            if (key == null) return false;

            // 1. Check deterministic file path in DCIM/Google Photos
            File dir = getGooglePhotosDir();
            if (dir.exists()) {
                File directFile = new File(dir, getFileNameForKey(key));
                if (directFile.exists() && directFile.length() > 0) {
                    return true;
                }
            }

            // 2. Check SharedPreferences for mapped file path
            Context ctx = getApplicationContext();
            if (ctx != null) {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String savedPath = prefs.getString(key, null);
                if (savedPath != null) {
                    File f = new File(savedPath);
                    if (f.exists() && f.length() > 0) {
                        return true;
                    } else {
                        // User deleted the local file! Remove from registry so button reappears
                        prefs.edit().remove(key).apply();
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error in isCreationSaved", t);
        }
        return false;
    }

    public static File getGooglePhotosDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Google Photos");
    }

    public static String getFileNameForKey(String key) {
        return "Collage_" + sanitizeFileName(key) + ".jpg";
    }

    private static String sanitizeFileName(String input) {
        if (input == null || input.isEmpty()) return "item";
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Extracts a stable identifier for a media item (_1846 / bwel).
     */
    public static String extractItemKey(Object mediaItem) {
        if (mediaItem == null) return null;

        // 1. Try bwep.e() returning long ID
        try {
            Method eMethod = mediaItem.getClass().getMethod("e");
            if (eMethod.getReturnType() == long.class || eMethod.getReturnType() == Long.class) {
                long id = ((Number) eMethod.invoke(mediaItem)).longValue();
                if (id != 0 && id != -1) {
                    return "id_" + id;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Check 0-arg methods returning String
        for (Method m : mediaItem.getClass().getMethods()) {
            if (m.getParameterTypes().length == 0 && m.getReturnType() == String.class) {
                String name = m.getName().toLowerCase(Locale.US);
                if (name.contains("key") || name.contains("dedup") || name.equals("i")) {
                    try {
                        String val = (String) m.invoke(mediaItem);
                        if (val != null && val.length() > 5 && !val.contains("com.google") && !val.contains("@")) {
                            return val;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        // 3. Check fields
        for (Field f : mediaItem.getClass().getDeclaredFields()) {
            if (f.getType() == String.class) {
                String name = f.getName().toLowerCase(Locale.US);
                if (name.contains("key") || name.contains("dedup")) {
                    try {
                        f.setAccessible(true);
                        String val = (String) f.get(mediaItem);
                        if (val != null && val.length() > 5 && !val.contains("com.google") && !val.contains("@")) {
                            return val;
                        }
                    } catch (Throwable ignored) {}
                }
            } else if (f.getType() == long.class) {
                try {
                    f.setAccessible(true);
                    long val = f.getLong(mediaItem);
                    if (val > 0) return "id_" + val;
                } catch (Throwable ignored) {}
            }
        }

        // 4. Fallback to hash code
        return "item_" + Math.abs(mediaItem.toString().hashCode());
    }

    private static String extractKeyFromUri(Uri uri) {
        if (uri == null) return null;
        String path = uri.getPath();
        if (path == null) return null;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            String seg = path.substring(lastSlash + 1);
            int eq = seg.indexOf('=');
            if (eq > 0) seg = seg.substring(0, eq);
            if (seg.length() > 10) return seg;
        }
        return null;
    }

    private static boolean saveSingleItem(Context context, Object mediaItem) {
        Uri mediaUri = resolveMediaUri(context, mediaItem);
        if (mediaUri == null) {
            Log.w(TAG, "Could not resolve URI for media item: " + mediaItem);
            return false;
        }

        Log.d(TAG, "Resolved creation URI: " + mediaUri);
        ContentResolver resolver = context.getContentResolver();
        String mimeType = resolver.getType(mediaUri);
        if (mimeType == null) {
            mimeType = "image/jpeg";
        }

        boolean isVideo = mimeType.startsWith("video/");
        String ext = isVideo ? ".mp4" : ".jpg";

        String itemKey = extractItemKey(mediaItem);
        String uriKey = extractKeyFromUri(mediaUri);
        String stableKey = (uriKey != null) ? uriKey : itemKey;

        // Use deterministic filename: Collage_<key>.jpg or Highlight_<key>.mp4
        String fileName = (isVideo ? "Highlight_" : "Collage_") + sanitizeFileName(stableKey) + ext;
        File targetFile = new File(getGooglePhotosDir(), fileName);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Google Photos");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri targetUri = isVideo
                        ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

                Uri inserted = resolver.insert(targetUri, values);
                if (inserted == null) {
                    Log.e(TAG, "Failed to create MediaStore entry for " + fileName);
                    return false;
                }

                try (InputStream in = openMediaStream(resolver, mediaUri);
                     OutputStream out = resolver.openOutputStream(inserted)) {
                    if (in == null || out == null) {
                        return false;
                    }
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }

                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(inserted, values, null, null);

                // Index with MediaScanner so Google Photos sees it immediately
                MediaScannerConnection.scanFile(context,
                        new String[]{targetFile.getAbsolutePath()},
                        new String[]{mimeType},
                        null);

                recordSavedItem(context, itemKey, uriKey, targetFile.getAbsolutePath());
                return true;
            } else {
                File dcimDir = getGooglePhotosDir();
                if (!dcimDir.exists() && !dcimDir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: " + dcimDir.getAbsolutePath());
                    return false;
                }

                try (InputStream in = openMediaStream(resolver, mediaUri);
                     OutputStream out = new FileOutputStream(targetFile)) {
                    if (in == null) return false;
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }

                MediaScannerConnection.scanFile(context,
                        new String[]{targetFile.getAbsolutePath()},
                        new String[]{mimeType},
                        null);

                recordSavedItem(context, itemKey, uriKey, targetFile.getAbsolutePath());
                return true;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error streaming creation to local storage", t);
            return false;
        }
    }

    private static void recordSavedItem(Context context, String itemKey, String uriKey, String filePath) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            if (itemKey != null) edit.putString(itemKey, filePath);
            if (uriKey != null) edit.putString(uriKey, filePath);
            edit.apply();
        } catch (Throwable ignored) {}
    }

    private static InputStream openMediaStream(ContentResolver resolver, Uri uri) throws Exception {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String urlStr = uri.toString();

            // Force original quality for Fife (googleusercontent) URLs.
            if (urlStr.contains("googleusercontent.com") || urlStr.contains("lh3.google")) {
                int eqPos = urlStr.lastIndexOf('=');
                int slashAfterHost = urlStr.indexOf('/', urlStr.indexOf("://") + 3);
                if (eqPos > 0 && eqPos > slashAfterHost) {
                    String suffix = urlStr.substring(eqPos);
                    if (!suffix.contains("&") && !suffix.contains("?")) {
                        urlStr = urlStr.substring(0, eqPos) + "=d";
                    } else {
                        urlStr = urlStr.replaceAll("=s\\d+", "=d").replaceAll("=w\\d+-h\\d+", "=d");
                    }
                } else {
                    urlStr = urlStr + "=d";
                }
                Log.d(TAG, "Upgraded Fife URL to original quality: " + urlStr);
            }

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                Log.w(TAG, "HTTP " + responseCode + " for URL: " + urlStr + " — falling back to original URI");
                conn.disconnect();
                conn = (java.net.HttpURLConnection) new java.net.URL(uri.toString()).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setInstanceFollowRedirects(true);
            }
            return conn.getInputStream();
        } else {
            return resolver.openInputStream(uri);
        }
    }

    private static Uri resolveMediaUri(Context context, Object mediaItem) {
        // Attempt 1: Photos DI Binder (bzeq / ahug) with MediaUriProvider (wiy)
        String[] binderClasses = {"bzeq", "ahug"};
        String[] providerClasses = {"wiy"};

        for (String binderName : binderClasses) {
            try {
                Class<?> binderCls = Class.forName(binderName);
                for (String provName : providerClasses) {
                    try {
                        Class<?> provCls = Class.forName(provName);
                        Object provider = null;

                        // Try static method e(Context, Class) on binder
                        try {
                            Method eMethod = binderCls.getMethod("e", Context.class, Class.class);
                            provider = eMethod.invoke(null, context, provCls);
                        } catch (Throwable ignored) {}

                        // Try static method i(Context, Class) on binder
                        if (provider == null) {
                            try {
                                Method iMethod = binderCls.getMethod("i", Context.class, Class.class);
                                provider = iMethod.invoke(null, context, provCls);
                            } catch (Throwable ignored) {}
                        }

                        // Try static method b(Context, Class) / a(Context, Class)
                        if (provider == null) {
                            try {
                                Method bMethod = binderCls.getMethod("b", Context.class, Class.class);
                                provider = bMethod.invoke(null, context, provCls);
                            } catch (Throwable ignored) {}
                        }

                        if (provider != null) {
                            // If provider is a wrapper (e.g. Component / ahtz), unwrap via a() or get()
                            try {
                                Method unwrapMethod = provider.getClass().getMethod("a");
                                Object unwrapped = unwrapMethod.invoke(provider);
                                if (unwrapped != null) {
                                    provider = unwrapped;
                                }
                            } catch (Throwable ignored) {}

                            // Call provider.a(mediaItem)
                            try {
                                for (Method pm : provider.getClass().getMethods()) {
                                    if (pm.getParameterTypes().length == 1 &&
                                        pm.getParameterTypes()[0].isInstance(mediaItem) &&
                                        Uri.class.isAssignableFrom(pm.getReturnType())) {
                                        Uri uri = (Uri) pm.invoke(provider, mediaItem);
                                        if (uri != null) return uri;
                                    }
                                }
                            } catch (Throwable ignored) {}

                            // Try wiw.d (ORIGINAL) or wiw.c (LARGE)
                            try {
                                Class<?> wiwClass = Class.forName("wiw");
                                for (Method pm : provider.getClass().getMethods()) {
                                    if (pm.getParameterTypes().length == 3 &&
                                        pm.getParameterTypes()[0].isInstance(mediaItem) &&
                                        Uri.class.isAssignableFrom(pm.getReturnType())) {
                                        try {
                                            Object origVal = wiwClass.getField("d").get(null);
                                            Uri uri = (Uri) pm.invoke(provider, mediaItem, origVal, 0);
                                            if (uri != null) return uri;
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (ClassNotFoundException ignored) {}
                }
            } catch (ClassNotFoundException ignored) {}
        }

        // Attempt 2: Check methods on mediaItem itself that return Uri
        try {
            for (Method m : mediaItem.getClass().getMethods()) {
                if (m.getParameterTypes().length == 0 && Uri.class.isAssignableFrom(m.getReturnType())) {
                    try {
                        Uri uri = (Uri) m.invoke(mediaItem);
                        if (uri != null && uri.toString().length() > 0) {
                            return uri;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Attempt 3: Check fields on mediaItem for Uri
        try {
            for (Field f : mediaItem.getClass().getDeclaredFields()) {
                if (Uri.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Uri uri = (Uri) f.get(mediaItem);
                        if (uri != null && uri.toString().length() > 0) {
                            return uri;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        Log.w(TAG, "Failed resolving media URI via all providers for item: " + mediaItem);
        return null;
    }

    private static Context extractContext(Object mixin) {
        for (Field f : mixin.getClass().getDeclaredFields()) {
            if (Context.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    return (Context) f.get(mixin);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static Context getApplicationContext() {
        if (sAppContext != null) return sAppContext;
        try {
            Class<?> atCls = Class.forName("android.app.ActivityThread");
            Method caMethod = atCls.getMethod("currentApplication");
            Object app = caMethod.invoke(null);
            if (app instanceof Context) {
                sAppContext = ((Context) app).getApplicationContext();
                return sAppContext;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void notifySaveListeners(Object mixin, Object mediaList) {
        // Direct call to Lakxr->c(Lcchb) which notifies save listeners
        try {
            for (Method m : mixin.getClass().getDeclaredMethods()) {
                if (m.getName().equals("c") && m.getParameterTypes().length == 1) {
                    m.setAccessible(true);
                    m.invoke(mixin, mediaList);
                    Log.d(TAG, "Invoked mixin.c(mediaList) directly");
                    return;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed invoking mixin.c", t);
        }

        // Generic fallback for any 1-arg void method matching mediaList
        for (Method m : mixin.getClass().getDeclaredMethods()) {
            if (m.getReturnType() == void.class && m.getParameterTypes().length == 1) {
                if (m.getParameterTypes()[0].isInstance(mediaList) ||
                    m.getParameterTypes()[0].isAssignableFrom(mediaList.getClass())) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mixin, mediaList);
                        Log.d(TAG, "Invoked notify method: " + m.getName());
                        return;
                    } catch (Throwable t) {
                        Log.w(TAG, "Failed invoking notify method", t);
                    }
                }
            }
        }
    }

    private static void notifyStoryUi(Object mixin) {
        try {
            for (Field f : mixin.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object wrapper = f.get(mixin);
                if (wrapper != null) {
                    Method getMethod = null;
                    try {
                        getMethod = wrapper.getClass().getMethod("a");
                    } catch (NoSuchMethodException ignored) {
                        try {
                            getMethod = wrapper.getClass().getMethod("get");
                        } catch (NoSuchMethodException ignored2) {}
                    }
                    if (getMethod != null) {
                        Object target = getMethod.invoke(wrapper);
                        if (target != null) {
                            for (Method tm : target.getClass().getDeclaredMethods()) {
                                if (tm.getParameterTypes().length == 0 && tm.getReturnType() == void.class) {
                                    if (tm.getName().equals("s") || tm.getName().equals("advance")) {
                                        tm.setAccessible(true);
                                        tm.invoke(target);
                                        Log.d(TAG, "Notified story UI via " + tm.getName());
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not notify story UI: " + t.getMessage());
        }
    }
}
