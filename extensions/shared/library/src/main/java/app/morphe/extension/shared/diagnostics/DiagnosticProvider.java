package app.morphe.extension.shared.diagnostics;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only, URI-granted provider exposing generated reports from one cache directory. */
public final class DiagnosticProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (uri.getPathSegments().size() != 1 || name == null || !name.matches("report_[A-Za-z0-9_-]+\\.(log|md)")) throw new FileNotFoundException();
        File dir = new File(getContext().getCacheDir(), "morphe_share");
        File file = new File(dir, name);
        try { if (!file.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())) throw new FileNotFoundException(); }
        catch (java.io.IOException e) { throw new FileNotFoundException(); }
        return file;
    }
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public String getType(Uri uri) { return "text/plain"; }
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File f = resolve(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()}); return c;
        } catch (FileNotFoundException e) { return null; }
    }
    public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
