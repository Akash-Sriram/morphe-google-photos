package app.morphe.extension.shared.requests;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** URL checks shared by authenticated downloads and the Photos WebView. */
public final class TrustedGoogleMedia {
    private TrustedGoogleMedia() {}
    public enum Reason { UNTRUSTED_HOST, INSECURE_SCHEME, REDIRECT_BLOCKED, AUTH_FAILED, HTTP_ERROR }
    public static final class DownloadException extends IOException {
        public final Reason reason;
        public DownloadException(Reason reason, String message) { super(reason + ": " + message); this.reason = reason; }
    }

    private static URI parseHttps(String value) {
        if (value == null) return null;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return null;
            }
            return uri;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static boolean isHost(String value, String host) {
        URI uri = parseHttps(value);
        return uri != null && host.equalsIgnoreCase(uri.getHost());
    }

    public static boolean isMediaUrl(String value) {
        URI uri = parseHttps(value);
        if (uri == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("googleusercontent.com") || host.endsWith(".googleusercontent.com")
                || host.equals("photos.google.com") || host.equals("photos.googleapis.com")
                || host.equals("lh3.google.com");
    }

    /** Validate every redirect before opening a connection or attaching credentials. */
    public static HttpURLConnection open(String value, String token) throws IOException {
        String credentialHost = parseHttps(value) == null ? "" : parseHttps(value).getHost();
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!isMediaUrl(value)) throw new DownloadException(Reason.UNTRUSTED_HOST, "update the patch if Google changed its media host");
            URI uri = URI.create(value);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");
            try {
                int status = connection.getResponseCode();
                if ((status == 401 || status == 403) && token != null && !token.isEmpty()
                        && credentialHost.equalsIgnoreCase(uri.getHost())) {
                    connection.disconnect();
                    connection = (HttpURLConnection) uri.toURL().openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(60000);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                    status = connection.getResponseCode();
                }
                if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) {
                    return connection;
                }
                String location = connection.getHeaderField("Location");
                if (location == null) throw new DownloadException(Reason.REDIRECT_BLOCKED, "missing redirect location");
                try {
                    value = uri.resolve(new URI(location)).toString();
                } catch (URISyntaxException | IllegalArgumentException e) {
                    throw new DownloadException(Reason.REDIRECT_BLOCKED, "invalid redirect location");
                }
            } catch (IOException | RuntimeException e) {
                connection.disconnect();
                throw e;
            }
            connection.disconnect();
        }
        throw new DownloadException(Reason.REDIRECT_BLOCKED, "too many redirects");
    }
}
