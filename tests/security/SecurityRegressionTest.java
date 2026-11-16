import app.morphe.extension.shared.requests.TrustedGoogleMedia;
import app.morphe.extension.shared.diagnostics.DiagnosticRedactor;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.List;

/** Runs against production helpers, with fake HTTP connections and no network access. */
public final class SecurityRegressionTest {
    private static final List<FakeConnection> opened = new ArrayList<>();
    private static String redirect;
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static final class FakeConnection extends HttpURLConnection {
        boolean disconnected;
        FakeConnection(URL url) { super(url); }
        public int getResponseCode() { return url.getPath().equals("/redirect") ? 302 : (url.getPath().equals("/private") && getRequestProperty("Authorization") == null ? 401 : 200); }
        public String getHeaderField(String name) { return "Location".equals(name) ? redirect : null; }
        public void disconnect() { disconnected = true; }
        public boolean usingProxy() { return false; }
        public void connect() {}
    }

    private static void blocked(String value) throws Exception {
        int before = opened.size();
        try {
            TrustedGoogleMedia.open(value, "test-secret");
            throw new AssertionError("Allowed unsafe destination");
        } catch (IOException expected) {
            check(opened.size() == before, "Connected to unsafe destination");
        }
    }

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            protected URLConnection openConnection(URL url) {
                FakeConnection result = new FakeConnection(url);
                opened.add(result);
                return result;
            }
        } : null);

        for (String good : new String[]{"https://lh3.googleusercontent.com/private=d",
                "https://photos.google.com/path", "https://photos.googleapis.com/media",
                "https://LH3.GOOGLEUSERCONTENT.COM:443/media"}) {
            check(TrustedGoogleMedia.isMediaUrl(good), "Rejected Google media");
        }
        for (String bad : new String[]{null, "", "http://lh3.googleusercontent.com/private",
                "https://googleusercontent.com.attacker.invalid/x", "https://evilgoogleusercontent.com/x",
                "https://photos.google.com.attacker.invalid/x", "https://photos.google.com@attacker.invalid/x",
                "https://attacker@photos.google.com/x", "https://photos.google.com:8443/x",
                "https://photos.google.com./x", "https://photos.google.com\\@attacker.invalid/x",
                "https://photos.google.com%2eattacker.invalid/x", "file:///private", "javascript:alert(1)"}) {
            check(!TrustedGoogleMedia.isMediaUrl(bad), "Trusted unsafe media URL");
            blocked(bad);
        }
        check(TrustedGoogleMedia.isHost("https://PHOTOS.GOOGLE.COM:443/a", "photos.google.com"), "Host check");
        check(!TrustedGoogleMedia.isHost("https://photos.google.com.evil.invalid", "photos.google.com"), "WebView prefix bypass");
        check(!TrustedGoogleMedia.isHost("https://evil.invalid/?next=https://accounts.google.com", "accounts.google.com"), "WebView query bypass");

        HttpURLConnection direct = TrustedGoogleMedia.open("https://lh3.googleusercontent.com/private", "test-secret");
        check("Bearer test-secret".equals(direct.getRequestProperty("Authorization")), "Lost valid authentication");
        check(!direct.getInstanceFollowRedirects(), "Automatic redirect enabled");
        direct.disconnect();
        HttpURLConnection anonymous = TrustedGoogleMedia.open("https://lh3.googleusercontent.com/media", null);
        check(anonymous.getRequestProperty("Authorization") == null, "Anonymous request got credentials");
        anonymous.disconnect();

        redirect = "/private";
        int before = opened.size();
        TrustedGoogleMedia.open("https://lh3.googleusercontent.com/redirect", "test-secret").disconnect();
        check(opened.size() == before + 3, "Relative redirect failed");
        check(opened.get(before).disconnected, "Redirect connection leaked");
        check("Bearer test-secret".equals(opened.get(before + 2).getRequestProperty("Authorization")), "Valid redirect lost auth");
        for (String badRedirect : new String[]{"https://attacker.invalid/media", "//attacker.invalid/media",
                "http://lh3.googleusercontent.com/media", "https://photos.google.com.attacker.invalid/media",
                "https://user@lh3.googleusercontent.com/media", "https://lh3.googleusercontent.com:8443/media",
                "https://bad host/", "/redirect", null}) {
            redirect = badRedirect;
            before = opened.size();
            try {
                TrustedGoogleMedia.open("https://lh3.googleusercontent.com/redirect", "test-secret");
                throw new AssertionError("Unsafe redirect followed");
            } catch (IOException expected) {
                for (FakeConnection connection : opened.subList(before, opened.size())) {
                    check(connection.getURL().getHost().equals("lh3.googleusercontent.com"), "Secret sent off host");
                    check(connection.disconnected, "Failed redirect connection leaked");
                }
            }
        }
        check(DiagnosticRedactor.sanitize(null) == null, "Null log input");
        for (String input : new String[]{"HTTP 403 https://lh3.googleusercontent.com/private-photo-key?sig=private-secret",
                "Failed content://photos/private-secret", "file:///storage/private-secret.jpg",
                "user private-secret@example.org", "Authorization: Bearer private-secret",
                "Cookie: SID=private-secret; other=secret", "Bearer private-secret",
                "{\"access_token\":\"private-secret\"}", "refresh_token=private-secret",
                "password: private-secret", "rapt=private-secret", "bearer=private-secret",
                "password: \"a private-secret with spaces\"", "password='a private-secret with spaces'"}) {
            check(!DiagnosticRedactor.sanitize(input).contains("private-secret"), "Sensitive log content survived");
        }
        check(DiagnosticRedactor.sanitize("HTTP 403: download failed").equals("HTTP 403: download failed"), "Useful diagnostic removed");
        for (String badName : new String[]{"../outside.jpg", "/tmp/x.jpg", "a/b.jpg", "..", "", "a".repeat(300) + ".jpg"}) {
            String safe = app.morphe.extension.shared.requests.ExportSafety.filename(badName);
            check(!safe.contains("/") && !safe.contains("\\") && !safe.equals("..") && safe.length() <= 150, "Unsafe export name");
        }
        for (String formula : new String[]{"=HYPERLINK(1)", "  +1", "\t-1", "@SUM(1)", "=1\n+2"}) {
            check(app.morphe.extension.shared.requests.ExportSafety.csvCell(formula).startsWith("\"'"), "Spreadsheet formula survived");
        }
        check(!DiagnosticRedactor.sanitize("Failed /storage/emulated/0/DCIM/private-secret.jpg").contains("private-secret"), "Path survived");
        redirect = "https://photos.google.com/media";
        before = opened.size();
        TrustedGoogleMedia.open("https://lh3.googleusercontent.com/redirect", "test-secret").disconnect();
        check(opened.get(before + 1).getRequestProperty("Authorization") == null, "Token forwarded across hosts");
        System.out.println("Passed " + checks + " security checks (no network).");
    }
}
