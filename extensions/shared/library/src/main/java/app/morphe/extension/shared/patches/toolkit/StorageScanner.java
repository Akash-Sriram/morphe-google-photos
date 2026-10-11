package app.morphe.extension.shared.patches.toolkit;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;

/**
 * Native engine that inspects Google Photos' internal SQLite databases directly.
 * Full 1:1 data model matching Google Photos Toolkit (GPTK v3.3.0).
 * Queries remote_media, shared_media, envelopes, collections, and remote_locked_media.
 */
public class StorageScanner {

    public enum SourceType {
        LIBRARY("Library"),
        SEARCH("Search"),
        ALBUMS("Albums"),
        SHARED("Shared"),
        FAVORITES("Favorites"),
        TRASH("Trash"),
        LOCKED("Locked Folder");

        public final String displayName;
        SourceType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum SpaceFilter {
        ANY("Any"),
        CONSUMING("Consuming"),
        NON_CONSUMING("Non-Consuming");

        public final String label;
        SpaceFilter(String label) {
            this.label = label;
        }
    }

    public static class AlbumInfo {
        public final String mediaKey;
        public final String title;
        public final int itemCount;
        public final boolean isShared;

        public AlbumInfo(String mediaKey, String title, int itemCount, boolean isShared) {
            this.mediaKey = mediaKey;
            this.title = title != null && !title.isEmpty() ? title : "Untitled Album";
            this.itemCount = itemCount;
            this.isShared = isShared;
        }

        public AlbumInfo(String mediaKey, String title, int itemCount) {
            this(mediaKey, title, itemCount, false);
        }

        @Override
        public String toString() {
            return title + " (" + itemCount + ")";
        }
    }

    /**
     * 1:1 criteria matching every filter from google_photos_toolkit.user.js (Lines 121-289)
     */
    public static class FilterCriteria {
        public SourceType source = SourceType.LIBRARY;

        // 1. Select Albums (Include)
        public List<String> albumsInclude = new ArrayList<>();

        // 2. Search Query
        public String searchQuery = "";

        // 3. Exclude Albums
        public List<String> albumsExclude = new ArrayList<>();

        // 4. Date Interval
        public long lowerBoundaryDate = 0;   // epoch ms
        public long higherBoundaryDate = 0;  // epoch ms
        public String intervalType = "include"; // "include" | "exclude"
        public String dateType = "taken";       // "taken" | "uploaded"

        // 5. Filename Regex
        public String fileNameRegex = "";
        public String fileNameMatchType = "include"; // "include" | "exclude"

        // 6. Description Regex
        public String descriptionRegex = "";
        public String descriptionMatchType = "include"; // "include" | "exclude"

        // 7. Space
        public SpaceFilter space = SpaceFilter.ANY;

        // 8. Similarity
        public float similarityThreshold = 0f; // 0 = disabled, 0.01 - 1.0
        public int imageHeight = 16;           // pixels for hashing (default 16)

        // 9. Size (bytes)
        public long lowerBoundarySize = 0;
        public long higherBoundarySize = 0;

        // 10. Resolution (pixels)
        public int minWidth = 0;
        public int maxWidth = 0;
        public int minHeight = 0;
        public int maxHeight = 0;

        // 11. Duration (seconds)
        public float minDuration = 0;
        public float maxDuration = 0;

        // 12. Quality (null=any, "original", "storage-saver")
        public String quality = null;

        // 13. Type (null=any, "image", "video", "live")
        public String mediaType = null;

        // 14. Upload Status (null=any, "full", "partial")
        public String uploadStatus = null;

        // 15. Archived (null=any, true, false)
        public Boolean archived = null;

        // 16. Ownership (null=any, true, false)
        public Boolean owned = null;

        // 17. Location & Bounding Box
        public Boolean hasLocation = null;
        public Double boundSouth = null;
        public Double boundWest = null;
        public Double boundNorth = null;
        public Double boundEast = null;

        // 18. Favorite (null=any, true, false)
        public Boolean favorite = null;

        // 19. Exclude Shared Links
        public boolean excludeShared = false;

        // 20. Exclude Favorites
        public boolean excludeFavorites = false;

        // 21. Sort by size
        public boolean sortBySize = true;

        // Limit (matching Google Photos max album limit)
        public int limit = 20000;
    }

    public static class AccountInfo {
        public final int accountId;
        public final String accountName;
        public final String dbPath;
        public final String gaiaId;

        public AccountInfo(int accountId, String accountName, String dbPath, String gaiaId) {
            this.accountId = accountId;
            this.accountName = accountName;
            this.dbPath = dbPath;
            this.gaiaId = gaiaId != null ? gaiaId : "";
        }

        public AccountInfo(int accountId, String accountName, String dbPath) {
            this(accountId, accountName, dbPath, "");
        }

        @Override
        public String toString() {
            return accountName;
        }
    }

    public static class MediaItem {
        public final String dedupKey;
        public final String mediaKey;
        public final String filename;
        public final long sizeBytes;
        public final long quotaBytes;
        public final long captureTimestamp;
        public final String remoteUrl;
        public final long trashTimestamp;
        public final int width;
        public final int height;
        public final long duration;
        public final boolean isArchived;
        public final boolean isFavorite;
        public final boolean isMicroVideo;
        public final String mimeType;
        public final double latitude;
        public final double longitude;
        public final String caption;
        public long perceptualHash = 0;
        public int clusterId = -1;

        public MediaItem(String dedupKey, String mediaKey, String filename, long sizeBytes,
                         long quotaBytes, long captureTimestamp, String remoteUrl,
                         long trashTimestamp, int width, int height, long duration,
                         boolean isArchived, boolean isFavorite, boolean isMicroVideo,
                         String mimeType, double latitude, double longitude, String caption) {
            this.dedupKey = dedupKey;
            this.mediaKey = mediaKey;
            this.filename = filename != null ? filename : "Unknown";
            this.sizeBytes = sizeBytes;
            this.quotaBytes = quotaBytes;
            this.captureTimestamp = captureTimestamp;
            this.remoteUrl = remoteUrl;
            this.trashTimestamp = trashTimestamp;
            this.width = width;
            this.height = height;
            this.duration = duration;
            this.isArchived = isArchived;
            this.isFavorite = isFavorite;
            this.isMicroVideo = isMicroVideo;
            this.mimeType = mimeType != null ? mimeType : "";
            this.latitude = latitude;
            this.longitude = longitude;
            this.caption = caption != null ? caption : "";
        }

        public boolean isTrashed() {
            return trashTimestamp > 0;
        }

        public String getFormattedSize() {
            return formatBytes(sizeBytes);
        }

        public String getFormattedQuota() {
            return formatBytes(quotaBytes);
        }
    }

    public static List<AccountInfo> getAvailableAccounts(Context context) {
        List<AccountInfo> accounts = new ArrayList<>();
        try {
            SharedPreferences sp = context.getSharedPreferences("accounts", Context.MODE_PRIVATE);
            for (int i = 0; i < 10; i++) {
                String accName = sp.getString(i + ".account_name", null);
                String gaiaId = sp.getString(i + ".gaia_id", "");
                File db = context.getDatabasePath("gphotos" + i + ".db");
                if (accName != null && db.exists()) {
                    accounts.add(new AccountInfo(i, accName, db.getAbsolutePath(), gaiaId));
                }
            }
        } catch (Throwable t) {
            Logger.printException(() -> "Error discovering accounts", t);
        }

        if (accounts.isEmpty()) {
            File dbDir = context.getDatabasePath("gphotos1.db").getParentFile();
            if (dbDir != null && dbDir.exists()) {
                File[] dbs = dbDir.listFiles((dir, name) -> name.matches("gphotos\\d+\\.db"));
                if (dbs != null) {
                    for (File f : dbs) {
                        String name = f.getName().replace("gphotos", "").replace(".db", "");
                        try {
                            int id = Integer.parseInt(name);
                            accounts.add(new AccountInfo(id, "Account " + id, f.getAbsolutePath(), ""));
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
        return accounts;
    }

    public static AccountInfo getActiveAccount(Context context) {
        List<AccountInfo> accounts = getAvailableAccounts(context);
        if (accounts.isEmpty()) return null;

        // 1. Check native Google Photos accounts.xml "key.active-account-key" (exact active account in app)
        try {
            SharedPreferences sp = context.getSharedPreferences("accounts", Context.MODE_PRIVATE);
            int activeKey = sp.getInt("key.active-account-key", -1);
            if (activeKey >= 0) {
                for (AccountInfo a : accounts) {
                    if (a.accountId == activeKey) return a;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Check photos.backup.backup_prefs "backup_prefs_account_id"
        try {
            SharedPreferences backupSp = context.getSharedPreferences("photos.backup.backup_prefs", Context.MODE_PRIVATE);
            int activeId = backupSp.getInt("backup_prefs_account_id", -1);
            if (activeId >= 0) {
                for (AccountInfo a : accounts) {
                    if (a.accountId == activeId) return a;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Check Morphe account prefs (if user previously manually switched inside Morphe)
        try {
            SharedPreferences morphePrefs = context.getSharedPreferences("morphe_account_prefs", Context.MODE_PRIVATE);
            int selectedIdx = morphePrefs.getInt("selected_account_index", -1);
            if (selectedIdx >= 0) {
                for (AccountInfo a : accounts) {
                    if (a.accountId == selectedIdx) return a;
                }
            }
        } catch (Throwable ignored) {}

        return accounts.get(0);
    }

    public static List<AlbumInfo> getAvailableAlbums(AccountInfo account) {
        List<AlbumInfo> albums = new ArrayList<>();
        if (account == null) return albums;
        File dbFile = new File(account.dbPath);
        if (!dbFile.exists()) return albums;

        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            if (hasTable(db, "collections") || hasTable(db, "envelopes")) {
                String sql = "SELECT c.collection_media_key, coalesce(c.title_text, c.title, e.title) as album_title, c.total_items, (c.audience > 1) as is_shared " +
                        "FROM collections c LEFT JOIN envelopes e ON c.associated_envelope_media_key = e.media_key " +
                        "WHERE album_title IS NOT NULL AND album_title != '' " +
                        "AND (c.is_hidden = 0 OR c.is_hidden IS NULL) AND (c.is_soft_deleted = 0 OR c.is_soft_deleted IS NULL) " +
                        "UNION " +
                        "SELECT e.media_key, e.title, e.total_item_count, 1 as is_shared " +
                        "FROM envelopes e WHERE e.title IS NOT NULL AND e.title != '' " +
                        "AND (e.is_hidden = 0 OR e.is_hidden IS NULL) " +
                        "AND e.media_key NOT IN (SELECT coalesce(associated_envelope_media_key, '') FROM collections) " +
                        "ORDER BY album_title COLLATE NOCASE ASC";
                try (Cursor c = db.rawQuery(sql, null)) {
                    while (c != null && c.moveToNext()) {
                        String mKey = c.getString(0);
                        String title = c.getString(1);
                        int count = c.getInt(2);
                        boolean isShared = c.getInt(3) == 1;
                        albums.add(new AlbumInfo(mKey, title, count, isShared));
                    }
                }
            }
        } catch (Throwable t) {
            Logger.printException(() -> "Error fetching albums", t);
        } finally {
            if (db != null) {
                try { db.close(); } catch (Throwable ignored) {}
            }
        }
        return albums;
    }

    public static List<MediaItem> queryMedia(AccountInfo account, FilterCriteria crit) {
        List<MediaItem> items = new ArrayList<>();
        if (account == null || crit == null) return items;
        File dbFile = new File(account.dbPath);
        if (!dbFile.exists()) return items;

        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            String targetTable = "remote_media";
            if (crit.source == SourceType.SHARED || crit.source == SourceType.ALBUMS) {
                targetTable = "shared_media";
            } else if (crit.source == SourceType.LOCKED) {
                targetTable = "remote_locked_media";
            }

            if (!hasTable(db, targetTable)) return items;

            StringBuilder where = new StringBuilder("1=1");
            List<String> args = new ArrayList<>();

            // 1. Source constraint
            switch (crit.source) {
                case LIBRARY:
                    where.append(" AND (trash_timestamp IS NULL OR trash_timestamp = 0)");
                    if (crit.excludeFavorites) {
                        where.append(" AND (is_favorite IS NULL OR is_favorite = 0)");
                    }
                    break;
                case SEARCH:
                    where.append(" AND (trash_timestamp IS NULL OR trash_timestamp = 0)");
                    if (crit.searchQuery != null && !crit.searchQuery.trim().isEmpty()) {
                        String q = "%" + crit.searchQuery.trim() + "%";
                        where.append(" AND (filename LIKE ? OR caption LIKE ?)");
                        args.add(q);
                        args.add(q);
                    }
                    break;
                case ALBUMS:
                    if (hasColumn(db, targetTable, "trash_timestamp")) {
                        where.append(" AND (trash_timestamp IS NULL OR trash_timestamp = 0)");
                    }
                    if (!crit.albumsInclude.isEmpty()) {
                        where.append(" AND collection_id IN (");
                        for (int i = 0; i < crit.albumsInclude.size(); i++) {
                            if (i > 0) where.append(",");
                            where.append("?");
                            args.add(crit.albumsInclude.get(i));
                        }
                        where.append(")");
                    } else {
                        // In Google Photos Toolkit, selecting Albums source requires picking album(s)
                        return items;
                    }
                    break;
                case SHARED:
                    break;
                case FAVORITES:
                    where.append(" AND is_favorite = 1 AND (trash_timestamp IS NULL OR trash_timestamp = 0)");
                    break;
                case TRASH:
                    where.append(" AND trash_timestamp > 0");
                    break;
                case LOCKED:
                    break;
            }

            // Exclude albums
            if (!crit.albumsExclude.isEmpty()) {
                where.append(" AND (collection_id IS NULL OR collection_id NOT IN (");
                for (int i = 0; i < crit.albumsExclude.size(); i++) {
                    if (i > 0) where.append(",");
                    where.append("?");
                    args.add(crit.albumsExclude.get(i));
                }
                where.append("))");
                if ("remote_media".equals(targetTable) && hasTable(db, "shared_media")) {
                    where.append(" AND dedup_key NOT IN (SELECT dedup_key FROM shared_media WHERE collection_id IN (");
                    for (int i = 0; i < crit.albumsExclude.size(); i++) {
                        if (i > 0) where.append(",");
                        where.append("?");
                        args.add(crit.albumsExclude.get(i));
                    }
                    where.append("))");
                }
            }

            // Space filter
            if (crit.space == SpaceFilter.CONSUMING) {
                where.append(" AND quota_charged_bytes > 0");
            } else if (crit.space == SpaceFilter.NON_CONSUMING) {
                where.append(" AND (quota_charged_bytes = 0 OR quota_charged_bytes IS NULL)");
            }

            // Date interval
            String dateCol = "uploaded".equals(crit.dateType) && hasColumn(db, targetTable, "utc_timestamp")
                    ? "utc_timestamp" : "capture_timestamp";

            if (crit.lowerBoundaryDate > 0 && crit.higherBoundaryDate > 0) {
                if ("include".equals(crit.intervalType)) {
                    where.append(" AND ").append(dateCol).append(" >= ? AND ").append(dateCol).append(" <= ?");
                    args.add(String.valueOf(crit.lowerBoundaryDate));
                    args.add(String.valueOf(crit.higherBoundaryDate));
                } else {
                    where.append(" AND NOT (").append(dateCol).append(" >= ? AND ").append(dateCol).append(" <= ?)");
                    args.add(String.valueOf(crit.lowerBoundaryDate));
                    args.add(String.valueOf(crit.higherBoundaryDate));
                }
            } else if (crit.lowerBoundaryDate > 0) {
                if ("include".equals(crit.intervalType)) {
                    where.append(" AND ").append(dateCol).append(" >= ?");
                } else {
                    where.append(" AND ").append(dateCol).append(" < ?");
                }
                args.add(String.valueOf(crit.lowerBoundaryDate));
            } else if (crit.higherBoundaryDate > 0) {
                if ("include".equals(crit.intervalType)) {
                    where.append(" AND ").append(dateCol).append(" <= ?");
                } else {
                    where.append(" AND ").append(dateCol).append(" > ?");
                }
                args.add(String.valueOf(crit.higherBoundaryDate));
            }

            // Size bounds
            if (crit.lowerBoundarySize > 0) {
                where.append(" AND size_bytes >= ?");
                args.add(String.valueOf(crit.lowerBoundarySize));
            }
            if (crit.higherBoundarySize > 0) {
                where.append(" AND size_bytes <= ?");
                args.add(String.valueOf(crit.higherBoundarySize));
            }

            // Resolution
            if (crit.minWidth > 0) {
                where.append(" AND width >= ?");
                args.add(String.valueOf(crit.minWidth));
            }
            if (crit.maxWidth > 0) {
                where.append(" AND width <= ?");
                args.add(String.valueOf(crit.maxWidth));
            }
            if (crit.minHeight > 0) {
                where.append(" AND height >= ?");
                args.add(String.valueOf(crit.minHeight));
            }
            if (crit.maxHeight > 0) {
                where.append(" AND height <= ?");
                args.add(String.valueOf(crit.maxHeight));
            }

            // Duration
            if (crit.minDuration > 0) {
                where.append(" AND duration >= ?");
                args.add(String.valueOf((long) (crit.minDuration * 1000)));
            }
            if (crit.maxDuration > 0) {
                where.append(" AND duration <= ?");
                args.add(String.valueOf((long) (crit.maxDuration * 1000)));
            }

            // Media Type
            if ("image".equalsIgnoreCase(crit.mediaType)) {
                where.append(" AND mime_type LIKE 'image/%'");
            } else if ("video".equalsIgnoreCase(crit.mediaType)) {
                where.append(" AND mime_type LIKE 'video/%'");
            } else if ("live".equalsIgnoreCase(crit.mediaType)) {
                where.append(" AND is_micro_video = 1");
            }

            // Archived
            if (crit.archived != null) {
                where.append(" AND is_archived = ").append(crit.archived ? "1" : "0");
            }

            // Favorite
            if (crit.favorite != null && crit.source != SourceType.FAVORITES) {
                where.append(" AND is_favorite = ").append(crit.favorite ? "1" : "0");
            }

            // Quality
            if ("original".equalsIgnoreCase(crit.quality)) {
                where.append(" AND quota_charged_bytes > 0");
            } else if ("storage-saver".equalsIgnoreCase(crit.quality)) {
                where.append(" AND (quota_charged_bytes = 0 OR quota_charged_bytes IS NULL)");
            }

            // Ownership
            if (crit.owned != null && hasColumn(db, targetTable, "is_owned")) {
                where.append(" AND is_owned = ").append(crit.owned ? "1" : "0");
            }

            // Upload Status
            if (crit.uploadStatus != null && hasColumn(db, targetTable, "upload_status")) {
                where.append(" AND upload_status = ?");
                args.add(crit.uploadStatus);
            }

            // Location
            boolean hasLat = hasColumn(db, targetTable, "latitude");
            boolean hasLon = hasColumn(db, targetTable, "longitude");
            if (hasLat && hasLon) {
                if (Boolean.TRUE.equals(crit.hasLocation)) {
                    where.append(" AND (latitude IS NOT NULL AND latitude != 0.0)");
                } else if (Boolean.FALSE.equals(crit.hasLocation)) {
                    where.append(" AND (latitude IS NULL OR latitude = 0.0)");
                }
                if (crit.boundSouth != null && crit.boundNorth != null) {
                    where.append(" AND (latitude >= ? AND latitude <= ?)");
                    args.add(String.valueOf(crit.boundSouth));
                    args.add(String.valueOf(crit.boundNorth));
                }
                if (crit.boundWest != null && crit.boundEast != null) {
                    where.append(" AND (longitude >= ? AND longitude <= ?)");
                    args.add(String.valueOf(crit.boundWest));
                    args.add(String.valueOf(crit.boundEast));
                }
            }

            String orderBy = crit.sortBySize ? "size_bytes DESC" : "capture_timestamp DESC";
            if (crit.space == SpaceFilter.CONSUMING) {
                orderBy = "quota_charged_bytes DESC, size_bytes DESC";
            }

            String latExpr = hasLat ? "latitude" : "0.0";
            String lonExpr = hasLon ? "longitude" : "0.0";
            String captionExpr = hasColumn(db, targetTable, "caption") ? "caption" : "''";
            String mediaKeyExpr = hasColumn(db, targetTable, "remote_media_key") ? "COALESCE(NULLIF(remote_media_key, ''), media_key)" : "media_key";

            String sql = "SELECT dedup_key, " + mediaKeyExpr + ", filename, size_bytes, quota_charged_bytes, capture_timestamp, remote_url, " +
                    (hasColumn(db, targetTable, "trash_timestamp") ? "trash_timestamp" : "0") + ", " +
                    "width, height, duration, " +
                    (hasColumn(db, targetTable, "is_archived") ? "is_archived" : "0") + ", " +
                    (hasColumn(db, targetTable, "is_favorite") ? "is_favorite" : "0") + ", " +
                    (hasColumn(db, targetTable, "is_micro_video") ? "is_micro_video" : "0") + ", " +
                    "mime_type, " + latExpr + ", " + lonExpr + ", " + captionExpr + " " +
                    "FROM " + targetTable + " " +
                    "WHERE " + where + " " +
                    "ORDER BY " + orderBy + " LIMIT " + crit.limit;

            try (Cursor c = db.rawQuery(sql, args.toArray(new String[0]))) {
                Pattern fileNamePat = null;
                if (crit.fileNameRegex != null && !crit.fileNameRegex.isEmpty()) {
                    try { fileNamePat = Pattern.compile(crit.fileNameRegex, Pattern.CASE_INSENSITIVE); } catch (Exception ignored) {}
                }
                Pattern descPat = null;
                if (crit.descriptionRegex != null && !crit.descriptionRegex.isEmpty()) {
                    try { descPat = Pattern.compile(crit.descriptionRegex, Pattern.CASE_INSENSITIVE); } catch (Exception ignored) {}
                }

                while (c != null && c.moveToNext()) {
                    String filename = c.getString(2);
                    String caption = c.getString(17);

                    // In-memory Regex matching
                    if (fileNamePat != null) {
                        boolean matches = fileNamePat.matcher(filename != null ? filename : "").find();
                        if ("exclude".equals(crit.fileNameMatchType) && matches) continue;
                        if ("include".equals(crit.fileNameMatchType) && !matches) continue;
                    }
                    if (descPat != null) {
                        boolean matches = descPat.matcher(caption != null ? caption : "").find();
                        if ("exclude".equals(crit.descriptionMatchType) && matches) continue;
                        if ("include".equals(crit.descriptionMatchType) && !matches) continue;
                    }

                    items.add(new MediaItem(
                            c.getString(0),
                            c.getString(1),
                            filename,
                            c.getLong(3),
                            c.getLong(4),
                            c.getLong(5),
                            c.getString(6),
                            c.getLong(7),
                            c.getInt(8),
                            c.getInt(9),
                            c.getLong(10),
                            c.getInt(11) == 1,
                            c.getInt(12) == 1,
                            c.getInt(13) == 1,
                            c.getString(14),
                            c.getDouble(15),
                            c.getDouble(16),
                            caption
                    ));
                }
            }

            // If Source is ALBUMS, also query remote_media in case items were synced there
            if (crit.source == SourceType.ALBUMS && hasTable(db, "remote_media") && !crit.albumsInclude.isEmpty()) {
                try {
                    StringBuilder remWhere = new StringBuilder("collection_id IN (");
                    List<String> remArgs = new ArrayList<>();
                    for (int i = 0; i < crit.albumsInclude.size(); i++) {
                        if (i > 0) remWhere.append(",");
                        remWhere.append("?");
                        remArgs.add(crit.albumsInclude.get(i));
                    }
                    remWhere.append(") AND (trash_timestamp IS NULL OR trash_timestamp = 0)");

                    String remSql = "SELECT dedup_key, " + mediaKeyExpr + ", filename, size_bytes, quota_charged_bytes, capture_timestamp, remote_url, " +
                            "trash_timestamp, width, height, duration, is_archived, is_favorite, is_micro_video, mime_type, " + latExpr + ", " + lonExpr + ", " + captionExpr + " " +
                            "FROM remote_media WHERE " + remWhere;
                    try (Cursor rc = db.rawQuery(remSql, remArgs.toArray(new String[0]))) {
                        HashSet<String> seen = new HashSet<>();
                        for (MediaItem it : items) seen.add(it.dedupKey);
                        while (rc != null && rc.moveToNext()) {
                            String dKey = rc.getString(0);
                            if (seen.contains(dKey)) continue;
                            items.add(new MediaItem(
                                    dKey,
                                    rc.getString(1),
                                    rc.getString(2),
                                    rc.getLong(3),
                                    rc.getLong(4),
                                    rc.getLong(5),
                                    rc.getString(6),
                                    rc.getLong(7),
                                    rc.getInt(8),
                                    rc.getInt(9),
                                    rc.getLong(10),
                                    rc.getInt(11) == 1,
                                    rc.getInt(12) == 1,
                                    rc.getInt(13) == 1,
                                    rc.getString(14),
                                    rc.getDouble(15),
                                    rc.getDouble(16),
                                    rc.getString(17)
                            ));
                            seen.add(dKey);
                        }
                    }
                } catch (Throwable ignored) {}
            }

        } catch (Throwable t) {
            Logger.printException(() -> "Error querying media items", t);
        } finally {
            if (db != null) {
                try { db.close(); } catch (Throwable ignored) {}
            }
        }
        return items;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Perceptual Hashing & Similarity Clustering (1:1 with user.js)
    // ─────────────────────────────────────────────────────────────────────────
    public static long calculateAverageHash(Bitmap bitmap, int hashSize) {
        if (bitmap == null || bitmap.isRecycled()) return 0;
        try {
            int size = Math.min(8, Math.max(4, hashSize)); // 8x8 fits cleanly in a 64-bit long
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, size, size, true);
            int total = size * size;
            int[] pixels = new int[total];
            scaled.getPixels(pixels, 0, size, 0, 0, size, size);
            if (scaled != bitmap) scaled.recycle();

            long sum = 0;
            int[] grays = new int[total];
            for (int i = 0; i < total; i++) {
                int c = pixels[i];
                int gray = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
                grays[i] = gray;
                sum += gray;
            }
            int avg = (int) (sum / total);

            long hash = 0;
            for (int i = 0; i < total; i++) {
                if (grays[i] >= avg) {
                    hash |= (1L << i);
                }
            }
            return hash;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static float calculateSimilarity(long hashA, long hashB, int hashSize) {
        int totalBits = Math.min(64, hashSize * hashSize);
        int distance = Long.bitCount(hashA ^ hashB);
        return 1.0f - ((float) distance / totalBits);
    }

    public static List<List<MediaItem>> clusterSimilarItems(List<MediaItem> items, float threshold, int hashSize) {
        List<List<MediaItem>> clusters = new ArrayList<>();
        if (items == null || items.isEmpty() || threshold <= 0) return clusters;

        for (MediaItem item : items) {
            if (item.perceptualHash == 0) continue;
            boolean added = false;
            for (List<MediaItem> cluster : clusters) {
                long clusterHash = cluster.get(0).perceptualHash;
                float sim = calculateSimilarity(item.perceptualHash, clusterHash, hashSize);
                if (sim >= threshold) {
                    cluster.add(item);
                    added = true;
                    break;
                }
            }
            if (!added) {
                List<MediaItem> newCluster = new ArrayList<>();
                newCluster.add(item);
                clusters.add(newCluster);
            }
        }

        // Only return clusters that have 2 or more similar items
        List<List<MediaItem>> result = new ArrayList<>();
        int clusterIndex = 1;
        for (List<MediaItem> c : clusters) {
            if (c.size() > 1) {
                for (MediaItem item : c) item.clusterId = clusterIndex;
                result.add(c);
                clusterIndex++;
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Action Execution Engine (Step 3: All 14 Actions)
    // ─────────────────────────────────────────────────────────────────────────
    public static class ActionResult {
        public final boolean success;
        public final int affectedCount;
        public final String message;

        public ActionResult(boolean success, int affectedCount, String message) {
            this.success = success;
            this.affectedCount = affectedCount;
            this.message = message;
        }
    }

    public static String createNativeCloudAlbum(Context context, int accountId, String title, List<String> mediaKeys) {
        String createdKey = null;
        try {
            Logger.printInfo(() -> "Creating native cloud album: " + title + " for account " + accountId + " with " + (mediaKeys != null ? mediaKeys.size() : 0) + " items");

            // Method A: Create envelope with initial items and title using bemf.e
            if (mediaKeys != null && !mediaKeys.isEmpty()) {
                try {
                    Class<?> bemfClass = Class.forName("bemf");
                    Constructor<?> bemfCtor = bemfClass.getConstructor(long.class);
                    Object builder = bemfCtor.newInstance(System.currentTimeMillis());

                    // Set title in bemf.g
                    Field fG = bemfClass.getField("g");
                    fG.set(builder, title);

                    // Build initial batch of bemh items (up to 50 items)
                    Class<?> bemhClass = Class.forName("bemh");
                    Class<?> bwzeClass = Class.forName("bwze");
                    Constructor<?> bwzeCtor = bwzeClass.getConstructor(long.class, long.class);
                    Object defaultBwze = bwzeCtor.newInstance(System.currentTimeMillis(), 0L);

                    Constructor<?> bemhCtor = bemhClass.getConstructor(String.class, bwzeClass);

                    int initialCount = Math.min(mediaKeys.size(), 50);
                    List<Object> bemhList = new ArrayList<>();
                    for (int i = 0; i < initialCount; i++) {
                        bemhList.add(bemhCtor.newInstance(mediaKeys.get(i), defaultBwze));
                    }

                    Field fE = bemfClass.getField("e");
                    fE.set(builder, bemhList);

                    // Build bemg (EnvelopeDetails) - bemf.b() sets x = 2 automatically
                    Method bMethod = bemfClass.getMethod("b");
                    Object bemgObj = bMethod.invoke(builder);

                    // Create task via adwr.d(accountId, bemg) -> calls adwr.c natively
                    Class<?> ladwrClass = Class.forName("adwr");
                    Method dMethod = ladwrClass.getMethod("d", int.class, bemgObj.getClass());
                    Object task = dMethod.invoke(null, accountId, bemgObj);

                    if (task != null) {
                        Class<?> bzoqClass = Class.forName("bzoq");
                        Class<?> qcxClass = Class.forName("qcx");
                        Method getServiceMethod = bzoqClass.getMethod("e", Context.class, Class.class);
                        Object taskRunner = getServiceMethod.invoke(null, context, qcxClass);

                        if (taskRunner != null) {
                            Class<?> bxtyClass = Class.forName("bxty");
                            Method executeMethod = qcxClass.getMethod("a", bxtyClass);
                            Object result = executeMethod.invoke(taskRunner, task);

                            if (result != null) {
                                Class<?> bxuzClass = Class.forName("bxuz");
                                int statusCode = bxuzClass.getField("d").getInt(result);
                                Logger.printInfo(() -> "Method A CreateEnvelopeTask status: " + statusCode);
                                if (statusCode == 200) {
                                    Method getBundleMethod = bxuzClass.getMethod("b");
                                    Bundle bundle = (Bundle) getBundleMethod.invoke(result);
                                    if (bundle != null) {
                                        createdKey = bundle.getString("envelope_media_key");
                                        if (createdKey == null || createdKey.isEmpty()) {
                                            Parcelable shareDetails = bundle.getParcelable("envelope_share_details");
                                            if (shareDetails != null) {
                                                createdKey = (String) Class.forName("bemk").getField("a").get(shareDetails);
                                            }
                                        }
                                    }
                                    if (createdKey != null && !createdKey.isEmpty()) {
                                        final String keyA = createdKey;
                                        Logger.printInfo(() -> "Method A created album successfully! Key: " + keyA);
                                        // If more items remain, add them via AddMediaToEnvelopeTask
                                        if (mediaKeys.size() > initialCount) {
                                            List<String> remaining = mediaKeys.subList(initialCount, mediaKeys.size());
                                            addMediaToCloudAlbum(context, accountId, createdKey, remaining);
                                        }
                                        return createdKey;
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable tMethodA) {
                    Logger.printException(() -> "Method A failed, falling back to Method B", tMethodA);
                }
            }

            // Method B (Fallback): Create empty envelope, then add items via AddMediaToEnvelopeTask
            Class<?> bemfClass = Class.forName("bemf");
            Constructor<?> bemfCtor = bemfClass.getConstructor(long.class);
            Object builder = bemfCtor.newInstance(System.currentTimeMillis());

            Field fX = bemfClass.getField("x");
            fX.setInt(builder, 2); // 2 = ENVELOPE_TYPE_ALBUM

            Field fG = bemfClass.getField("g");
            fG.set(builder, title);

            Class<?> bemgClass = Class.forName("bemg");
            Constructor<?> bemgCtor = bemgClass.getConstructor(bemfClass);
            Object bemgObj = bemgCtor.newInstance(builder);

            Class<?> ladwrClass = Class.forName("adwr");
            Method dMethod = ladwrClass.getMethod("d", int.class, bemgClass);
            Object task = dMethod.invoke(null, accountId, bemgObj);

            if (task != null) {
                Class<?> bzoqClass = Class.forName("bzoq");
                Class<?> qcxClass = Class.forName("qcx");
                Method getServiceMethod = bzoqClass.getMethod("e", Context.class, Class.class);
                Object taskRunner = getServiceMethod.invoke(null, context, qcxClass);

                if (taskRunner != null) {
                    Class<?> bxtyClass = Class.forName("bxty");
                    Method executeMethod = qcxClass.getMethod("a", bxtyClass);
                    Object result = executeMethod.invoke(taskRunner, task);

                    if (result != null) {
                        Class<?> bxuzClass = Class.forName("bxuz");
                        int statusCode = bxuzClass.getField("d").getInt(result);
                        Logger.printInfo(() -> "Method B CreateEnvelopeTask status: " + statusCode);
                        Method getBundleMethod = bxuzClass.getMethod("b");
                        Bundle bundle = (Bundle) getBundleMethod.invoke(result);
                        if (bundle != null) {
                            createdKey = bundle.getString("envelope_media_key");
                            if (createdKey == null || createdKey.isEmpty()) {
                                Parcelable shareDetails = bundle.getParcelable("envelope_share_details");
                                if (shareDetails != null) {
                                    createdKey = (String) Class.forName("bemk").getField("a").get(shareDetails);
                                }
                            }
                        }
                    }
                }
            }

            if (createdKey != null && !createdKey.isEmpty()) {
                final String keyB = createdKey;
                Logger.printInfo(() -> "Method B created empty album. Key: " + keyB);
                if (mediaKeys != null && !mediaKeys.isEmpty()) {
                    addMediaToCloudAlbum(context, accountId, createdKey, mediaKeys);
                }
                return createdKey;
            }
        } catch (Throwable t) {
            Logger.printException(() -> "Error in createNativeCloudAlbum", t);
        }
        return null;
    }

    public static int addMediaToCloudAlbum(Context context, int accountId, String envelopeMediaKey, List<String> mediaKeys) {
        if (context == null || envelopeMediaKey == null || envelopeMediaKey.isEmpty() || mediaKeys == null || mediaKeys.isEmpty()) {
            return 0;
        }
        int totalAdded = 0;
        int chunkSize = 100;
        for (int i = 0; i < mediaKeys.size(); i += chunkSize) {
            List<String> chunk = mediaKeys.subList(i, Math.min(i + chunkSize, mediaKeys.size()));
            try {
                Logger.printInfo(() -> "Adding chunk of " + chunk.size() + " items to cloud album " + envelopeMediaKey + " for account " + accountId);

                Class<?> lamwaClass = Class.forName("amwa");
                Constructor<?> lamwaCtor = null;
                for (Constructor<?> c : lamwaClass.getDeclaredConstructors()) {
                    if (c.getParameterTypes().length == 1) {
                        lamwaCtor = c;
                        break;
                    }
                }
                if (lamwaCtor == null) {
                    lamwaCtor = lamwaClass.getConstructor(byte[].class);
                }
                lamwaCtor.setAccessible(true);
                Object builder = lamwaCtor.newInstance(new Object[]{null});

                Field fA = lamwaClass.getField("a");
                fA.setInt(builder, accountId);

                Field fG = lamwaClass.getField("g");
                fG.set(builder, envelopeMediaKey);

                Field fH = lamwaClass.getField("h");
                fH.set(builder, chunk);

                try {
                    Field fD = lamwaClass.getField("d");
                    fD.set(builder, Collections.emptyMap());
                } catch (Throwable ignored) {}

                Method dMethod = lamwaClass.getMethod("d");
                Object task = dMethod.invoke(builder);

                Class<?> bzoqClass = Class.forName("bzoq");
                Class<?> qcxClass = Class.forName("qcx");
                Method getServiceMethod = bzoqClass.getMethod("e", Context.class, Class.class);
                Object taskRunner = getServiceMethod.invoke(null, context, qcxClass);

                Class<?> bxtyClass = Class.forName("bxty");
                Method executeMethod = qcxClass.getMethod("a", bxtyClass);
                Object result = executeMethod.invoke(taskRunner, task);

                if (result != null) {
                    Class<?> bxuzClass = Class.forName("bxuz");
                    int statusCode = bxuzClass.getField("d").getInt(result);
                    Logger.printInfo(() -> "AddMediaToEnvelopeTask chunk status code: " + statusCode);
                    if (statusCode == 200) {
                        Method getBundleMethod = bxuzClass.getMethod("b");
                        Bundle bundle = (Bundle) getBundleMethod.invoke(result);
                        int added = bundle != null ? bundle.getInt("added_media_count", chunk.size()) : chunk.size();
                        totalAdded += added;
                    }
                }
            } catch (Throwable t) {
                Logger.printException(() -> "Error in addMediaToCloudAlbum chunk", t);
            }
        }
        final int finalTotal = totalAdded;
        Logger.printInfo(() -> "Finished adding to cloud album. Total items added: " + finalTotal);
        return totalAdded;
    }

    public static boolean deleteCloudAlbum(Context context, int accountId, String albumMediaKey) {
        if (context == null || albumMediaKey == null || albumMediaKey.isEmpty()) return false;
        try {
            Logger.printInfo(() -> "Deleting cloud album: " + albumMediaKey + " for account " + accountId);

            Class<?> ahnrClass = Class.forName("ahnr");
            Object optionalKey = null;
            try {
                Method aMethod = ahnrClass.getMethod("a", String.class);
                optionalKey = aMethod.invoke(null, albumMediaKey);
            } catch (Throwable t) {
                Constructor<?> ahnrCtor = ahnrClass.getConstructor(String.class);
                optionalKey = ahnrCtor.newInstance(albumMediaKey);
            }

            Class<?> bxtyClass = Class.forName("bxty");
            Class<?> bxueClass = Class.forName("bxue");
            Method fMethod = bxueClass.getMethod("f", Context.class, bxtyClass);

            // Determine whether album is a shared envelope (envelopes table) or private album (collections table)
            boolean isSharedEnvelope = false;
            boolean isOwner = true;
            try {
                File f = context.getDatabasePath("gphotos" + accountId + ".db");
                if (f != null && f.exists()) {
                    SQLiteDatabase checkDb = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                    try (Cursor c = checkDb.rawQuery("SELECT owner_actor_id, viewer_actor_id FROM envelopes WHERE media_key = ? LIMIT 1", new String[]{albumMediaKey})) {
                        if (c != null && c.moveToFirst()) {
                            isSharedEnvelope = true;
                            String owner = c.getString(0);
                            String viewer = c.getString(1);
                            if (owner != null && viewer != null) {
                                isOwner = owner.equals(viewer);
                            }
                        }
                    } catch (Throwable ignored) {}
                    try { checkDb.close(); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            boolean success = false;
            if (isSharedEnvelope) {
                // 1. Shared Envelope: invoke official DeleteSharedCollectionTask synchronously
                // Constructor: DeleteSharedCollectionTask(int accountId, ahnr collectionMediaKey, boolean isLeave, boolean isDelete)
                try {
                    boolean isLeave = !isOwner;
                    boolean isDelete = isOwner;
                    Class<?> delTaskClass = Class.forName("com.google.android.apps.photos.album.removealbum.DeleteSharedCollectionTask");
                    Constructor<?> delCtor = delTaskClass.getConstructor(int.class, ahnrClass, boolean.class, boolean.class);
                    Object delTask = delCtor.newInstance(accountId, optionalKey, isLeave, isDelete);
                    Object res = fMethod.invoke(null, context, delTask);
                    if (res != null) {
                        try {
                            int statusCode = res.getClass().getField("d").getInt(res);
                            Logger.printInfo(() -> "DeleteSharedCollectionTask synchronous result status: " + statusCode);
                            if (statusCode == 200) success = true;
                        } catch (Throwable ignored) {}
                    }
                    final boolean fSuccess = success;
                    Logger.printInfo(() -> "Dispatched DeleteSharedCollectionTask for " + albumMediaKey + " (isLeave=" + isLeave + ", isDelete=" + isDelete + "), success=" + fSuccess);
                } catch (Throwable tDel) {
                    Logger.printException(() -> "Error calling DeleteSharedCollectionTask", tDel);
                }
            } else {
                // 2. Private Album: invoke official mne.a (RemoveCollectionProvider)
                try {
                    Class<?> bzoqClass = Class.forName("bzoq");
                    Method eMethod = bzoqClass.getMethod("e", Context.class, Class.class);
                    Class<?> mneClass = Class.forName("mne");
                    Object mneInstance = eMethod.invoke(null, context, mneClass);
                    Method aMethod = mneClass.getMethod("a", int.class, ahnrClass);
                    aMethod.invoke(mneInstance, accountId, optionalKey);
                    Logger.printInfo(() -> "Successfully invoked mne.a for private album " + albumMediaKey);
                    success = true;
                } catch (Throwable tMne) {
                    Logger.printException(() -> "Error calling mne.a", tMne);
                }

                // Fallback to RemoveCollectionProvider$RemoveCollectionTask directly if mne.a failed
                if (!success) {
                    try {
                        Class<?> wrrClass = Class.forName("wrr");
                        Object emptyFeatures = wrrClass.getField("a").get(null);
                        Class<?> nysClass = Class.forName("nys");
                        Constructor<?> nysCtor = nysClass.getConstructor(int.class, ahnrClass, wrrClass);
                        Object collection = nysCtor.newInstance(accountId, optionalKey, emptyFeatures);

                        Class<?> bwpgClass = Class.forName("bwpg");
                        Class<?> removeTaskClass = Class.forName("com.google.android.apps.photos.album.removealbum.RemoveCollectionProvider$RemoveCollectionTask");
                        Constructor<?> removeCtor = removeTaskClass.getConstructor(int.class, bwpgClass);
                        Object removeTask = removeCtor.newInstance(accountId, collection);

                        Object res = fMethod.invoke(null, context, removeTask);
                        if (res != null) {
                            try {
                                int statusCode = res.getClass().getField("d").getInt(res);
                                if (statusCode == 200) success = true;
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable tRemove) {
                        Logger.printException(() -> "Error calling RemoveCollectionProvider$RemoveCollectionTask", tRemove);
                    }
                }
            }

            return success;
        } catch (Throwable t) {
            Logger.printException(() -> "Error in deleteCloudAlbum", t);
        }
        return false;
    }

    private static List<String> resolveRemoteMediaKeys(Context context, int accountId, List<MediaItem> items) {
        List<String> mediaKeys = new ArrayList<>();
        if (items == null || items.isEmpty()) return mediaKeys;

        SQLiteDatabase db = null;
        try {
            File f = context != null ? context.getDatabasePath("gphotos" + accountId + ".db") : null;
            if (f != null && f.exists()) {
                db = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            }
        } catch (Throwable ignored) {}

        for (MediaItem it : items) {
            String key = null;
            // 1. Look up media table first by dedupKey for canonical_media_key (cloud primary copy)
            if (db != null && it.dedupKey != null && !it.dedupKey.trim().isEmpty() && hasTable(db, "media") && hasColumn(db, "media", "canonical_media_key")) {
                try (Cursor c = db.rawQuery("SELECT canonical_media_key FROM media WHERE dedup_key = ? AND canonical_media_key IS NOT NULL AND canonical_media_key != '' AND canonical_media_key NOT LIKE 'local:%' LIMIT 1", new String[]{it.dedupKey})) {
                    if (c != null && c.moveToFirst()) {
                        String rk = c.getString(0);
                        if (rk != null && !rk.trim().isEmpty()) {
                            key = rk.trim();
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // 2. Look up remote_media table ordered by is_canonical DESC, is_canonical_copy DESC
            if ((key == null || key.startsWith("local:")) && db != null && it.dedupKey != null && !it.dedupKey.trim().isEmpty() && hasTable(db, "remote_media")) {
                try (Cursor c = db.rawQuery("SELECT COALESCE(NULLIF(remote_media_key, ''), media_key) FROM remote_media WHERE dedup_key = ? AND (trash_timestamp IS NULL OR trash_timestamp = 0) ORDER BY is_canonical DESC, is_canonical_copy DESC LIMIT 1", new String[]{it.dedupKey})) {
                    if (c != null && c.moveToFirst()) {
                        String rk = c.getString(0);
                        if (rk != null && !rk.trim().isEmpty() && !rk.startsWith("local:")) {
                            key = rk.trim();
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // 3. If not found in remote_media, check item's own mediaKey
            if (key == null || key.startsWith("local:")) {
                if (it.mediaKey != null && !it.mediaKey.trim().isEmpty() && !it.mediaKey.startsWith("local:")) {
                    key = it.mediaKey.trim();
                }
            }
            // 4. If still local or null, check shared_media
            if ((key == null || key.startsWith("local:")) && db != null && it.dedupKey != null && !it.dedupKey.trim().isEmpty() && hasTable(db, "shared_media")) {
                try (Cursor c = db.rawQuery("SELECT remote_media_key FROM shared_media WHERE dedup_key = ? AND remote_media_key IS NOT NULL AND remote_media_key != '' AND remote_media_key NOT LIKE 'local:%' LIMIT 1", new String[]{it.dedupKey})) {
                    if (c != null && c.moveToFirst()) {
                        String rk = c.getString(0);
                        if (rk != null && !rk.trim().isEmpty()) {
                            key = rk.trim();
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (key != null && !key.trim().isEmpty() && !key.startsWith("local:")) {
                mediaKeys.add(key);
            }
        }

        if (db != null) {
            try { db.close(); } catch (Throwable ignored) {}
        }
        return mediaKeys;
    }

    private static List<String> resolveRemoteMediaKeys(AccountInfo account, List<MediaItem> items) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        Context ctx = app.morphe.extension.shared.Utils.getContext();
        int accId = account != null ? account.accountId : 0;
        return resolveRemoteMediaKeys(ctx, accId, items);
    }

    private static Object createMediaCollectionForKeys(int accountId, List<String> mediaKeys) {
        try {
            Class<?> nulClass = Class.forName("nul");
            Object builder = nulClass.getConstructor().newInstance();
            nulClass.getField("a").setInt(builder, accountId);
            nulClass.getField("b").set(builder, mediaKeys);
            nulClass.getField("d").setBoolean(builder, true);
            nulClass.getField("e").setBoolean(builder, true);
            Method buildMethod = nulClass.getMethod("a");
            return buildMethod.invoke(builder);
        } catch (Throwable t) {
            Logger.printException(() -> "Error building media collection for keys", t);
            return null;
        }
    }

    private static Object buildCoreFeaturesRequest(Context context) {
        try {
            Class<?> wqaClass = Class.forName("wqa");
            Constructor<?> wqaCtor = wqaClass.getConstructor(boolean.class);
            Object builder = wqaCtor.newInstance(true);
            Method dMethod = wqaClass.getMethod("d", Class.forName("wqb"));

            // 1. Core Trash Features: neb.a (contains zon, bams, Axdq, Laflw)
            try {
                Class<?> nebClass = Class.forName("neb");
                Field f = nebClass.getField("a");
                Object fVal = f.get(null);
                if (fVal != null) dMethod.invoke(builder, fVal);
            } catch (Throwable t) {
                Logger.printInfo(() -> "neb.a not resolved: " + t.getMessage());
            }

            // 2. Favorites Features: FavoritesTask.c (contains bfwt, bfwu, Axdq, bams)
            try {
                Class<?> favClass = Class.forName("com.google.android.apps.photos.favorites.FavoritesTask");
                Field f = favClass.getDeclaredField("c");
                f.setAccessible(true);
                Object fVal = f.get(null);
                if (fVal != null) dMethod.invoke(builder, fVal);
            } catch (Throwable t) {
                Logger.printInfo(() -> "FavoritesTask.c not resolved: " + t.getMessage());
            }

            // 3. Restore Features: bjiq.a (contains bams, zon)
            try {
                Class<?> bjiqClass = Class.forName("bjiq");
                Field f = bjiqClass.getField("a");
                Object fVal = f.get(null);
                if (fVal != null) dMethod.invoke(builder, fVal);
            } catch (Throwable t) {
                Logger.printInfo(() -> "bjiq.a not resolved: " + t.getMessage());
            }

            // 4. Delete Label features: out.a(context)
            if (context != null) {
                try {
                    Class<?> outClass = Class.forName("out");
                    Method aMethod = outClass.getMethod("a", Context.class);
                    Object outVal = aMethod.invoke(null, context);
                    if (outVal != null) dMethod.invoke(builder, outVal);
                } catch (Throwable t) {
                    Logger.printInfo(() -> "out.a not resolved: " + t.getMessage());
                }
            }

            Method aMethod = wqaClass.getMethod("a");
            return aMethod.invoke(builder);
        } catch (Throwable t) {
            Logger.printException(() -> "Error constructing composite FeaturesRequest", t);
            try {
                Class<?> wqbClass = Class.forName("wqb");
                return wqbClass.getField("a").get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static List<?> loadCoreMediaItems(Context context, int accountId, List<MediaItem> items) {
        List<Object> result = new ArrayList<>();
        if (context == null || items == null || items.isEmpty()) return result;
        List<String> keys = resolveRemoteMediaKeys(context, accountId, items);
        if (keys.isEmpty()) {
            Logger.printInfo(() -> "resolveRemoteMediaKeys found 0 remote keys for account " + accountId);
            return result;
        }

        try {
            Object mediaCollection = createMediaCollectionForKeys(accountId, keys);
            if (mediaCollection == null) return result;

            Class<?> bwpgClass = Class.forName("bwpg");
            Class<?> wsgClass = Class.forName("wsg");
            Method aRefMethod = wsgClass.getMethod("a", bwpgClass);
            Object collectionRef = aRefMethod.invoke(null, mediaCollection);

            Class<?> wrlClass = Class.forName("wrl");
            Class<?> wryClass = Class.forName("wry");
            Class<?> wqsClass = Class.forName("wqs");
            Class<?> wqbClass = Class.forName("wqb");

            Object wqsVal = wqsClass.getField("a").get(null);
            Object wqbVal = buildCoreFeaturesRequest(context);
            if (wqbVal == null) {
                wqbVal = wqbClass.getField("a").get(null);
            }

            Method hMethod = wrlClass.getMethod("H", Context.class, wryClass, wqsClass, wqbClass);
            List<?> loaded = (List<?>) hMethod.invoke(null, context, collectionRef, wqsVal, wqbVal);
            if (loaded != null) {
                result.addAll(loaded);
            }
            Logger.printInfo(() -> "Loaded " + result.size() + " core media items for " + keys.size() + " keys");
        } catch (Throwable t) {
            Logger.printException(() -> "Error loading core media items (bwpf)", t);
        }
        return result;
    }

    private static boolean executeOnlineTrashTask(Context context, int accountId, List<MediaItem> items) {
        if (context == null || items == null || items.isEmpty()) return false;
        try {
            List<?> coreItems = loadCoreMediaItems(context, accountId, items);
            if (coreItems.isEmpty()) {
                Logger.printInfo(() -> "executeOnlineTrashTask: 0 core media items loaded, cannot dispatch MoveToTrashActionWrapper");
                return false;
            }

            Class<?> bdkqClass = Class.forName("bdkq");
            Constructor<?> bdkqCtor = bdkqClass.getConstructor(Collection.class);
            Object mediaGroup = bdkqCtor.newInstance(coreItems);

            Class<?> lakjzClass = Class.forName("akjz");
            Object targetMode = null;
            try {
                targetMode = lakjzClass.getField("c").get(null);
            } catch (Throwable t) {
                targetMode = lakjzClass.getField("b").get(null);
            }

            Class<?> romClass = Class.forName("rom");
            Method romMethod = romClass.getMethod("b", Context.class);
            Object cjizSource = romMethod.invoke(null, context);

            Class<?> wrapperClass = Class.forName("com.google.android.apps.photos.trash.MoveToTrashActionWrapper");
            Class<?> cjizClass = Class.forName("cjiz");
            Constructor<?> wrapperCtor = wrapperClass.getConstructor(int.class, bdkqClass, lakjzClass, cjizClass);
            Object task = wrapperCtor.newInstance(accountId, mediaGroup, targetMode, cjizSource);

            try {
                Bundle taskBundle = new Bundle();
                taskBundle.putParcelable("mediagroup", (Parcelable) mediaGroup);
                Field sField = Class.forName("bxty").getField("s");
                sField.set(task, taskBundle);
            } catch (Throwable ignored) {}

            Class<?> bxtyClass = Class.forName("bxty");
            Class<?> bxueClass = Class.forName("bxue");
            Method runMethod = bxueClass.getMethod("f", Context.class, bxtyClass);
            Object res = runMethod.invoke(null, context, task);
            boolean success = false;
            if (res != null) {
                try {
                    int statusCode = res.getClass().getField("d").getInt(res);
                    Logger.printInfo(() -> "MoveToTrashActionWrapper synchronous result status: " + statusCode);
                    if (statusCode == 200) success = true;
                } catch (Throwable ignored) {}
            }
            final boolean fSuccess = success;
            Logger.printInfo(() -> "Executed MoveToTrashActionWrapper with " + coreItems.size() + " core media items, success=" + fSuccess);
            return success;
        } catch (Throwable t) {
            Logger.printException(() -> "Error invoking MoveToTrashActionWrapper", t);
            return false;
        }
    }

    private static boolean executeOnlineRestoreTask(Context context, int accountId, List<MediaItem> items) {
        if (context == null || items == null || items.isEmpty()) return false;
        try {
            List<?> coreItems = loadCoreMediaItems(context, accountId, items);
            if (coreItems.isEmpty()) return false;

            Class<?> bdkqClass = Class.forName("bdkq");
            Constructor<?> bdkqCtor = bdkqClass.getConstructor(Collection.class);
            Object mediaGroup = bdkqCtor.newInstance(coreItems);

            Class<?> restoreClass = Class.forName("com.google.android.apps.photos.trash.restore.RestoreActionTask");
            Constructor<?> restoreCtor = restoreClass.getConstructor(int.class, bdkqClass, boolean.class);
            Object task = restoreCtor.newInstance(accountId, mediaGroup, false);

            Class<?> bxtyClass = Class.forName("bxty");
            Class<?> bxueClass = Class.forName("bxue");
            Method runMethod = bxueClass.getMethod("f", Context.class, bxtyClass);
            Object res = runMethod.invoke(null, context, task);
            boolean success = false;
            if (res != null) {
                try {
                    int statusCode = res.getClass().getField("d").getInt(res);
                    if (statusCode == 200) success = true;
                } catch (Throwable ignored) {}
            }
            return success;
        } catch (Throwable t) {
            Logger.printException(() -> "Error invoking RestoreActionTask", t);
            return false;
        }
    }

    private static boolean executeOnlineArchiveTask(Context context, int accountId, List<MediaItem> items, boolean archive) {
        if (context == null || items == null || items.isEmpty()) return false;
        try {
            List<?> coreItems = loadCoreMediaItems(context, accountId, items);
            if (coreItems.isEmpty()) return false;

            Set<?> set = new HashSet<>(coreItems);
            Class<?> archiveClass = Class.forName("com.google.android.apps.photos.archive.ArchiveTask");
            Constructor<?> archiveCtor = archiveClass.getConstructor(int.class, Set.class, int.class);
            Object task = archiveCtor.newInstance(accountId, set, archive ? 1 : 2);

            Class<?> bxtyClass = Class.forName("bxty");
            Class<?> bxueClass = Class.forName("bxue");
            Method runMethod = bxueClass.getMethod("f", Context.class, bxtyClass);
            Object res = runMethod.invoke(null, context, task);
            boolean success = false;
            if (res != null) {
                try {
                    int statusCode = res.getClass().getField("d").getInt(res);
                    if (statusCode == 200) success = true;
                } catch (Throwable ignored) {}
            }
            return success;
        } catch (Throwable t) {
            Logger.printException(() -> "Error invoking ArchiveTask", t);
            return false;
        }
    }

    private static boolean executeOnlineFavoriteTask(Context context, int accountId, List<MediaItem> items, boolean favorite) {
        if (context == null || items == null || items.isEmpty()) return false;
        try {
            List<?> coreItems = loadCoreMediaItems(context, accountId, items);
            if (coreItems.isEmpty()) return false;

            Class<?> swhClass = Class.forName("swh");
            Object reason = swhClass.getField("a").get(null);

            Class<?> favClass = Class.forName("com.google.android.apps.photos.favorites.FavoritesTask");
            Constructor<?> favCtor = favClass.getConstructor(int.class, Collection.class, boolean.class, swhClass);
            Object task = favCtor.newInstance(accountId, coreItems, favorite, reason);

            Class<?> bxtyClass = Class.forName("bxty");
            Class<?> bxueClass = Class.forName("bxue");
            Method runMethod = bxueClass.getMethod("f", Context.class, bxtyClass);
            Object res = runMethod.invoke(null, context, task);
            boolean success = false;
            if (res != null) {
                try {
                    int statusCode = res.getClass().getField("d").getInt(res);
                    if (statusCode == 200) success = true;
                } catch (Throwable ignored) {}
            }
            return success;
        } catch (Throwable t) {
            Logger.printException(() -> "Error invoking FavoritesTask", t);
            return false;
        }
    }

    private static void triggerOnlineSync(Context context, int accountId) {
        if (context == null) return;
        try {
            Class<?> syncTaskClass = Class.forName("com.google.android.apps.photos.metasync.actionqueue.block.SyncActionQueueBlock$SyncBackgroundTask");
            Constructor<?> syncCtor = syncTaskClass.getConstructor(int.class);
            Object syncTask = syncCtor.newInstance(accountId);

            Class<?> bxtyClass = Class.forName("bxty");
            Class<?> bxueClass = Class.forName("bxue");
            try {
                Method fMethod = bxueClass.getMethod("f", Context.class, bxtyClass);
                fMethod.invoke(null, context, syncTask);
                Logger.printInfo(() -> "Successfully executed SyncActionQueueBlock synchronously for account " + accountId);
            } catch (Throwable tSync) {
                Method runMethod = bxueClass.getMethod("n", Context.class, bxtyClass);
                runMethod.invoke(null, context, syncTask);
                Logger.printInfo(() -> "Scheduled SyncActionQueueBlock asynchronously for account " + accountId);
            }
        } catch (Throwable t) {
            Logger.printException(() -> "Could not run SyncActionQueueBlock", t);
        }
    }

    public static ActionResult executeDatabaseAction(Context context, AccountInfo account, String actionId, List<MediaItem> items, String param) {
        if (account == null) return new ActionResult(false, 0, "No account selected");
        if ((items == null || items.isEmpty()) && !("toTrash".equals(actionId) && param != null && !param.trim().isEmpty())) {
            return new ActionResult(false, 0, "No items selected");
        }

        // Native Cloud Album handling (runs via Google Photos TaskRunner and syncs to cloud)
        if ("toNewAlbum".equals(actionId)) {
            String newAlbumName = param != null && !param.trim().isEmpty() ? param.trim() : "New Album";
            List<String> mediaKeys = resolveRemoteMediaKeys(account, items);
            if (mediaKeys.isEmpty()) {
                return new ActionResult(false, 0, "None of the selected items have a cloud media key. Albums require backed-up photos.");
            }
            String cloudAlbumKey = createNativeCloudAlbum(context, account.accountId, newAlbumName, mediaKeys);
            if (cloudAlbumKey == null) {
                return new ActionResult(false, 0, "Failed to create cloud album on Google Photos servers. Please check your internet connection.");
            }
            return new ActionResult(true, mediaKeys.size(), "Successfully created album '" + newAlbumName + "' with " + mediaKeys.size() + " items");
        }

        if ("toExistingAlbum".equals(actionId)) {
            if (param == null || param.trim().isEmpty()) {
                return new ActionResult(false, 0, "No album selected");
            }
            String existingColKey = param.trim();
            List<String> mediaKeys = resolveRemoteMediaKeys(account, items);
            if (mediaKeys.isEmpty()) {
                return new ActionResult(false, 0, "None of the selected items have a cloud media key. Albums require backed-up photos.");
            }
            int added = addMediaToCloudAlbum(context, account.accountId, existingColKey, mediaKeys);
            if (added <= 0) {
                return new ActionResult(false, 0, "Failed to add items to cloud album on Google Photos servers.");
            }
            return new ActionResult(true, added, "Successfully added " + added + " items to album");
        }

        if ("toTrash".equals(actionId)) {
            // 1. Invoke official Google Photos MoveToTrashActionWrapper synchronously FIRST
            boolean dispatchedOnlineTrash = executeOnlineTrashTask(context, account.accountId, items);
            Logger.printInfo(() -> "Executed official online trash task: " + dispatchedOnlineTrash);

            // 2. Invoke official Google Photos DeleteSharedCollectionTask / mne synchronously SECOND while envelopes/collections are intact
            int deletedAlbums = 0;
            if (param != null && !param.trim().isEmpty()) {
                String[] albumKeys = param.split(",");
                for (String albKey : albumKeys) {
                    String key = albKey.trim();
                    if (key.isEmpty()) continue;
                    boolean albSuccess = deleteCloudAlbum(context, account.accountId, key);
                    Logger.printInfo(() -> "deleteCloudAlbum(" + key + ") result: " + albSuccess);
                    deletedAlbums++;
                }
            }

            // 3. Optimistic local database update on media, remote_media, local_media
            if (items != null && !items.isEmpty()) {
                File dbFile = new File(account.dbPath);
                if (dbFile.exists()) {
                    SQLiteDatabase localDb = null;
                    try {
                        localDb = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING);
                        localDb.beginTransaction();
                        long now = System.currentTimeMillis();
                        for (MediaItem it : items) {
                            ContentValues cv = new ContentValues();
                            cv.put("trash_timestamp", now);
                            localDb.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                            if (hasTable(localDb, "media")) {
                                ContentValues mCv = new ContentValues();
                                mCv.put("trash_timestamp", now);
                                mCv.put("is_deleted", 0);
                                localDb.update("media", mCv, "dedup_key = ?", new String[]{it.dedupKey});
                            }
                            if (hasTable(localDb, "local_media")) localDb.update("local_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        }
                        localDb.setTransactionSuccessful();
                        localDb.endTransaction();
                    } catch (Throwable tLocal) {
                        Logger.printException(() -> "Error in local trash update", tLocal);
                    } finally {
                        if (localDb != null) {
                            try { localDb.close(); } catch (Throwable ignored) {}
                        }
                    }
                }
            }

            // 4. Trigger MetaSync & ActionQueue sync to flush to Google Photos servers
            triggerOnlineSync(context, account.accountId);

            try {
                if (context != null) {
                    ContentResolver cr = context.getContentResolver();
                    cr.notifyChange(Uri.parse("content://GPhotos/trash"), null);
                    cr.notifyChange(Uri.parse("content://GPhotos/all_photos"), null);
                    cr.notifyChange(Uri.parse("content://GPhotos/collections"), null);
                }
            } catch (Throwable ignored) {}

            int count = (items != null) ? items.size() : 0;
            if (deletedAlbums > 0) {
                return new ActionResult(true, count, "Moved " + count + " items to Trash & deleted " + deletedAlbums + " album(s)");
            }
            return new ActionResult(true, count, "Successfully moved " + count + " items to Trash");
        }

        File dbFile = new File(account.dbPath);
        if (!dbFile.exists()) return new ActionResult(false, 0, "Database file not found");

        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING);
            db.beginTransaction();
            int count = (items != null) ? items.size() : 0;

            long now = System.currentTimeMillis();
            switch (actionId) {
                case "restoreTrash":
                    boolean dispatchedOnlineRestore = executeOnlineRestoreTask(context, account.accountId, items);
                    Logger.printInfo(() -> "Dispatched official online restore task: " + dispatchedOnlineRestore);

                    for (MediaItem it : items) {
                        ContentValues cv = new ContentValues();
                        cv.put("trash_timestamp", 0);
                        db.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "media")) {
                            ContentValues mCv = new ContentValues();
                            mCv.put("trash_timestamp", 0);
                            mCv.put("is_deleted", 0);
                            db.update("media", mCv, "dedup_key = ?", new String[]{it.dedupKey});
                        }
                        if (hasTable(db, "local_media")) db.update("local_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                    }

                    db.setTransactionSuccessful();
                    db.endTransaction();
                    try { db.close(); db = null; } catch (Throwable ignored) {}

                    triggerOnlineSync(context, account.accountId);

                    try {
                        if (context != null) {
                            ContentResolver cr = context.getContentResolver();
                            cr.notifyChange(Uri.parse("content://GPhotos/trash"), null);
                            cr.notifyChange(Uri.parse("content://GPhotos/all_photos"), null);
                        }
                    } catch (Throwable ignored) {}

                    return new ActionResult(true, count, "Successfully restored " + count + " items from Trash");

                case "toArchive":
                    boolean dispatchedOnlineArchive = executeOnlineArchiveTask(context, account.accountId, items, true);
                    Logger.printInfo(() -> "Dispatched official online archive task: " + dispatchedOnlineArchive);

                    for (MediaItem it : items) {
                        ContentValues cv = new ContentValues();
                        cv.put("is_archived", 1);
                        db.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "media")) db.update("media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "local_media")) db.update("local_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                    }

                    db.setTransactionSuccessful();
                    db.endTransaction();
                    try { db.close(); db = null; } catch (Throwable ignored) {}

                    triggerOnlineSync(context, account.accountId);

                    try {
                        if (context != null) {
                            ContentResolver cr = context.getContentResolver();
                            cr.notifyChange(Uri.parse("content://GPhotos/all_photos"), null);
                        }
                    } catch (Throwable ignored) {}

                    return new ActionResult(true, count, "Successfully archived " + count + " items");

                case "unArchive":
                    boolean dispatchedOnlineUnarchive = executeOnlineArchiveTask(context, account.accountId, items, false);
                    Logger.printInfo(() -> "Dispatched official online unarchive task: " + dispatchedOnlineUnarchive);

                    for (MediaItem it : items) {
                        ContentValues cv = new ContentValues();
                        cv.put("is_archived", 0);
                        db.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "media")) db.update("media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "local_media")) db.update("local_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                    }

                    db.setTransactionSuccessful();
                    db.endTransaction();
                    try { db.close(); db = null; } catch (Throwable ignored) {}

                    triggerOnlineSync(context, account.accountId);

                    try {
                        if (context != null) {
                            ContentResolver cr = context.getContentResolver();
                            cr.notifyChange(Uri.parse("content://GPhotos/all_photos"), null);
                        }
                    } catch (Throwable ignored) {}

                    return new ActionResult(true, count, "Successfully un-archived " + count + " items");

                case "toFavorite":
                    boolean dispatchedOnlineFav = executeOnlineFavoriteTask(context, account.accountId, items, true);
                    Logger.printInfo(() -> "Dispatched official online favorite task: " + dispatchedOnlineFav);

                    for (MediaItem it : items) {
                        ContentValues cv = new ContentValues();
                        cv.put("is_favorite", 1);
                        db.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "media")) db.update("media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "local_media")) db.update("local_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                    }

                    db.setTransactionSuccessful();
                    db.endTransaction();
                    try { db.close(); db = null; } catch (Throwable ignored) {}

                    triggerOnlineSync(context, account.accountId);

                    try {
                        if (context != null) {
                            ContentResolver cr = context.getContentResolver();
                            cr.notifyChange(Uri.parse("content://GPhotos/all_photos"), null);
                        }
                    } catch (Throwable ignored) {}

                    return new ActionResult(true, count, "Successfully added " + count + " items to favorites");

                case "unFavorite":
                    boolean dispatchedOnlineUnfav = executeOnlineFavoriteTask(context, account.accountId, items, false);
                    Logger.printInfo(() -> "Dispatched official online unfavorite task: " + dispatchedOnlineUnfav);

                    for (MediaItem it : items) {
                        ContentValues cv = new ContentValues();
                        cv.put("is_favorite", 0);
                        db.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "media")) db.update("media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "local_media")) db.update("local_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                    }

                    db.setTransactionSuccessful();
                    db.endTransaction();
                    try { db.close(); db = null; } catch (Throwable ignored) {}

                    triggerOnlineSync(context, account.accountId);

                    try {
                        if (context != null) {
                            ContentResolver cr = context.getContentResolver();
                            cr.notifyChange(Uri.parse("content://GPhotos/all_photos"), null);
                        }
                    } catch (Throwable ignored) {}

                    return new ActionResult(true, count, "Successfully removed " + count + " items from favorites");

                case "removeFromAlbum":
                    if (items != null) {
                        for (MediaItem it : items) {
                            ContentValues cv = new ContentValues();
                            cv.putNull("collection_id");
                            db.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                            if (hasTable(db, "shared_media")) {
                                if (param != null && !param.trim().isEmpty()) {
                                    String[] albumKeys = param.split(",");
                                    for (String k : albumKeys) {
                                        db.delete("shared_media", "dedup_key = ? AND collection_id = ?", new String[]{it.dedupKey, k.trim()});
                                    }
                                } else {
                                    db.delete("shared_media", "dedup_key = ?", new String[]{it.dedupKey});
                                }
                            }
                        }
                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();
                    try { db.close(); db = null; } catch (Throwable ignored) {}

                    triggerOnlineSync(context, account.accountId);

                    try {
                        if (context != null) {
                            ContentResolver cr = context.getContentResolver();
                            cr.notifyChange(Uri.parse("content://GPhotos/collections"), null);
                        }
                    } catch (Throwable ignored) {}

                    return new ActionResult(true, count, "Removed " + count + " items from album");

                case "lock":
                    for (MediaItem it : items) {
                        if (hasTable(db, "remote_locked_media")) {
                            db.execSQL("INSERT OR REPLACE INTO remote_locked_media (_id, dedup_key, capture_timestamp, type, " +
                                            "protobuf, width, height, size_bytes, timezone_offset, utc_timestamp, duration, filename, " +
                                            "latitude, longitude, mime_type, remote_url, media_key, quota_charged_bytes) " +
                                            "SELECT _id, dedup_key, capture_timestamp, type, protobuf, width, height, size_bytes, " +
                                            "timezone_offset, utc_timestamp, duration, filename, latitude, longitude, mime_type, " +
                                            "remote_url, media_key, quota_charged_bytes FROM remote_media WHERE dedup_key = ?",
                                    new Object[]{it.dedupKey});
                        }
                        ContentValues cv = new ContentValues();
                        cv.put("is_hidden", 1);
                        db.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "media")) db.update("media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();
                    try { db.close(); db = null; } catch (Throwable ignored) {}

                    triggerOnlineSync(context, account.accountId);

                    try {
                        if (context != null) {
                            ContentResolver cr = context.getContentResolver();
                            cr.notifyChange(Uri.parse("content://GPhotos/all_photos"), null);
                        }
                    } catch (Throwable ignored) {}

                    return new ActionResult(true, count, "Locked " + count + " items");

                case "unLock":
                    for (MediaItem it : items) {
                        ContentValues cv = new ContentValues();
                        cv.put("is_hidden", 0);
                        db.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "media")) db.update("media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                        if (hasTable(db, "remote_locked_media")) {
                            db.delete("remote_locked_media", "dedup_key = ?", new String[]{it.dedupKey});
                        }
                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();
                    try { db.close(); db = null; } catch (Throwable ignored) {}

                    triggerOnlineSync(context, account.accountId);

                    try {
                        if (context != null) {
                            ContentResolver cr = context.getContentResolver();
                            cr.notifyChange(Uri.parse("content://GPhotos/all_photos"), null);
                        }
                    } catch (Throwable ignored) {}

                    return new ActionResult(true, count, "Unlocked " + count + " items");

                case "copyDescFromOther":
                    for (MediaItem it : items) {
                        String desc = it.caption;
                        if (desc == null || desc.trim().isEmpty()) {
                            desc = it.filename;
                        }
                        if (desc != null && !desc.trim().isEmpty()) {
                            ContentValues cv = new ContentValues();
                            cv.put("user_specified_caption", desc);
                            db.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                            if (hasTable(db, "local_media")) {
                                db.update("local_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                            }
                        }
                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();
                    try { db.close(); db = null; } catch (Throwable ignored) {}

                    triggerOnlineSync(context, account.accountId);

                    try {
                        if (context != null) {
                            ContentResolver cr = context.getContentResolver();
                            cr.notifyChange(Uri.parse("content://GPhotos/all_photos"), null);
                        }
                    } catch (Throwable ignored) {}

                    return new ActionResult(true, count, "Copied description for " + count + " items");

                case "setDateFromFilename":
                    Pattern p1 = Pattern.compile("(\\d{4})[_-]?(\\d{2})[_-]?(\\d{2})[_-]?(\\d{2})?(\\d{2})?(\\d{2})?");
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                    for (MediaItem it : items) {
                        Matcher m = p1.matcher(it.filename);
                        if (m.find()) {
                            try {
                                String yr = m.group(1);
                                String mo = m.group(2);
                                String dy = m.group(3);
                                String hr = m.group(4) != null ? m.group(4) : "12";
                                String mi = m.group(5) != null ? m.group(5) : "00";
                                String se = m.group(6) != null ? m.group(6) : "00";
                                Date parsed = sdf.parse(yr + mo + dy + hr + mi + se);
                                if (parsed != null) {
                                    ContentValues cv = new ContentValues();
                                    cv.put("capture_timestamp", parsed.getTime());
                                    db.update("remote_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                                    if (hasTable(db, "media")) db.update("media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                                    if (hasTable(db, "local_media")) db.update("local_media", cv, "dedup_key = ?", new String[]{it.dedupKey});
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();
                    try { db.close(); db = null; } catch (Throwable ignored) {}

                    triggerOnlineSync(context, account.accountId);

                    try {
                        if (context != null) {
                            ContentResolver cr = context.getContentResolver();
                            cr.notifyChange(Uri.parse("content://GPhotos/all_photos"), null);
                        }
                    } catch (Throwable ignored) {}

                    return new ActionResult(true, count, "Updated date from filename for " + count + " items");

                default:
                    db.endTransaction();
                    return new ActionResult(false, 0, "Action " + actionId + " not supported directly");
            }
        } catch (Throwable t) {
            Logger.printException(() -> "Error executing database action " + actionId, t);
            if (db != null && db.inTransaction()) {
                try { db.endTransaction(); } catch (Throwable ignored) {}
            }
            return new ActionResult(false, 0, "Error: " + t.getMessage());
        } finally {
            if (db != null) {
                try { db.close(); } catch (Throwable ignored) {}
            }
        }
    }

    public static String exportToCsv(List<MediaItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("mediaKey,dedupKey,fileName,takenAt,width,height,durationMs,sizeBytes,takesUpSpace,spaceTakenBytes,isArchived,isFavorite,latitude,longitude,caption,thumbnailUrl\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

        for (MediaItem item : items) {
            sb.append(escapeCsv(item.mediaKey)).append(",");
            sb.append(escapeCsv(item.dedupKey)).append(",");
            sb.append(escapeCsv(item.filename)).append(",");
            sb.append(item.captureTimestamp > 0 ? sdf.format(new Date(item.captureTimestamp)) : "").append(",");
            sb.append(item.width).append(",");
            sb.append(item.height).append(",");
            sb.append(item.duration).append(",");
            sb.append(item.sizeBytes).append(",");
            sb.append(item.quotaBytes > 0 ? "true" : "false").append(",");
            sb.append(item.quotaBytes).append(",");
            sb.append(item.isArchived).append(",");
            sb.append(item.isFavorite).append(",");
            sb.append(item.latitude).append(",");
            sb.append(item.longitude).append(",");
            sb.append(escapeCsv(item.caption)).append(",");
            sb.append(escapeCsv(item.remoteUrl)).append("\n");
        }
        return sb.toString();
    }

    private static String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private static boolean hasTable(SQLiteDatabase db, String tableName) {
        try (Cursor c = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type IN ('table','view') AND name=?", new String[]{tableName})) {
            return c != null && c.moveToFirst();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String col) {
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (c != null && c.moveToNext()) {
                if (col.equalsIgnoreCase(c.getString(1))) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static byte[] buildCollectionProtobuf(String colKey, String title, String albumId, String gaiaId) {
        try {
            ByteArrayOutputStream f1 = new ByteArrayOutputStream();
            writeStringField(f1, 1, colKey);

            ByteArrayOutputStream f2Inner = new ByteArrayOutputStream();
            writeStringField(f2Inner, 5, title);
            ByteArrayOutputStream f2 = new ByteArrayOutputStream();
            writeMsgField(f2, 2, f2Inner.toByteArray());

            ByteArrayOutputStream f4Sub = new ByteArrayOutputStream();
            if (albumId != null && !albumId.isEmpty()) {
                writeStringField(f4Sub, 3, albumId);
            }
            if (gaiaId != null && !gaiaId.isEmpty()) {
                writeStringField(f4Sub, 1, gaiaId);
            }
            ByteArrayOutputStream f4Inner = new ByteArrayOutputStream();
            writeStringField(f4Inner, 1, colKey);
            writeMsgField(f4Inner, 2, f4Sub.toByteArray());
            ByteArrayOutputStream f4 = new ByteArrayOutputStream();
            writeMsgField(f4, 4, f4Inner.toByteArray());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(f1.toByteArray());
            out.write(f2.toByteArray());
            out.write(f4.toByteArray());
            return out.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private static void writeVarint(OutputStream os, long val) throws IOException {
        while ((val & ~0x7FL) != 0) {
            os.write((int) ((val & 0x7F) | 0x80));
            val >>>= 7;
        }
        os.write((int) val);
    }

    private static void writeStringField(OutputStream os, int fieldNum, String s) throws IOException {
        if (s == null) return;
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarint(os, (fieldNum << 3) | 2);
        writeVarint(os, b.length);
        os.write(b);
    }

    private static void writeMsgField(OutputStream os, int fieldNum, byte[] body) throws IOException {
        if (body == null) return;
        writeVarint(os, (fieldNum << 3) | 2);
        writeVarint(os, body.length);
        os.write(body);
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
