package app.morphe.extension.shared.diagnostics;

/** All extension logcat output is scrubbed before reaching Android's logger. */
public final class SafeLog {
    private SafeLog() {}
    private static String text(String message, Throwable error) {
        String result = message == null ? "" : message;
        if (error != null) result += "\n" + android.util.Log.getStackTraceString(error);
        return DiagnosticRedactor.sanitize(result);
    }
    public static int d(String tag, String message) { return android.util.Log.d(tag, text(message, null)); }
    public static int d(String tag, String message, Throwable error) { return android.util.Log.d(tag, text(message, error)); }
    public static int i(String tag, String message) { return android.util.Log.i(tag, text(message, null)); }
    public static int i(String tag, String message, Throwable error) { return android.util.Log.i(tag, text(message, error)); }
    public static int w(String tag, String message) { return android.util.Log.w(tag, text(message, null)); }
    public static int w(String tag, String message, Throwable error) { return android.util.Log.w(tag, text(message, error)); }
    public static int e(String tag, String message) { return android.util.Log.e(tag, text(message, null)); }
    public static int e(String tag, String message, Throwable error) { return android.util.Log.e(tag, text(message, error)); }
}
