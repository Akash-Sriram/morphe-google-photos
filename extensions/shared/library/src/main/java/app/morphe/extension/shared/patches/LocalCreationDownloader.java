package app.morphe.extension.shared.patches;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Intercepts saving memory collages and creations in Google Photos.
 * Instead of committing the creation solely to Google's cloud server (which debits account storage quota),
 * this downloader exports the media stream directly to the device's DCIM/Camera folder.
 * Google Photos then detects the local file and backs it up under the Pixel XL quota-free exemption.
 */
public class LocalCreationDownloader {
    private static final String TAG = "LocalCreationDownloader";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

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
                Log.w(TAG, "No Context found on SaveCreationMixin");
                return false;
            }

            Collection<?> items = (mediaList instanceof Collection)
                    ? (Collection<?>) mediaList
                    : null;
            if (items == null || items.isEmpty()) {
                return false;
            }

            // Save locally to DCIM/Camera
            boolean saved = saveCreationToCamera(context, items);
            if (!saved) {
                return false;
            }

            // Notify save listeners so UI updates button to "Saved"
            notifySaveListeners(saveCreationMixin, mediaList);

            // Advance / update story UI
            notifyStoryUi(saveCreationMixin);

            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error during onSaveRequested interception", t);
            return false;
        }
    }

    public static boolean saveCreationToCamera(Context context, Collection<?> mediaList) {
        if (context == null || mediaList == null || mediaList.isEmpty()) {
            return false;
        }

        final Context appContext = context.getApplicationContext();
        Log.i(TAG, "Intercepted creation save request for " + mediaList.size() + " item(s). Redirecting to DCIM/Google Photos.");

        // Dispatch background save to avoid blocking the main UI thread
        EXECUTOR.execute(() -> {
            int successCount = 0;
            for (Object media : mediaList) {
                if (media == null) continue;
                if (saveSingleItem(appContext, media)) {
                    successCount++;
                }
            }

            final int saved = successCount;
            MAIN_HANDLER.post(() -> {
                if (saved > 0) {
                    Toast.makeText(appContext, "Creation saved to Google Photos folder (quota-free)", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Successfully exported " + saved + " creation(s) to DCIM/Google Photos.");
                } else {
                    Log.w(TAG, "Failed to resolve local stream for creation item(s).");
                }
            });
        });

        return true;
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
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = (isVideo ? "Highlight_" : "Collage_") + timeStamp + ext;

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
                return true;
            } else {
                File dcimDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Google Photos");
                if (!dcimDir.exists() && !dcimDir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: " + dcimDir.getAbsolutePath());
                    return false;
                }

                File destFile = new File(dcimDir, fileName);
                try (InputStream in = openMediaStream(resolver, mediaUri);
                     OutputStream out = new FileOutputStream(destFile)) {
                    if (in == null) return false;
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }

                MediaScannerConnection.scanFile(context,
                        new String[]{destFile.getAbsolutePath()},
                        new String[]{mimeType},
                        null);
                return true;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error streaming creation to local storage", t);
            return false;
        }
    }

    private static InputStream openMediaStream(ContentResolver resolver, Uri uri) throws Exception {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String urlStr = uri.toString();

            // Force original quality for Fife (googleusercontent) URLs.
            // Fife encodes size/quality params as a trailing "=<params>" suffix.
            // Strip any existing suffix and append "=d" (download original bytes).
            if (urlStr.contains("googleusercontent.com") || urlStr.contains("lh3.google")) {
                int eqPos = urlStr.lastIndexOf('=');
                // Only strip if '=' appears after the path (not inside query/fragment)
                int slashAfterHost = urlStr.indexOf('/', urlStr.indexOf("://") + 3);
                if (eqPos > 0 && eqPos > slashAfterHost) {
                    // Check it's actually a Fife param (not a query-string key=value)
                    String suffix = urlStr.substring(eqPos);
                    if (!suffix.contains("&") && !suffix.contains("?")) {
                        urlStr = urlStr.substring(0, eqPos) + "=d";
                    } else {
                        // Append download flag as separate param won't work — try replacing known size tokens
                        urlStr = urlStr.replaceAll("=s\\d+", "=d").replaceAll("=w\\d+-h\\d+", "=d");
                    }
                } else {
                    // No Fife suffix yet — append download param
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

    private static void notifySaveListeners(Object mixin, Object mediaList) {
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
