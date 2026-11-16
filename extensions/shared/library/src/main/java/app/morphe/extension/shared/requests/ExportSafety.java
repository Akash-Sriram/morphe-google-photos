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
}
