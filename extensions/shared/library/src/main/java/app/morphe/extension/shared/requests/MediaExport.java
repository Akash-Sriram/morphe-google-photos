package app.morphe.extension.shared.requests;

import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import android.system.Os;
import android.system.OsConstants;

/** Publishes complete cache files without exposing partial downloads or overwriting files. */
public final class MediaExport {
    private MediaExport() {}
    @FunctionalInterface
    public interface StreamWriter {
        void writeTo(OutputStream out) throws Exception;
    }

    public static String publish(Context context, File source, String name, String mime) throws Exception {
        return publishStream(context, name, mime, out -> {
            try (InputStream in = new FileInputStream(source)) {
                copy(in, out);
            }
        });
    }

    public static String publishStream(Context context, String name, String mime, StreamWriter writer) throws Exception {
        name = ExportSafety.filename(name);
        String resolvedMime = ExportSafety.resolveMimeType(name, mime);
        boolean isVideo = resolvedMime.startsWith("video/");
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/GooglePhotos_Export");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri collection = isVideo ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Uri uri = context.getContentResolver().insert(collection, values);
            if (uri == null) throw new IOException("EXPORT_CREATE_FAILED");
            try {
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("EXPORT_WRITE_FAILED");
                    writer.writeTo(out);
                    out.flush();
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                if (context.getContentResolver().update(uri, values, null, null) != 1) throw new IOException("EXPORT_COMMIT_FAILED");
                return uri.toString();
            } catch (Exception e) { context.getContentResolver().delete(uri, null, null); throw e; }
        }
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getCanonicalFile();
        File dir = new File(pictures, "GooglePhotos_Export");
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("EXPORT_CREATE_FAILED");
        if (!dir.getCanonicalFile().getParentFile().equals(pictures)) throw new IOException("EXPORT_PATH_BLOCKED");
        for (int i = 0; i < 10000; i++) {
            File target = new File(dir, i == 0 ? name : i + "_" + name);
            java.io.FileDescriptor fd;
            try { fd = Os.open(target.getPath(), OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL | OsConstants.O_NOFOLLOW, 0600); }
            catch (android.system.ErrnoException e) { if (e.errno == OsConstants.EEXIST) continue; throw e; }
            try (OutputStream out = new FileOutputStream(fd)) {
                writer.writeTo(out);
                out.flush();
            } catch (Exception e) { target.delete(); throw e; }
            return target.getPath();
        }
        throw new IOException("EXPORT_NAME_EXHAUSTED");
    }
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] bytes = new byte[65536]; int count;
        while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
    }
}
