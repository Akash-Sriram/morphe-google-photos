package app.morphe.extension.shared.patches;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.content.SharedPreferences;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.SafeLog;
import app.morphe.extension.shared.patches.PhenotypeFlagManager.Theme;

/**
 * Manages timeline folder filtering:
 * 1. Hiding the DCIM/Camera roll from the main Photos tab (#89).
 * 2. Allowing user-selected device folders to display on the main Photos timeline without cloud backup (#46).
 */
public final class PhotosTimelineFilter {

    private static final String PREFS_NAME = "morphe_settings";
    public static final String PREF_KEY_HIDE_CAMERA = "morphe_hide_camera_in_timeline";
    public static final String PREF_KEY_CUSTOM_BUCKETS = "morphe_custom_timeline_buckets";

    // Known bucket ID for Camera folder: /storage/emulated/0/DCIM/Camera hash
    public static final String DEFAULT_CAMERA_BUCKET_ID = "-1739773001";

    private static volatile Boolean cachedHideCamera = null;
    private static volatile Set<String> cachedCustomBuckets = null;

    private PhotosTimelineFilter() {}

    public static boolean isHideCameraEnabled() {
        return !getSelectedCustomBucketIds().contains(DEFAULT_CAMERA_BUCKET_ID);
    }

    public static void clearGridCache(Context ctx) {
        try {
            Context context = ctx != null ? ctx : Utils.getContextOrNull();
            if (context == null) return;
            String[] dbs = context.databaseList();
            if (dbs == null) return;
            for (String dbName : dbs) {
                if (dbName != null && dbName.startsWith("gphotos") && dbName.endsWith(".db")) {
                    try (android.database.sqlite.SQLiteDatabase db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)) {
                        String[] tables = new String[] {
                                "main_grid_queried_date_headers",
                                "main_grid_date_headers",
                                "main_grid_queried_count",
                                "all_media_count",
                                "all_media_count_nd_collapsed",
                                "day_segmented_date_headers",
                                "day_segmented_date_headers_nd_collapsed",
                                "header_map"
                        };
                        for (String t : tables) {
                            try {
                                db.execSQL("DELETE FROM " + t + ";");
                            } catch (Throwable ignored) {}
                        }
                        SafeLog.i("PhotosTimelineFilter", "Cleared grid cache tables in " + dbName);
                    } catch (Throwable t) {
                        SafeLog.e("PhotosTimelineFilter", "Failed to clear cache in " + dbName, t);
                    }
                }
            }
        } catch (Throwable t) {
            SafeLog.e("PhotosTimelineFilter", "Failed to clear grid cache", t);
        }
    }

    public static void setHideCameraEnabled(Context ctx, boolean enabled) {
        Set<String> current = new HashSet<>(getSelectedCustomBucketIds());
        if (enabled) {
            current.remove(DEFAULT_CAMERA_BUCKET_ID);
        } else {
            current.add(DEFAULT_CAMERA_BUCKET_ID);
        }
        setSelectedCustomBucketIds(ctx, current);
    }

    public static Set<String> getSelectedCustomBucketIds() {
        Set<String> cached = cachedCustomBuckets;
        if (cached != null) return cached;
        try {
            Context ctx = Utils.getContextOrNull();
            if (ctx != null) {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                if (!prefs.contains(PREF_KEY_CUSTOM_BUCKETS)) {
                    // Initial default: Camera is included unless legacy hide camera pref was set to true
                    Set<String> defaultSet = new HashSet<>();
                    boolean legacyHide = prefs.getBoolean(PREF_KEY_HIDE_CAMERA, false);
                    if (!legacyHide) {
                        defaultSet.add(DEFAULT_CAMERA_BUCKET_ID);
                    }
                    cachedCustomBuckets = defaultSet;
                    return cachedCustomBuckets;
                }
                Set<String> set = prefs.getStringSet(PREF_KEY_CUSTOM_BUCKETS, null);
                if (set != null) {
                    cachedCustomBuckets = new HashSet<>(set);
                    return cachedCustomBuckets;
                }
            }
        } catch (Throwable ignored) {}
        Set<String> fallback = new HashSet<>();
        fallback.add(DEFAULT_CAMERA_BUCKET_ID);
        return fallback;
    }

    public static void setSelectedCustomBucketIds(Context ctx, Set<String> bucketIds) {
        cachedCustomBuckets = new HashSet<>(bucketIds);
        cachedHideCamera = !bucketIds.contains(DEFAULT_CAMERA_BUCKET_ID);
        try {
            Context context = ctx != null ? ctx : Utils.getContextOrNull();
            if (context != null) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putStringSet(PREF_KEY_CUSTOM_BUCKETS, new HashSet<>(bucketIds))
                        .putBoolean(PREF_KEY_HIDE_CAMERA, cachedHideCamera)
                        .apply();
                clearGridCache(context);
            }
        } catch (Throwable t) {
            SafeLog.e("PhotosTimelineFilter", "Failed to save custom buckets", t);
        }
    }

    /**
     * Final query rewriter called from AllMediaQuery.aH() right before return-object.
     * Intercepts all queries generated for the home feed, MainGridCollection, headers, and counts.
     */
    public static String filterTimelineSqlQuery(String sql) {
        if (sql == null || sql.isEmpty()) return sql;

        Set<String> selectedBuckets = getSelectedCustomBucketIds();
        boolean hideCamera = !selectedBuckets.contains(DEFAULT_CAMERA_BUCKET_ID);

        Set<String> customNonCamera = new HashSet<>();
        for (String b : selectedBuckets) {
            if (!DEFAULT_CAMERA_BUCKET_ID.equals(b) && !"-1739773001".equals(b)) {
                customNonCamera.add(b);
            }
        }
        boolean hasCustom = !customNonCamera.isEmpty();

        String result = sql;

        // 1. Always neutralize stock screenshot bypasses so device folders outside Camera
        // (such as com.android.systemui Screenshots) only appear if explicitly selected in Custom Folders (#46).
        if (result.contains("((owner_package_name IN")) {
            result = result.replace("((owner_package_name IN", "(0=1 AND (owner_package_name IN");
        } else if (result.contains(" (owner_package_name IN")) {
            result = result.replace(" (owner_package_name IN", " (0=1 AND owner_package_name IN");
        }
        result = result.replace(" OR has_local = 1", "");
        result = result.replace(" OR (has_local = 1 AND in_camera_folder = 0)", "");
        result = result.replace(" OR (has_local = 1 AND media.in_camera_folder = 0)", "");

        // 2. Hide Camera (#89):
        if (hideCamera) {
            // Neutralize local_media.in_camera_folder = 1
            result = result.replace("local_media.in_camera_folder = 1", "local_media.in_camera_folder = -1");
            result = result.replace("local_media.in_camera_folder=1", "local_media.in_camera_folder=-1");
            result = result.replace("+local_in_camera_folder = 1", "+local_in_camera_folder = -1");
            result = result.replace("+local_in_camera_folder=1", "+local_in_camera_folder=-1");

            // Strip default camera bucket if present:
            result = result.replace("local_media.bucket_id IN (" + DEFAULT_CAMERA_BUCKET_ID + ")", "1=0");
            result = result.replace("local_media.bucket_id IN (" + DEFAULT_CAMERA_BUCKET_ID + ",", "local_media.bucket_id IN (");
            result = result.replace("," + DEFAULT_CAMERA_BUCKET_ID + ")", ")");
            result = result.replace("," + DEFAULT_CAMERA_BUCKET_ID + ",", ",");

            // Top-level enforcement: hide unbacked-up camera items, but preserve active backed-up cloud photos (#89):
            // Cloud photos only bypass camera hiding if they are actively in the cloud (not trashed or deleted):
            String activeCloudCondition = "(media.canonical_media_key IS NOT NULL " +
                    "AND (media.trash_timestamp IS NULL OR media.trash_timestamp = 0) " +
                    "AND media.is_deleted = 0 " +
                    "AND media.dedup_key IN (SELECT dedup_key FROM remote_media WHERE state = 0))";
            String cameraFilter = "(media.in_camera_folder = 0 OR " + activeCloudCondition + ")";
            if (result.contains("burst_media.bucket_id IS NULL AND")) {
                result = result.replace("burst_media.bucket_id IS NULL AND", "burst_media.bucket_id IS NULL AND " + cameraFilter + " AND");
            } else if (result.contains("burst_media.bucket_id IS NULL")) {
                result = result.replace("burst_media.bucket_id IS NULL", "burst_media.bucket_id IS NULL AND " + cameraFilter);
            }
        }

        // 3. Custom Folders (#46):
        if (hasCustom) {
            StringBuilder bucketList = new StringBuilder();
            boolean first = true;
            for (String b : customNonCamera) {
                if (!first) bucketList.append(",");
                bucketList.append(b);
                first = false;
            }
            String buckets = bucketList.toString();
            String customMatch = "(media.dedup_key IN (SELECT dedup_key FROM local_media WHERE bucket_id IN (" + buckets + ") " +
                    "AND (state = 0 OR state IS NULL) " +
                    "AND (desired_state != 2 OR desired_state IS NULL) " +
                    "AND (trash_timestamp IS NULL OR trash_timestamp = 0)) " +
                    "AND (media.trash_timestamp IS NULL OR media.trash_timestamp = 0) " +
                    "AND media.is_deleted = 0)";

            // Allow custom buckets through the is_hidden filter in inner subqueries and header queries:
            if (result.contains("+media.is_hidden = 0")) {
                result = result.replace("+media.is_hidden = 0", "(+media.is_hidden = 0 OR " + customMatch + ")");
            } else if (result.contains("media.is_hidden = 0")) {
                result = result.replace("media.is_hidden = 0", "(media.is_hidden = 0 OR " + customMatch + ")");
            } else if (result.contains("+is_hidden = 0")) {
                result = result.replace("+is_hidden = 0", "(+is_hidden = 0 OR " + customMatch + ")");
            } else if (result.contains("is_hidden = 0")) {
                result = result.replace("is_hidden = 0", "(is_hidden = 0 OR " + customMatch + ")");
            }

            // Also handle queries where local_media is directly filtered by in_camera_folder:
            if (result.contains("local_media.in_camera_folder = -1")) {
                result = result.replace("local_media.in_camera_folder = -1", "local_media.bucket_id IN (" + buckets + ")");
            } else if (result.contains("local_media.in_camera_folder = 1")) {
                result = result.replace("local_media.in_camera_folder = 1", "(local_media.in_camera_folder = 1 OR local_media.bucket_id IN (" + buckets + "))");
            }
        }

        boolean changed = !result.equals(sql);
        SafeLog.i("PhotosTimelineFilter", "filterTimelineSqlQuery: modified=" + changed);
        if (changed) {
            android.util.Log.i("PhotosTimelineFilter", "Original SQL: " + sql);
            android.util.Log.i("PhotosTimelineFilter", "Rewritten SQL: " + result);
        }

        return result;
    }

    public static class DeviceFolder {
        public final String bucketId;
        public final String displayName;
        public final String relativePath;
        public final int itemCount;
        public final boolean isCamera;

        public DeviceFolder(String bucketId, String displayName, String relativePath, int itemCount, boolean isCamera) {
            this.bucketId = bucketId;
            this.displayName = displayName;
            this.relativePath = relativePath;
            this.itemCount = itemCount;
            this.isCamera = isCamera;
        }
    }

    public static String extractRelativePath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) return "";
        int lastSlash = fullPath.lastIndexOf('/');
        if (lastSlash <= 0) return fullPath;
        String dir = fullPath.substring(0, lastSlash);

        if (dir.startsWith("/storage/emulated/")) {
            int nextSlash = dir.indexOf('/', "/storage/emulated/".length());
            if (nextSlash != -1 && nextSlash < dir.length() - 1) {
                return dir.substring(nextSlash + 1);
            }
        } else if (dir.startsWith("/storage/")) {
            int nextSlash = dir.indexOf('/', "/storage/".length());
            if (nextSlash != -1 && nextSlash < dir.length() - 1) {
                return "[SD Card] " + dir.substring(nextSlash + 1);
            }
        } else if (dir.startsWith("/sdcard/")) {
            return dir.substring("/sdcard/".length());
        }
        return dir;
    }

    public static boolean isTrashFolder(String relPath, String name) {
        if (name != null) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains(".trash") || lower.contains(".trashed") || lower.equals("trash") || lower.equals("bin") || lower.equals("recycle bin") || lower.contains("lost+found")) {
                return true;
            }
        }
        if (relPath != null) {
            String lower = relPath.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains(".trash") || lower.contains(".trashed") || lower.contains("/trash") || lower.contains("lost+found")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queries local_media and MediaStore for all device folders that currently contain images or videos.
     * Extracts relative paths to disambiguate folders with identical names under different parent directories.
     */
    public static List<DeviceFolder> getDeviceFolders(Context context) {
        Map<String, DeviceFolder> folders = new LinkedHashMap<>();

        // Always put Camera at the top
        folders.put(DEFAULT_CAMERA_BUCKET_ID, new DeviceFolder(
                DEFAULT_CAMERA_BUCKET_ID,
                "Camera",
                "DCIM/Camera",
                0,
                true
        ));

        // 1. Query local_media table in gphotos2.db (very fast, covers known media)
        try {
            String[] dbs = context.databaseList();
            if (dbs != null) {
                for (String dbName : dbs) {
                    if (dbName != null && dbName.startsWith("gphotos") && dbName.endsWith(".db")) {
                        try (android.database.sqlite.SQLiteDatabase db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)) {
                            try (Cursor c = db.rawQuery(
                                    "SELECT bucket_id, folder_name, filepath, COUNT(*) " +
                                    "FROM local_media WHERE (state = 0 OR state IS NULL) AND (trash_timestamp IS NULL OR trash_timestamp = 0) " +
                                    "GROUP BY bucket_id", null)) {
                                if (c != null) {
                                    while (c.moveToNext()) {
                                        String bId = c.getString(0);
                                        String fName = c.getString(1);
                                        String fPath = c.getString(2);
                                        int count = c.getInt(3);
                                        if (bId == null || bId.isEmpty()) continue;

                                        boolean isCam = DEFAULT_CAMERA_BUCKET_ID.equals(bId) ||
                                                (fName != null && "camera".equalsIgnoreCase(fName));

                                        String relPath = extractRelativePath(fPath);
                                        if (relPath.isEmpty() && isCam) relPath = "DCIM/Camera";

                                        if (!isCam && isTrashFolder(relPath, fName)) continue;

                                        String dispName = (fName != null && !fName.isEmpty()) ? fName : (isCam ? "Camera" : relPath);

                                        folders.put(bId, new DeviceFolder(bId, dispName, relPath, count, isCam));
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable t) {
            SafeLog.e("PhotosTimelineFilter", "Failed to query local_media for folders", t);
        }

        // 2. Query MediaStore to find any device folders on internal or external storage
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri uri = MediaStore.Files.getContentUri("external");
            String[] projection = new String[] {
                    MediaStore.Files.FileColumns.BUCKET_ID,
                    MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.Files.FileColumns.DATA
            };
            String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    + " OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

            try (Cursor cursor = resolver.query(uri, projection, selection, null, null)) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID);
                    int nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME);
                    int dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);

                    Map<String, Integer> msCounts = new HashMap<>();
                    Map<String, String> msPaths = new HashMap<>();
                    Map<String, String> msNames = new HashMap<>();

                    while (cursor.moveToNext()) {
                        String id = cursor.getString(idCol);
                        if (id == null || id.isEmpty()) continue;

                        int current = msCounts.containsKey(id) ? msCounts.get(id) : 0;
                        msCounts.put(id, current + 1);

                        if (!msPaths.containsKey(id) && dataCol != -1) {
                            String d = cursor.getString(dataCol);
                            if (d != null) msPaths.put(id, d);
                        }
                        if (!msNames.containsKey(id) && nameCol != -1) {
                            String n = cursor.getString(nameCol);
                            if (n != null) msNames.put(id, n);
                        }
                    }

                    for (Map.Entry<String, Integer> entry : msCounts.entrySet()) {
                        String id = entry.getKey();
                        int count = entry.getValue();
                        String fPath = msPaths.get(id);
                        String fName = msNames.get(id);

                        boolean isCam = DEFAULT_CAMERA_BUCKET_ID.equals(id) ||
                                (fName != null && "camera".equalsIgnoreCase(fName));

                        String relPath = extractRelativePath(fPath);
                        if (relPath.isEmpty() && isCam) relPath = "DCIM/Camera";

                        if (!isCam && isTrashFolder(relPath, fName)) continue;

                        String dispName = (fName != null && !fName.isEmpty()) ? fName : (isCam ? "Camera" : relPath);

                        if (folders.containsKey(id)) {
                            DeviceFolder existing = folders.get(id);
                            int finalCount = Math.max(existing.itemCount, count);
                            String finalPath = existing.relativePath.isEmpty() ? relPath : existing.relativePath;
                            folders.put(id, new DeviceFolder(id, existing.displayName, finalPath, finalCount, existing.isCamera));
                        } else {
                            folders.put(id, new DeviceFolder(id, dispName, relPath, count, isCam));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            SafeLog.e("PhotosTimelineFilter", "Failed to query MediaStore device folders", t);
        }

        List<DeviceFolder> resultList = new ArrayList<>(folders.values());
        Collections.sort(resultList, (a, b) -> {
            if (a.isCamera && !b.isCamera) return -1;
            if (!a.isCamera && b.isCamera) return 1;
            int nameCmp = a.displayName.compareToIgnoreCase(b.displayName);
            if (nameCmp != 0) return nameCmp;
            return a.relativePath.compareToIgnoreCase(b.relativePath);
        });
        return resultList;
    }

    /**
     * Displays a clean M3 folder checklist dialog with two-line items (Title + Path & Count)
     * to organically disambiguate folders with identical child names across different parent directories.
     */
    public static void showFolderPickerDialog(Activity activity, Theme theme) {
        float density = activity.getResources().getDisplayMetrics().density;
        List<DeviceFolder> allFolders = getDeviceFolders(activity);
        Set<String> currentlySelected = new HashSet<>(getSelectedCustomBucketIds());
        final Set<String> workingSet = new HashSet<>(currentlySelected);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) (18 * density);
        root.setPadding(padH, (int) (4 * density), padH, (int) (8 * density));

        TextView tvDesc = new TextView(activity);
        tvDesc.setText("Select device folders to display directly on your main Photos timeline. Uncheck Camera to hide your camera roll from the home feed without deleting photos:");
        tvDesc.setTextSize(12.5f);
        tvDesc.setTextColor(theme.textSecondary);
        tvDesc.setLineSpacing(0, 1.15f);
        root.addView(tvDesc);

        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (340 * density)));
        scroll.setVerticalScrollBarEnabled(true);

        LinearLayout listLayout = new LinearLayout(activity);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

        for (DeviceFolder folder : allFolders) {
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            int pV = (int) (10 * density);
            item.setPadding((int) (6 * density), pV, (int) (6 * density), pV);

            CheckBox cb = new CheckBox(activity);
            cb.setChecked(workingSet.contains(folder.bucketId));
            cb.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    workingSet.add(folder.bucketId);
                } else {
                    workingSet.remove(folder.bucketId);
                }
            });

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textColLp.setMargins((int) (10 * density), 0, 0, 0);
            textCol.setLayoutParams(textColLp);

            TextView tvName = new TextView(activity);
            tvName.setText(folder.displayName);
            tvName.setTextSize(15f);
            tvName.setTextColor(theme.textPrimary);
            tvName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            textCol.addView(tvName);

            TextView tvSub = new TextView(activity);
            String sub = folder.relativePath;
            if (folder.isCamera) {
                sub += " • Primary camera roll";
            } else if (folder.itemCount > 0) {
                sub += " • " + folder.itemCount + (folder.itemCount == 1 ? " item" : " items");
            }
            tvSub.setText(sub);
            tvSub.setTextSize(12f);
            tvSub.setTextColor(theme.textSecondary);
            tvSub.setLineSpacing(0, 1.1f);
            textCol.addView(tvSub);

            item.addView(cb);
            item.addView(textCol);

            item.setOnClickListener(v -> cb.setChecked(!cb.isChecked()));

            listLayout.addView(item);
        }

        scroll.addView(listLayout);
        root.addView(scroll);

        PhenotypeFlagManager.createM3ActionDialog(
                activity,
                "Customize Timeline Folders",
                root,
                "Apply & Save",
                () -> {
                    setSelectedCustomBucketIds(activity, workingSet);
                    Toast.makeText(activity, "Timeline folders updated! Pull down on Photos tab to refresh.", Toast.LENGTH_SHORT).show();
                }
        ).show();
    }
}
