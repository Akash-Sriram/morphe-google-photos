package app.morphe.extension.shared.requests;

public final class ExportSafety {
    private ExportSafety() {}
    public static String filename(String value) {
        String name = value == null ? "" : value.replaceAll("[\\p{Cntrl}/\\\\]", "_").trim();
        name = name.replaceAll("^\\.+", "_");
        if (name.isEmpty()) name = "photo.jpg";
        if (name.length() > 150) {
            int dot = name.lastIndexOf('.');
            String extension = dot >= 0 && name.length() - dot < 12 ? name.substring(dot) : "";
            name = name.substring(0, 150 - extension.length()) + extension;
        }
        return name;
    }
    public static String csvCell(String value) {
        if (value == null) return "";
        // A leading apostrophe forces spreadsheet text; double an existing apostrophe
        // so this escaping can be reversed unambiguously by a CSV consumer.
        if (value.startsWith("'") || value.matches("(?s)^[\\s\\p{Z}]*[=+@-].*")) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String resolveMimeType(String filename, String fallbackMime) {
        String ext = "";
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                ext = filename.substring(dot + 1).trim().toLowerCase(java.util.Locale.US);
            }
        }

        String extMime = getKnownMimeType(ext);
        if (extMime == null && !ext.isEmpty()) {
            try {
                Class<?> mapClass = Class.forName("android.webkit.MimeTypeMap");
                Object singleton = mapClass.getMethod("getSingleton").invoke(null);
                if (singleton != null) {
                    extMime = (String) mapClass.getMethod("getMimeTypeFromExtension", String.class).invoke(singleton, ext);
                }
            } catch (Throwable ignored) {}
        }

        String cleanFallback = fallbackMime != null ? fallbackMime.trim().toLowerCase(java.util.Locale.US) : null;
        if (cleanFallback != null && cleanFallback.isEmpty()) {
            cleanFallback = null;
        }

        boolean extIsVideo = extMime != null && extMime.startsWith("video/");
        boolean extIsImage = extMime != null && extMime.startsWith("image/");
        boolean fallbackIsVideo = cleanFallback != null && cleanFallback.startsWith("video/");
        boolean fallbackIsImage = cleanFallback != null && cleanFallback.startsWith("image/");

        if (extIsVideo) {
            return fallbackIsVideo ? cleanFallback : extMime;
        }
        if (extIsImage) {
            return fallbackIsImage ? cleanFallback : extMime;
        }
        if (fallbackIsVideo || fallbackIsImage) {
            return cleanFallback;
        }
        return extMime != null ? extMime : "image/jpeg";
    }

    public static boolean isVideo(String filename, String mimeType, long durationMs) {
        if (durationMs > 0) return true;
        if (mimeType != null && mimeType.toLowerCase(java.util.Locale.US).contains("video")) return true;
        if (filename != null) {
            String lower = filename.trim().toLowerCase(java.util.Locale.US);
            int dot = lower.lastIndexOf('.');
            if (dot >= 0 && dot < lower.length() - 1) {
                String ext = lower.substring(dot + 1);
                String known = getKnownMimeType(ext);
                if (known != null && known.startsWith("video/")) return true;
            }
        }
        return false;
    }

    private static String getKnownMimeType(String ext) {
        if (ext == null || ext.isEmpty()) return null;
        switch (ext) {
            case "mp4":
            case "m4v":
                return "video/mp4";
            case "mov":
                return "video/quicktime";
            case "webm":
                return "video/webm";
            case "mkv":
                return "video/x-matroska";
            case "3gp":
            case "3gpp":
                return "video/3gpp";
            case "avi":
                return "video/x-msvideo";
            case "ts":
                return "video/mp2t";
            case "wmv":
                return "video/x-ms-wmv";
            case "flv":
                return "video/x-flv";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "heic":
                return "image/heic";
            case "heif":
                return "image/heif";
            case "dng":
                return "image/x-adobe-dng";
            case "bmp":
                return "image/bmp";
            case "avif":
                return "image/avif";
            case "tif":
            case "tiff":
                return "image/tiff";
            default:
                return null;
        }
    }
}
