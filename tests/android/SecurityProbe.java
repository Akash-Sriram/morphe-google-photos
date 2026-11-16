package app.morphe.securityprobe;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Instrumented integration checks: accesses only probe-created media and synthetic errors. */
public final class SecurityProbe extends Instrumentation {
    private int checks;
    private boolean cloudOnly;
    private final List<Uri> created = new ArrayList<>();
    private void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    private Class<?> production(String name) throws Exception {
        return getTargetContext().getClassLoader().loadClass("app.morphe.extension.shared." + name);
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); cloudOnly = arguments != null && "cloud".equals(arguments.getString("mode")); start(); }
    /** Reads one existing cloud thumbnail into memory; reports only host/status, never identifiers or bytes. */
    private void observeCloud() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            Class<?> scanner = production("patches.toolkit.StorageScanner");
            Object account = scanner.getMethod("getActiveAccount", Context.class).invoke(null, context);
            if (account == null) throw new IllegalStateException("No active Photos account");
            String path = (String) account.getClass().getField("dbPath").get(account);
            String selectedUrl = null;
            java.util.Set<String> hosts = new java.util.TreeSet<>();
            try (android.database.sqlite.SQLiteDatabase database = android.database.sqlite.SQLiteDatabase.openDatabase(path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
                 Cursor cursor = database.rawQuery("SELECT remote_url FROM remote_media WHERE remote_url LIKE 'https://%' LIMIT 200", null)) {
                while (cursor.moveToNext()) {
                    String url = cursor.getString(0);
                    java.net.URI parsed = new java.net.URI(url);
                    if (parsed.getHost() != null) hosts.add(parsed.getHost());
                    if (selectedUrl == null) selectedUrl = url;
                }
            }
            if (selectedUrl == null) throw new IllegalStateException("No cloud URL available");
            result.putString("hosts", hosts.toString());
            result.putString("processUid", String.valueOf(android.os.Process.myUid()));
            result.putString("targetUid", String.valueOf(context.getApplicationInfo().uid));
            java.net.InetAddress.getAllByName(new java.net.URI(selectedUrl).getHost());
            result.putString("dns", "resolved");
            Method open = production("requests.TrustedGoogleMedia").getMethod("open", String.class, String.class);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) open.invoke(null, selectedUrl, null);
            int anonymousStatus = connection.getResponseCode();
            int finalStatus = anonymousStatus;
            boolean triedAuth = false;
            if (finalStatus == 401 || finalStatus == 403) {
                connection.disconnect();
                String email = (String) account.getClass().getField("accountName").get(account);
                String token = (String) production("patches.LocalCreationDownloader").getMethod("getAuthToken", Context.class, String.class).invoke(null, context, email);
                triedAuth = token != null;
                connection = (java.net.HttpURLConnection) open.invoke(null, selectedUrl, token);
                finalStatus = connection.getResponseCode();
            }
            int read;
            try (InputStream stream = connection.getInputStream()) { read = stream.read(new byte[4096]); }
            finally { connection.disconnect(); }
            result.putString("result", "Cloud metadata hosts=" + hosts + "; selected host=" + new java.net.URI(selectedUrl).getHost()
                + "; anonymous HTTP=" + anonymousStatus + "; auth needed=" + triedAuth + "; final HTTP=" + finalStatus + "; media bytes readable=" + (read > 0));
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException ? ((InvocationTargetException) error).getCause() : error;
            result.putString("result", "Cloud probe unavailable: " + cause.getClass().getSimpleName());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    @Override public void onStart() {
        if (cloudOnly) { observeCloud(); return; }
        Bundle result = new Bundle();
        Context context = getTargetContext();
        File source = null;
        File report = null;
        int finishCode = Activity.RESULT_CANCELED;
        try {
            source = File.createTempFile("security_probe_", ".png", context.getCacheDir());
            Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xff2196f3);
            try (OutputStream out = new FileOutputStream(source)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
            bitmap.recycle();
            Method publish = production("requests.MediaExport").getMethod("publish", Context.class, File.class, String.class, String.class);
            for (String name : new String[]{"MorpheSecurityProbe.png", "../MorpheSecurityProbe.png", "/outside/MorpheSecurityProbe.png", "MorpheSecurityProbe.png"}) {
                Uri uri = Uri.parse((String) publish.invoke(null, context, source, name, "image/png"));
                created.add(uri);
                try (Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.SIZE}, null, null, null)) {
                    check(cursor != null && cursor.moveToFirst(), "Created media query");
                    check(!cursor.getString(0).contains("/"), "Filename confinement");
                    check("Pictures/GooglePhotos_Export/".equals(cursor.getString(1)), "Destination confinement");
                    check(cursor.getInt(2) == 0, "MediaStore committed");
                    check(cursor.getLong(3) == source.length(), "Published byte length");
                }
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] block = new byte[4096]; int n;
                    while ((n = in.read(block)) != -1) bytes.write(block, 0, n);
                    check(Arrays.equals(java.nio.file.Files.readAllBytes(source.toPath()), bytes.toByteArray()), "Exact media bytes");
                }
            }
            check(!created.get(0).equals(created.get(3)), "Repeated export unique object");
            Class<?> itemType = production("patches.toolkit.StorageScanner$MediaItem");
            Object item = itemType.getConstructors()[0].newInstance("probe-dedup", "probe-media", "=HYPERLINK(1)", 10L,
                0L, 1609459200000L, "https://lh3.googleusercontent.com/private-probe", 0L, 16, 16, 0L,
                false, false, false, "image/png", 12.34, 56.78, "=1+1");
            java.util.TimeZone previousZone = java.util.TimeZone.getDefault();
            String basic;
            String full;
            try {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("GMT+09:00"));
                Method csv = production("patches.toolkit.StorageScanner").getMethod("exportToCsv", List.class, boolean.class);
                basic = (String) csv.invoke(null, Collections.singletonList(item), false);
                full = (String) csv.invoke(null, Collections.singletonList(item), true);
            } finally { java.util.TimeZone.setDefault(previousZone); }
            check(basic.contains("2021-01-01T00:00:00Z"), "CSV UTC independent of device zone");
            check(basic.contains("\"'=HYPERLINK(1)\""), "Basic CSV formula escaping");
            check(!basic.contains("private-probe") && !basic.contains("12.34") && !basic.contains("probe-media") && !basic.contains("=1+1"), "Basic CSV omits private fields");
            check(full.contains("private-probe") && full.contains("12.34") && full.contains("\"'=1+1\""), "Full CSV retains metadata with formula escaping");
            java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
            try {
                server.setSoTimeout(300);
                boolean rejected = false;
                try {
                    production("requests.TrustedGoogleMedia").getMethod("open", String.class, String.class)
                        .invoke(null, "https://127.0.0.1:" + server.getLocalPort() + "/private-probe", "synthetic-token");
                } catch (InvocationTargetException expected) { rejected = expected.getCause() instanceof IOException; }
                check(rejected, "Controlled foreign endpoint rejected");
                boolean noConnection = false;
                try { server.accept().close(); } catch (java.net.SocketTimeoutException expected) { noConnection = true; }
                check(noConnection, "Controlled foreign endpoint receives no connection");
            } finally { server.close(); }
            Class<?> logs = production("diagnostics.SessionLogManager");
            Method sanitize = logs.getMethod("sanitize", String.class);
            String secret = "SECURITY_PROBE_SECRET_84729";
            for (String text : new String[]{"Bearer " + secret, "access_token=" + secret, "https://lh3.googleusercontent.com/" + secret, "/storage/emulated/0/DCIM/" + secret + ".png"}) {
                check(!((String) sanitize.invoke(null, text)).contains(secret), "Diagnostic scrub on Android");
            }
            logs.getMethod("appendLog", String.class, String.class, String.class, Throwable.class).invoke(null, "SecurityProbe", "ERROR", "Probe failure", new IOException("Bearer " + secret));
            Method crash = logs.getDeclaredMethod("handleCrash", Thread.class, Throwable.class);
            crash.setAccessible(true);
            crash.invoke(null, Thread.currentThread(), new IOException("Bearer " + secret));
            Object sessions = logs.getMethod("getAllSessions").invoke(null);
            int crashed = 0;
            for (Object session : (List<?>) sessions) {
                Class<?> sessionType = session.getClass();
                String summary = (String) sessionType.getField("crashSummary").get(session);
                if (summary != null) {
                    check(!summary.contains(secret), "Crash summary scrubbed");
                    crashed++;
                }
                File file = (File) sessionType.getField("file").get(session);
                check(!new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8).contains(secret), "Synthetic secret absent from stored log");
            }
            check(crashed > 0, "Synthetic crash captured");
            File directory = new File(context.getCacheDir(), "morphe_share"); directory.mkdirs();
            report = new File(directory, "report_probe.log");
            try (FileOutputStream out = new FileOutputStream(report)) { out.write("synthetic probe".getBytes("UTF-8")); }
            Uri reportUri = Uri.parse("content://" + context.getPackageName() + ".morphe.diagnostics/report_probe.log");
            try (InputStream in = context.getContentResolver().openInputStream(reportUri)) { check(in.read() == 's', "Provider own report read"); }
            boolean readOnly = false;
            try { context.getContentResolver().openOutputStream(reportUri).close(); }
            catch (Exception expected) { readOnly = true; }
            check(readOnly, "Provider rejects write");
            boolean traversalBlocked = false;
            try { context.getContentResolver().openInputStream(Uri.parse("content://" + context.getPackageName() + ".morphe.diagnostics/../report_probe.log")).close(); }
            catch (Exception expected) { traversalBlocked = true; }
            check(traversalBlocked, "Provider rejects path traversal");
            result.putString("result", "PASS " + checks + " Android integration checks");
            finishCode = Activity.RESULT_OK;
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException ? ((InvocationTargetException) error).getCause() : error;
            result.putString("result", "FAIL after " + checks + " checks: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());

        } finally {
            for (Uri uri : created) context.getContentResolver().delete(uri, null, null);
            if (source != null) source.delete();
            if (report != null) report.delete();
        }
        finish(finishCode, result);
    }
}
