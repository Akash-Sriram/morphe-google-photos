package app.morphe.extension.shared.diagnostics;

import java.util.regex.Pattern;

/** Best-effort removal of credentials and private media links from diagnostic text. */
public final class DiagnosticRedactor {
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?|content|file)://[^\\s<>\"']+");
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+");
    private static final Pattern HEADER = Pattern.compile("(?im)\\b(?:authorization|proxy-authorization|cookie|set-cookie)[\"']?\\s*[:=][^\\r\\n]*");
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(?:bearer|basic)\\s+[^\\s,;\"']+");
    private static final Pattern TOKEN = Pattern.compile("(?i)\\b(auth_token|access_token|refresh_token|id_token|api_key|password|rapt|SAPISID|SID|HSID|SSID|bearer)[\"']?\\s*[:=]\\s*(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\s,;\"'}]+)");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");

    private DiagnosticRedactor() {}

    public static String sanitize(String input) {
        if (input == null) return null;
        String result = URL.matcher(input).replaceAll("[REDACTED_URL]");
        result = EMAIL.matcher(result).replaceAll("[REDACTED_EMAIL]");
        result = HEADER.matcher(result).replaceAll("[REDACTED_AUTH_HEADER]");
        result = BEARER.matcher(result).replaceAll("[REDACTED_CREDENTIAL]");
        result = TOKEN.matcher(result).replaceAll("$1=[REDACTED_TOKEN]");
        result = result.replaceAll("(?:/storage/|/sdcard/|/data/|/mnt/)[^\\r\\n<>\"]+", "[REDACTED_PATH]");
        result = JWT.matcher(result).replaceAll("[REDACTED_TOKEN]");
        return result.length() > 32768 ? result.substring(0, 32768) + " [TRUNCATED]" : result;
    }
}
