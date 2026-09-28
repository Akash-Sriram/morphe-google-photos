package app.morphe.extension.shared.patches.toolkit;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Logger;

/**
 * High-performance, schema-resilient SQLite scanner for Google Photos internal databases.
 * Joins master media index with local_media, remote_media, item_collection_data, and burst_media
 * to provide a comprehensive toolkit dataset matching Google-Photos-Toolkit capabilities.
 */
public final class PhotosDatabaseScanner {

    public static class MediaItemSummary {
        public final long id;
        public final String mediaKey;
        public final String dedupKey;
        public final String filename;
        public final String filepath;
        public final long sizeBytes;
        public final long quotaChargedBytes;
        public final long timestamp;
        public final int type;
        public final boolean isFavorite;
        public final boolean isArchived;
        public final boolean isDeleted;
        public final boolean inCamera;
        public final boolean hasLocal;

        public MediaItemSummary(long id, String mediaKey, String dedupKey, String filename, String filepath,
                                long sizeBytes, long quotaChargedBytes, long timestamp,
                                int type, boolean isFavorite, boolean isArchived, boolean isDeleted,
                                boolean inCamera, boolean hasLocal) {
            this.id = id;
            this.mediaKey = mediaKey != null ? mediaKey : "";
            this.dedupKey = dedupKey != null ? dedupKey : "";
            this.filename = filename != null && !filename.isEmpty() ? filename : "Photo_" + id;
            this.filepath = filepath != null ? filepath : "";
            this.sizeBytes = sizeBytes;
            this.quotaChargedBytes = quotaChargedBytes;
            this.timestamp = timestamp;
            this.type = type;
            this.isFavorite = isFavorite;
            this.isArchived = isArchived;
            this.isDeleted = isDeleted;
            this.inCamera = inCamera;
            this.hasLocal = hasLocal;
        }

        public boolean isVideo() {
            if (type == 2) return true;
            String lower = filename.toLowerCase(Locale.US);
            return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".3gp");
        }

        public boolean isSpaceConsuming() {
            return quotaChargedBytes > 0;
        }
    }

    public static class AlbumSummary {
        public final String title;
        public final String mediaKey;
        public final int itemCount;

        public AlbumSummary(String title, String mediaKey, int itemCount) {
            this.title = title != null && !title.isEmpty() ? title : "Untitled Album";
            this.mediaKey = mediaKey != null ? mediaKey : "";
            this.itemCount = itemCount;
        }
    }

    public static class AlbumActionResult {
        public final boolean success;
        public final int count;
        public final String albumTitle;
        public final String albumKey;
        public final String message;

        public AlbumActionResult(boolean success, int count, String albumTitle, String albumKey, String message) {
            this.success = success;
            this.count = count;
            this.albumTitle = albumTitle;
            this.albumKey = albumKey;
            this.message = message;
        }
    }

    public static class ScanResult {
        public final List<MediaItemSummary> allMediaItems = new ArrayList<>();
        public final List<MediaItemSummary> largestMediaItems = new ArrayList<>();
        public final List<MediaItemSummary> unorganizedItems = new ArrayList<>();
        public final Map<String, List<MediaItemSummary>> duplicateGroups = new HashMap<>();
        public final List<MediaItemSummary> videoItems = new ArrayList<>();
        public final List<MediaItemSummary> favoriteItems = new ArrayList<>();
        public final List<MediaItemSummary> archivedItems = new ArrayList<>();
        public final List<MediaItemSummary> trashedItems = new ArrayList<>();
        public final List<MediaItemSummary> cameraItems = new ArrayList<>();
        public final List<AlbumSummary> existingAlbums = new ArrayList<>();

        public long totalMediaCount = 0;
        public long totalEstimatedBytes = 0;
        public long totalQuotaChargedBytes = 0;
        public String scannedDatabase = "None";
    }

    private PhotosDatabaseScanner() {}

    /**
     * Executes a full library scan across local databases.
     */
    public static ScanResult scanLibrary(Context context) {
        ScanResult result = new ScanResult();
        if (context == null) return result;

        List<File> dbCandidates = findDatabaseFiles(context);
        if (dbCandidates.isEmpty()) {
            Logger.printInfo(() -> "PhotosDatabaseScanner: No database candidates found.");
            return result;
        }

        Logger.printInfo(() -> "PhotosDatabaseScanner: Discovered " + dbCandidates.size() + " database candidates.");

        for (File dbFile : dbCandidates) {
            if (!dbFile.exists() || !dbFile.canRead() || dbFile.length() <= 0) continue;

            Logger.printInfo(() -> "PhotosDatabaseScanner: Evaluating candidate: " + dbFile.getName() + " (" + dbFile.length() + " bytes)");

            sanitizeDatabase(context, dbFile.getName());

            SQLiteDatabase db = null;
            try {
                db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                if (db != null) {
                    boolean hasMedia = scanDatabase(db, dbFile.getName(), result);
                    if (hasMedia) {
                        result.scannedDatabase = dbFile.getName();
                        Logger.printInfo(() -> "PhotosDatabaseScanner: Successfully scanned " + dbFile.getName()
                                + " [Total: " + result.totalMediaCount
                                + ", Heavy: " + result.largestMediaItems.size()
                                + ", Unorg: " + result.unorganizedItems.size()
                                + ", Dups: " + result.duplicateGroups.size()
                                + ", Videos: " + result.videoItems.size()
                                + ", Favs: " + result.favoriteItems.size()
                                + ", Archived: " + result.archivedItems.size()
                                + ", Trash: " + result.trashedItems.size() + "]");
                        break; // Successfully scanned active database
                    }
                }
            } catch (Throwable t) {
                Logger.printException(() -> "PhotosDatabaseScanner: Error scanning " + dbFile.getName(), t);
            } finally {
                if (db != null && db.isOpen()) {
                    try { db.close(); } catch (Throwable ignored) {}
                }
            }
        }

        return result;
    }

    private static List<File> findDatabaseFiles(Context context) {
        List<File> candidates = new ArrayList<>();
        File fallbackUnset = null;

        try {
            File sample = context.getDatabasePath("gphotos0.db");
            File dbDir = sample != null ? sample.getParentFile() : null;
            if (dbDir != null && dbDir.isDirectory()) {
                File[] files = dbDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName();
                        if (name.endsWith(".db") && !name.contains("-journal") && !name.contains("-wal")) {
                            if (name.equals("gphotos-1.db")) {
                                fallbackUnset = f;
                            } else if (name.startsWith("gphotos")) {
                                candidates.add(f);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Logger.printException(() -> "PhotosDatabaseScanner: Error searching for databases", t);
        }

        // Sort candidates by file size DESCENDING so active account database with largest size comes first
        Collections.sort(candidates, (a, b) -> Long.compare(b.length(), a.length()));

        if (candidates.isEmpty() && fallbackUnset != null) {
            candidates.add(fallbackUnset);
        }

        return candidates;
    }

    private static boolean scanDatabase(SQLiteDatabase db, String dbName, ScanResult result) {
        Set<String> tables = getTableNames(db);
        if (tables.isEmpty()) return false;

        Logger.printInfo(() -> "PhotosDatabaseScanner: Tables in " + dbName + ": " + tables);

        // Check for media table
        if (!tables.contains("media")) {
            Logger.printInfo(() -> "PhotosDatabaseScanner: No media table found in " + dbName);
            return false;
        }

        long mediaCount = getRowCount(db, "media");
        if (mediaCount == 0) {
            Logger.printInfo(() -> "PhotosDatabaseScanner: media table in " + dbName + " has 0 rows, skipping.");
            return false;
        }

        result.totalMediaCount = mediaCount;
        Set<String> mediaCols = getColumnNames(db, "media");
        Set<String> localCols = tables.contains("local_media") ? getColumnNames(db, "local_media") : Collections.emptySet();
        Set<String> remoteCols = tables.contains("remote_media") ? getColumnNames(db, "remote_media") : Collections.emptySet();

        boolean hasLocalTable = tables.contains("local_media");
        boolean hasRemoteTable = tables.contains("remote_media");

        String mediaKeyExpr = mediaCols.contains("canonical_media_key") ? "m.canonical_media_key" :
                (hasRemoteTable && remoteCols.contains("media_key") ? "r.media_key" : "m.dedup_key");

        String filenameExpr = (hasLocalTable && hasRemoteTable)
                ? "COALESCE(l.filename, r.filename, 'Photo_' || m._id)"
                : (hasLocalTable ? "COALESCE(l.filename, 'Photo_' || m._id)"
                : (hasRemoteTable ? "COALESCE(r.filename, 'Photo_' || m._id)" : "'Photo_' || m._id"));

        String filepathExpr = hasLocalTable ? "COALESCE(l.filepath, '')" : "''";
        String sizeExpr = (hasLocalTable && hasRemoteTable)
                ? "COALESCE(l.size_bytes, r.size_bytes, 0)"
                : (hasLocalTable ? "COALESCE(l.size_bytes, 0)"
                : (hasRemoteTable ? "COALESCE(r.size_bytes, 0)" : "0"));

        String quotaExpr = hasRemoteTable && remoteCols.contains("quota_charged_bytes")
                ? "COALESCE(r.quota_charged_bytes, 0)" : "0";

        String query = "SELECT " +
                "m._id AS _id, " +
                "m.dedup_key AS dedup_key, " +
                mediaKeyExpr + " AS media_key, " +
                filenameExpr + " AS filename, " +
                filepathExpr + " AS filepath, " +
                sizeExpr + " AS size_bytes, " +
                quotaExpr + " AS quota_bytes, " +
                "m.type AS type, " +
                "COALESCE(m.is_favorite, 0) AS is_favorite, " +
                "COALESCE(m.is_archived, 0) AS is_archived, " +
                "COALESCE(m.is_deleted, 0) AS is_deleted, " +
                "COALESCE(m.in_camera_folder, 0) AS in_camera_folder, " +
                "COALESCE(m.has_local, 0) AS has_local, " +
                "COALESCE(m.capture_timestamp, m.utc_timestamp, 0) AS capture_timestamp " +
                "FROM media m " +
                (hasLocalTable ? "LEFT JOIN local_media l ON m.dedup_key = l.dedup_key " : "") +
                (hasRemoteTable ? "LEFT JOIN remote_media r ON m.dedup_key = r.dedup_key " : "");

        Map<String, MediaItemSummary> dedupToItemMap = new HashMap<>();

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, null);
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex("_id");
                int dedupIdx = cursor.getColumnIndex("dedup_key");
                int mediaKeyIdx = cursor.getColumnIndex("media_key");
                int fnIdx = cursor.getColumnIndex("filename");
                int fpIdx = cursor.getColumnIndex("filepath");
                int sizeIdx = cursor.getColumnIndex("size_bytes");
                int quotaIdx = cursor.getColumnIndex("quota_bytes");
                int typeIdx = cursor.getColumnIndex("type");
                int favIdx = cursor.getColumnIndex("is_favorite");
                int arcIdx = cursor.getColumnIndex("is_archived");
                int delIdx = cursor.getColumnIndex("is_deleted");
                int camIdx = cursor.getColumnIndex("in_camera_folder");
                int locIdx = cursor.getColumnIndex("has_local");
                int timeIdx = cursor.getColumnIndex("capture_timestamp");

                while (cursor.moveToNext()) {
                    long id = idIdx >= 0 ? cursor.getLong(idIdx) : 0L;
                    String dedup = dedupIdx >= 0 ? cursor.getString(dedupIdx) : "";
                    String mKey = mediaKeyIdx >= 0 ? cursor.getString(mediaKeyIdx) : "";
                    String fn = fnIdx >= 0 ? cursor.getString(fnIdx) : "";
                    String fp = fpIdx >= 0 ? cursor.getString(fpIdx) : "";
                    long sz = sizeIdx >= 0 ? cursor.getLong(sizeIdx) : 0L;
                    long quota = quotaIdx >= 0 ? cursor.getLong(quotaIdx) : 0L;
                    int type = typeIdx >= 0 ? cursor.getInt(typeIdx) : 1;
                    boolean fav = favIdx >= 0 && cursor.getInt(favIdx) == 1;
                    boolean arc = arcIdx >= 0 && cursor.getInt(arcIdx) == 1;
                    boolean del = delIdx >= 0 && cursor.getInt(delIdx) == 1;
                    boolean cam = camIdx >= 0 && cursor.getInt(camIdx) == 1;
                    boolean loc = locIdx >= 0 && cursor.getInt(locIdx) == 1;
                    long ts = timeIdx >= 0 ? cursor.getLong(timeIdx) : 0L;

                    if ((fn == null || fn.isEmpty()) && fp != null && !fp.isEmpty()) {
                        int slash = fp.lastIndexOf('/');
                        fn = slash >= 0 ? fp.substring(slash + 1) : fp;
                    }

                    MediaItemSummary item = new MediaItemSummary(id, mKey, dedup, fn, fp, sz, quota, ts,
                            type, fav, arc, del, cam, loc);

                    dedupToItemMap.put(dedup, item);

                    if (del) {
                        result.trashedItems.add(item);
                    } else {
                        result.allMediaItems.add(item);
                        result.totalEstimatedBytes += sz;
                        result.totalQuotaChargedBytes += quota;

                        if (fav) result.favoriteItems.add(item);
                        if (arc) result.archivedItems.add(item);
                        if (cam) result.cameraItems.add(item);
                        if (item.isVideo()) result.videoItems.add(item);
                        if (sz > 0) result.largestMediaItems.add(item);
                    }
                }
            }
        } catch (Throwable t) {
            Logger.printException(() -> "PhotosDatabaseScanner: Error querying master media view", t);
        } finally {
            if (cursor != null) cursor.close();
        }

        // Sort largest media items by file size descending
        Collections.sort(result.largestMediaItems, (a, b) -> Long.compare(b.sizeBytes, a.sizeBytes));

        // Scan for Album Membership to identify Unorganized Media
        Set<String> organizedKeys = new HashSet<>();
        if (tables.contains("item_collection_data")) {
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT DISTINCT canonical_id FROM item_collection_data WHERE canonical_id IS NOT NULL", null);
                if (c != null) {
                    while (c.moveToNext()) {
                        String k = c.getString(0);
                        if (k != null && !k.isEmpty()) organizedKeys.add(k);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (c != null) c.close();
            }
        }

        if (tables.contains("remote_media") && remoteCols.contains("collection_id")) {
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT DISTINCT dedup_key FROM remote_media WHERE collection_id IS NOT NULL AND collection_id != ''", null);
                if (c != null) {
                    while (c.moveToNext()) {
                        String k = c.getString(0);
                        if (k != null && !k.isEmpty()) organizedKeys.add(k);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (c != null) c.close();
            }
        }

        // Filter unorganized items
        for (MediaItemSummary item : result.allMediaItems) {
            if (!organizedKeys.contains(item.mediaKey) && !organizedKeys.contains(item.dedupKey)) {
                result.unorganizedItems.add(item);
            }
        }

        // Scan Duplicates & Burst media
        if (tables.contains("burst_media")) {
            Set<String> burstCols = getColumnNames(db, "burst_media");
            if (burstCols.contains("burst_group_id") && burstCols.contains("dedup_key")) {
                Cursor c = null;
                try {
                    c = db.rawQuery("SELECT dedup_key, burst_group_id FROM burst_media WHERE burst_group_id IS NOT NULL AND burst_group_id != ''", null);
                    if (c != null) {
                        Map<String, List<String>> burstMap = new HashMap<>();
                        while (c.moveToNext()) {
                            String dKey = c.getString(0);
                            String bGroup = c.getString(1);
                            if (dKey != null && bGroup != null) {
                                List<String> list = burstMap.get(bGroup);
                                if (list == null) {
                                    list = new ArrayList<>();
                                    burstMap.put(bGroup, list);
                                }
                                list.add(dKey);
                            }
                        }
                        for (Map.Entry<String, List<String>> entry : burstMap.entrySet()) {
                            if (entry.getValue().size() > 1) {
                                List<MediaItemSummary> g = new ArrayList<>();
                                for (String dk : entry.getValue()) {
                                    MediaItemSummary it = dedupToItemMap.get(dk);
                                    if (it != null) g.add(it);
                                }
                                if (g.size() > 1) {
                                    result.duplicateGroups.put("Burst: " + entry.getKey(), g);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (c != null) c.close();
                }
            }
        }

        // Also identify identical filename + file size duplicates
        Map<String, List<MediaItemSummary>> nameSizeMap = new HashMap<>();
        for (MediaItemSummary item : result.allMediaItems) {
            if (item.sizeBytes > 0 && !item.filename.isEmpty()) {
                String sig = item.filename.toLowerCase(Locale.US) + "_" + item.sizeBytes;
                List<MediaItemSummary> list = nameSizeMap.get(sig);
                if (list == null) {
                    list = new ArrayList<>();
                    nameSizeMap.put(sig, list);
                }
                list.add(item);
            }
        }
        for (Map.Entry<String, List<MediaItemSummary>> entry : nameSizeMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.duplicateGroups.put("Duplicate: " + entry.getValue().get(0).filename, entry.getValue());
            }
        }

        // Scan existing user albums
        Set<String> seenAlbumKeys = new HashSet<>();
        if (tables.contains("collections")) {
            Set<String> cols = getColumnNames(db, "collections");
            Logger.printInfo(() -> "PhotosDatabaseScanner: collections columns: " + cols);
            String titleExpr = cols.contains("title") && cols.contains("title_text")
                    ? "COALESCE(NULLIF(title, ''), NULLIF(title_text, ''))"
                    : (cols.contains("title_text") ? "NULLIF(title_text, '')"
                    : (cols.contains("title") ? "NULLIF(title, '')" : null));
            String keyCol = cols.contains("collection_media_key") ? "collection_media_key"
                    : (cols.contains("media_key") ? "media_key" : null);
            String countCol = cols.contains("total_items") ? "total_items"
                    : (cols.contains("total_item_count") ? "total_item_count" : "0");

            if (titleExpr != null && keyCol != null) {
                Cursor c = null;
                try {
                    String q = "SELECT " + titleExpr + " AS album_title, " + keyCol + ", " + countCol +
                            " FROM collections WHERE " + titleExpr + " IS NOT NULL ORDER BY album_title ASC";
                    c = db.rawQuery(q, null);
                    if (c != null) {
                        while (c.moveToNext()) {
                            String t = c.getString(0);
                            String k = c.getString(1);
                            int cnt = c.getInt(2);
                            if (t != null && !t.isEmpty() && k != null && seenAlbumKeys.add(k)) {
                                result.existingAlbums.add(new AlbumSummary(t, k, cnt));
                                Logger.printInfo(() -> "PhotosDatabaseScanner: Discovered collection album: " + t + " (" + k + ", " + cnt + " items)");
                            }
                        }
                    }
                } catch (Throwable t) {
                    Logger.printException(() -> "PhotosDatabaseScanner: Error querying collections", t);
                } finally {
                    if (c != null) c.close();
                }
            }
        }

        if (tables.contains("envelopes")) {
            Set<String> cols = getColumnNames(db, "envelopes");
            Logger.printInfo(() -> "PhotosDatabaseScanner: envelopes columns: " + cols);
            String titleExpr = cols.contains("title") && cols.contains("title_text")
                    ? "COALESCE(NULLIF(title, ''), NULLIF(title_text, ''))"
                    : (cols.contains("title") ? "NULLIF(title, '')" : null);
            String keyCol = cols.contains("media_key") ? "media_key"
                    : (cols.contains("envelope_media_key") ? "envelope_media_key" : null);
            String countCol = cols.contains("total_item_count") ? "total_item_count"
                    : (cols.contains("total_items") ? "total_items" : "0");

            if (titleExpr != null && keyCol != null) {
                Cursor c = null;
                try {
                    String q = "SELECT " + titleExpr + " AS album_title, " + keyCol + ", " + countCol +
                            " FROM envelopes WHERE " + titleExpr + " IS NOT NULL ORDER BY album_title ASC";
                    c = db.rawQuery(q, null);
                    if (c != null) {
                        while (c.moveToNext()) {
                            String t = c.getString(0);
                            String k = c.getString(1);
                            int cnt = c.getInt(2);
                            if (t != null && !t.isEmpty() && k != null && seenAlbumKeys.add(k)) {
                                result.existingAlbums.add(new AlbumSummary(t, k, cnt));
                                Logger.printInfo(() -> "PhotosDatabaseScanner: Discovered envelope album: " + t + " (" + k + ", " + cnt + " items)");
                            }
                        }
                    }
                } catch (Throwable t) {
                    Logger.printException(() -> "PhotosDatabaseScanner: Error querying envelopes", t);
                } finally {
                    if (c != null) c.close();
                }
            }
        }

        return true;
    }

    private static long getRowCount(SQLiteDatabase db, String table) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT count(*) FROM " + table, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return 0L;
    }

    private static Set<String> getTableNames(SQLiteDatabase db) {
        Set<String> tables = new HashSet<>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type IN ('table', 'view')", null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    tables.add(cursor.getString(0));
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return tables;
    }

    private static Set<String> getColumnNames(SQLiteDatabase db, String tableName) {
        Set<String> columns = new HashSet<>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            if (cursor != null) {
                int nameIdx = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0) columns.add(cursor.getString(nameIdx));
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return columns;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Native Database Mutation Operations
    // ─────────────────────────────────────────────────────────────────────────

    public static SQLiteDatabase openWritableDatabase(Context context, String dbName) {
        if (context == null) return null;
        File dbFile = null;
        if (dbName != null && !dbName.isEmpty() && !dbName.equals("None")) {
            File candidate = context.getDatabasePath(dbName);
            if (candidate != null && candidate.exists()) {
                dbFile = candidate;
            }
        }
        if (dbFile == null) {
            List<File> candidates = findDatabaseFiles(context);
            if (!candidates.isEmpty()) {
                dbFile = candidates.get(0);
            }
        }
        final File finalTarget = dbFile;
        if (finalTarget != null && finalTarget.exists()) {
            try {
                return SQLiteDatabase.openDatabase(finalTarget.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
            } catch (Throwable t) {
                Logger.printException(() -> "PhotosDatabaseScanner: Failed to open writable DB " + finalTarget.getName(), t);
            }
        }
        return null;
    }

    public static void notifyPhotosContentProviders(Context context) {
        if (context == null) return;
        try {
            android.content.ContentResolver cr = context.getContentResolver();
            cr.notifyChange(Uri.parse("content://com.google.android.apps.photos.contentprovider"), null);
            cr.notifyChange(Uri.parse("content://com.google.android.apps.photos.contentprovider/collections"), null);
            cr.notifyChange(Uri.parse("content://com.google.android.apps.photos.contentprovider/media"), null);
            cr.notifyChange(Uri.parse("content://media"), null);
        } catch (Throwable t) {
            Logger.printException(() -> "PhotosDatabaseScanner: Error notifying content providers", t);
        }
    }

    public static void sanitizeDatabase(Context context, String dbName) {
        if (context == null) return;
        SQLiteDatabase db = openWritableDatabase(context, dbName);
        if (db == null) return;
        try {
            Set<String> tables = getTableNames(db);
            if (tables.contains("collections")) {
                int purgedCol = db.delete("collections", "protobuf IS NULL", null);
                if (purgedCol > 0) {
                    Logger.printInfo(() -> "PhotosDatabaseScanner: Purged " + purgedCol + " corrupt collections with null protobuf.");
                }
            }
            if (tables.contains("item_collection_data")) {
                int purgedIcd = db.delete("item_collection_data", "protobuf IS NULL", null);
                if (purgedIcd > 0) {
                    Logger.printInfo(() -> "PhotosDatabaseScanner: Purged " + purgedIcd + " corrupt item_collection_data rows with null protobuf.");
                }
            }
        } catch (Throwable t) {
            Logger.printException(() -> "PhotosDatabaseScanner: Error in sanitizeDatabase", t);
        } finally {
            try { db.close(); } catch (Throwable ignored) {}
        }
    }

    public static AlbumActionResult addItemsToAlbum(
            Context context,
            String dbName,
            List<MediaItemSummary> items,
            String targetAlbumMediaKey,
            String newAlbumTitle,
            java.util.function.Consumer<String> progressLog) {

        if (items == null || items.isEmpty()) {
            return new AlbumActionResult(false, 0, "", "", "No items selected to add.");
        }

        SQLiteDatabase db = openWritableDatabase(context, dbName);
        if (db == null) {
            return new AlbumActionResult(false, 0, "", "", "Unable to open Google Photos database with write permissions.");
        }

        try {
            Set<String> tables = getTableNames(db);
            boolean hasCollections = tables.contains("collections");
            boolean hasEnvelopes = tables.contains("envelopes");
            boolean hasIcd = tables.contains("item_collection_data");

            if (!hasIcd) {
                return new AlbumActionResult(false, 0, "", "", "Database missing item_collection_data table.");
            }

            Set<String> icdCols = getColumnNames(db, "item_collection_data");
            Set<String> colCols = hasCollections ? getColumnNames(db, "collections") : Collections.emptySet();
            Set<String> envCols = hasEnvelopes ? getColumnNames(db, "envelopes") : Collections.emptySet();

            String finalAlbumKey = targetAlbumMediaKey;
            String finalAlbumTitle = newAlbumTitle != null && !newAlbumTitle.trim().isEmpty() ? newAlbumTitle.trim() : "Photos Album";
            byte[] emptyProtoBlob = new byte[0];

            // If creating a new album
            if (finalAlbumKey == null || finalAlbumKey.isEmpty()) {
                finalAlbumKey = "local_album_" + System.currentTimeMillis();
                if (hasCollections) {
                    ContentValues cv = new ContentValues();
                    if (colCols.contains("collection_media_key")) cv.put("collection_media_key", finalAlbumKey);
                    if (colCols.contains("title")) cv.put("title", finalAlbumTitle);
                    if (colCols.contains("title_text")) cv.put("title_text", finalAlbumTitle);
                    if (colCols.contains("type")) cv.put("type", 1);
                    if (colCols.contains("display_mode")) cv.put("display_mode", 1);
                    if (colCols.contains("total_items")) cv.put("total_items", items.size());
                    if (colCols.contains("last_activity_time_ms")) cv.put("last_activity_time_ms", System.currentTimeMillis());

                    // Crucial: Protobuf must NEVER be null, otherwise Google Photos crashes when parsing collections
                    if (colCols.contains("protobuf")) cv.put("protobuf", emptyProtoBlob);
                    if (colCols.contains("pristine_protobuf")) cv.put("pristine_protobuf", emptyProtoBlob);

                    String coverKey = !items.isEmpty() ? (!items.get(0).mediaKey.isEmpty() ? items.get(0).mediaKey : items.get(0).dedupKey) : "";
                    if (colCols.contains("cover_item_media_key")) cv.put("cover_item_media_key", coverKey);

                    long minTime = Long.MAX_VALUE;
                    long maxTime = Long.MIN_VALUE;
                    for (MediaItemSummary it : items) {
                        if (it.timestamp > 0) {
                            if (it.timestamp < minTime) minTime = it.timestamp;
                            if (it.timestamp > maxTime) maxTime = it.timestamp;
                        }
                    }
                    if (minTime != Long.MAX_VALUE && colCols.contains("start")) cv.put("start", minTime);
                    if (maxTime != Long.MIN_VALUE && colCols.contains("end")) cv.put("end", maxTime);

                    long colRow = db.insertWithOnConflict("collections", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    if (colRow == -1) {
                        return new AlbumActionResult(false, 0, finalAlbumTitle, finalAlbumKey, "Failed to insert album record into collections.");
                    }
                }
            }

            // Insert items into item_collection_data
            db.beginTransaction();
            int addedCount = 0;
            try {
                for (int i = 0; i < items.size(); i++) {
                    MediaItemSummary item = items.get(i);
                    String canonicalKey = !item.mediaKey.isEmpty() ? item.mediaKey : item.dedupKey;

                    ContentValues cv = new ContentValues();
                    if (icdCols.contains("icd_id")) cv.put("icd_id", finalAlbumKey + "_" + item.id);
                    if (icdCols.contains("collection_id")) cv.put("collection_id", finalAlbumKey);
                    if (icdCols.contains("canonical_id")) cv.put("canonical_id", canonicalKey);
                    if (icdCols.contains("provenance")) cv.put("provenance", 1);
                    if (icdCols.contains("version")) cv.put("version", 1);

                    // Crucial: Protobuf must NEVER be null, otherwise Google Photos crashes when rendering items
                    if (icdCols.contains("protobuf")) cv.put("protobuf", emptyProtoBlob);

                    long rowId = db.insertWithOnConflict("item_collection_data", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    if (rowId != -1) {
                        addedCount++;
                    }

                    if (progressLog != null && (i == 0 || (i + 1) % 25 == 0 || i == items.size() - 1)) {
                        progressLog.accept(String.format(Locale.US, "Added %d/%d items to album...", (i + 1), items.size()));
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            // Update album total item counts in collections
            if (hasCollections) {
                try {
                    db.execSQL("UPDATE collections SET total_items = (SELECT count(1) FROM item_collection_data WHERE collection_id = ?), last_activity_time_ms = ? WHERE collection_media_key = ?",
                            new Object[]{finalAlbumKey, System.currentTimeMillis(), finalAlbumKey});
                } catch (Throwable t) {
                    Logger.printException(() -> "Error updating collections count", t);
                }
            }
            if (hasEnvelopes && envCols.contains("total_item_count")) {
                try {
                    String keyCol = envCols.contains("media_key") ? "media_key" : "envelope_media_key";
                    db.execSQL("UPDATE envelopes SET total_item_count = (SELECT count(1) FROM item_collection_data WHERE collection_id = ?) WHERE " + keyCol + " = ?",
                            new Object[]{finalAlbumKey, finalAlbumKey});
                } catch (Throwable t) {
                    Logger.printException(() -> "Error updating envelopes count", t);
                }
            }

            // Notify content providers
            notifyPhotosContentProviders(context);

            if (progressLog != null) {
                progressLog.accept(String.format(Locale.US, "✔ Successfully added %d items to \"%s\"!", addedCount, finalAlbumTitle));
                progressLog.accept("Notified Google Photos ContentProvider. UI updated.");
            }

            return new AlbumActionResult(true, addedCount, finalAlbumTitle, finalAlbumKey, "Success");

        } catch (Throwable t) {
            Logger.printException(() -> "PhotosDatabaseScanner: Error in addItemsToAlbum", t);
            return new AlbumActionResult(false, 0, "", "", "Exception: " + t.getMessage());
        } finally {
            try { db.close(); } catch (Throwable ignored) {}
        }
    }

    public static int batchTrashItems(
            Context context,
            String dbName,
            List<MediaItemSummary> items,
            java.util.function.Consumer<String> progressLog) {

        if (items == null || items.isEmpty()) return 0;

        SQLiteDatabase db = openWritableDatabase(context, dbName);
        if (db == null) {
            if (progressLog != null) progressLog.accept("Error: Unable to open database for writing.");
            return 0;
        }

        try {
            Set<String> tables = getTableNames(db);
            Set<String> mediaCols = tables.contains("media") ? getColumnNames(db, "media") : Collections.emptySet();
            Set<String> localCols = tables.contains("local_media") ? getColumnNames(db, "local_media") : Collections.emptySet();
            Set<String> remoteCols = tables.contains("remote_media") ? getColumnNames(db, "remote_media") : Collections.emptySet();

            boolean hasMediaDel = mediaCols.contains("is_deleted");
            boolean hasLocalDel = localCols.contains("is_deleted");
            boolean hasRemoteDel = remoteCols.contains("is_deleted");

            if (!hasMediaDel) {
                if (progressLog != null) progressLog.accept("Error: 'media' table does not have 'is_deleted' column.");
                return 0;
            }

            db.beginTransaction();
            int count = 0;
            try {
                for (int i = 0; i < items.size(); i++) {
                    MediaItemSummary item = items.get(i);

                    ContentValues cv = new ContentValues();
                    cv.put("is_deleted", 1);
                    int updated = db.update("media", cv, "_id = ?", new String[]{String.valueOf(item.id)});
                    if (updated > 0) count++;

                    if (hasLocalDel && !item.dedupKey.isEmpty()) {
                        db.update("local_media", cv, "dedup_key = ?", new String[]{item.dedupKey});
                    }
                    if (hasRemoteDel && !item.dedupKey.isEmpty()) {
                        db.update("remote_media", cv, "dedup_key = ?", new String[]{item.dedupKey});
                    }

                    if (progressLog != null && (i == 0 || (i + 1) % 25 == 0 || i == items.size() - 1)) {
                        progressLog.accept(String.format(Locale.US, "Moving to Trash: %d/%d...", (i + 1), items.size()));
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            notifyPhotosContentProviders(context);

            if (progressLog != null) {
                progressLog.accept(String.format(Locale.US, "✔ Successfully moved %d items to Trash!", count));
                progressLog.accept("Notified Google Photos ContentProvider. Library updated.");
            }
            return count;

        } catch (Throwable t) {
            Logger.printException(() -> "PhotosDatabaseScanner: Error in batchTrashItems", t);
            if (progressLog != null) progressLog.accept("Error moving items to Trash: " + t.getMessage());
            return 0;
        } finally {
            try { db.close(); } catch (Throwable ignored) {}
        }
    }

    public static int batchArchiveItems(
            Context context,
            String dbName,
            List<MediaItemSummary> items,
            boolean archive,
            java.util.function.Consumer<String> progressLog) {

        if (items == null || items.isEmpty()) return 0;

        SQLiteDatabase db = openWritableDatabase(context, dbName);
        if (db == null) {
            if (progressLog != null) progressLog.accept("Error: Unable to open database for writing.");
            return 0;
        }

        try {
            Set<String> tables = getTableNames(db);
            Set<String> mediaCols = tables.contains("media") ? getColumnNames(db, "media") : Collections.emptySet();

            if (!mediaCols.contains("is_archived")) {
                if (progressLog != null) progressLog.accept("Error: 'media' table does not have 'is_archived' column.");
                return 0;
            }

            db.beginTransaction();
            int count = 0;
            try {
                for (int i = 0; i < items.size(); i++) {
                    MediaItemSummary item = items.get(i);
                    ContentValues cv = new ContentValues();
                    cv.put("is_archived", archive ? 1 : 0);
                    int updated = db.update("media", cv, "_id = ?", new String[]{String.valueOf(item.id)});
                    if (updated > 0) count++;

                    if (progressLog != null && (i == 0 || (i + 1) % 25 == 0 || i == items.size() - 1)) {
                        progressLog.accept(String.format(Locale.US, (archive ? "Archiving: " : "Unarchiving: ") + "%d/%d...", (i + 1), items.size()));
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            notifyPhotosContentProviders(context);

            if (progressLog != null) {
                progressLog.accept(String.format(Locale.US, "✔ Successfully %s %d items!", archive ? "archived" : "unarchived", count));
                progressLog.accept("Notified Google Photos ContentProvider. Library updated.");
            }
            return count;

        } catch (Throwable t) {
            Logger.printException(() -> "PhotosDatabaseScanner: Error in batchArchiveItems", t);
            if (progressLog != null) progressLog.accept("Error updating archive status: " + t.getMessage());
            return 0;
        } finally {
            try { db.close(); } catch (Throwable ignored) {}
        }
    }

    public static int batchFavoriteItems(
            Context context,
            String dbName,
            List<MediaItemSummary> items,
            boolean favorite,
            java.util.function.Consumer<String> progressLog) {

        if (items == null || items.isEmpty()) return 0;

        SQLiteDatabase db = openWritableDatabase(context, dbName);
        if (db == null) {
            if (progressLog != null) progressLog.accept("Error: Unable to open database for writing.");
            return 0;
        }

        try {
            Set<String> tables = getTableNames(db);
            Set<String> mediaCols = tables.contains("media") ? getColumnNames(db, "media") : Collections.emptySet();

            if (!mediaCols.contains("is_favorite")) {
                if (progressLog != null) progressLog.accept("Error: 'media' table does not have 'is_favorite' column.");
                return 0;
            }

            db.beginTransaction();
            int count = 0;
            try {
                for (int i = 0; i < items.size(); i++) {
                    MediaItemSummary item = items.get(i);
                    ContentValues cv = new ContentValues();
                    cv.put("is_favorite", favorite ? 1 : 0);
                    int updated = db.update("media", cv, "_id = ?", new String[]{String.valueOf(item.id)});
                    if (updated > 0) count++;

                    if (progressLog != null && (i == 0 || (i + 1) % 25 == 0 || i == items.size() - 1)) {
                        progressLog.accept(String.format(Locale.US, (favorite ? "Favoriting: " : "Unfavoriting: ") + "%d/%d...", (i + 1), items.size()));
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            notifyPhotosContentProviders(context);

            if (progressLog != null) {
                progressLog.accept(String.format(Locale.US, "✔ Successfully %s %d items!", favorite ? "favorited" : "unfavorited", count));
                progressLog.accept("Notified Google Photos ContentProvider. Library updated.");
            }
            return count;

        } catch (Throwable t) {
            Logger.printException(() -> "PhotosDatabaseScanner: Error in batchFavoriteItems", t);
            if (progressLog != null) progressLog.accept("Error updating favorite status: " + t.getMessage());
            return 0;
        } finally {
            try { db.close(); } catch (Throwable ignored) {}
        }
    }
}
