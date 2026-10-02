package app.morphe.extension.shared.patches;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilderFactory;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.patches.flags.PhotoFlagsRegistry;
import app.morphe.extension.shared.patches.flags.PhotoFlagsRegistry.CuratedFlag;

/**
 * High-performance Material 3 Phenotype Flag Manager for Morphe Google Photos.
 * Capable of smoothly displaying and streaming 1,000+ to 5,000+ flags via virtualized
 * ListView recycling, debounced filtering, and asynchronous background import pipelines.
 */
public final class PhenotypeFlagManager {

    private static final String PREF_NAME = "com.google.android.apps.photos.phenotype";
    private static final String SETTINGS_PILL_TAG = "morphe_photos_flags_pill";
    public static final String CUSTOM_FLAGS_KEY = "_morphe_custom_flag_ids";
    public static final String SEEDED_MARKER = "_morphe_flags_seeded";

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    // Dynamic Material 3 Palette Engine
    public static final class Theme {
        public final boolean isDark;
        public final int bg;
        public final int surface;
        public final int surfaceContainer;
        public final int headerCardBg;
        public final int card;
        public final int cardActive;
        public final int cardBorder;
        public final int cardBorderActive;
        public final int primary;
        public final int primaryContainer;
        public final int onPrimary;
        public final int onPrimaryContainer;
        public final int textPrimary;
        public final int textSecondary;
        public final int textTertiary;
        public final int outline;
        public final int searchInputBg;
        public final int disableAllText;
        public final int disableAllBg;
        public final int enableAllText;
        public final int enableAllBg;

        public Theme(boolean isDark, int bg, int surface, int surfaceContainer, int headerCardBg,
                     int card, int cardActive, int cardBorder, int cardBorderActive,
                     int primary, int primaryContainer, int onPrimary, int onPrimaryContainer,
                     int textPrimary, int textSecondary, int textTertiary, int outline,
                     int searchInputBg, int disableAllText, int disableAllBg,
                     int enableAllText, int enableAllBg) {
            this.isDark = isDark;
            this.bg = bg;
            this.surface = surface;
            this.surfaceContainer = surfaceContainer;
            this.headerCardBg = headerCardBg;
            this.card = card;
            this.cardActive = cardActive;
            this.cardBorder = cardBorder;
            this.cardBorderActive = cardBorderActive;
            this.primary = primary;
            this.primaryContainer = primaryContainer;
            this.onPrimary = onPrimary;
            this.onPrimaryContainer = onPrimaryContainer;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textTertiary = textTertiary;
            this.outline = outline;
            this.searchInputBg = searchInputBg;
            this.disableAllText = disableAllText;
            this.disableAllBg = disableAllBg;
            this.enableAllText = enableAllText;
            this.enableAllBg = enableAllBg;
        }

        public static Theme get(Context context) {
            boolean isDark = isDarkTheme(context);
            if (isDark) {
                return new Theme(
                        true,
                        0xFF121414, // bg
                        0xFF1A1C1C, // surface
                        0xFF202322, // surfaceContainer
                        0xFF242827, // headerCardBg
                        0xFF1D201F, // card
                        0xFF152A26, // cardActive (subtle dark teal tint)
                        0xFF2F3432, // cardBorder
                        0xFF4CDAC6, // cardBorderActive
                        0xFF4CDAC6, // primary (M3 teal)
                        0xFF005048, // primaryContainer
                        0xFF003731, // onPrimary
                        0xFF70F7E3, // onPrimaryContainer
                        0xFFE1E3E1, // textPrimary
                        0xFF8A938F, // textSecondary
                        0xFF6C7572, // textTertiary
                        0xFF3A423F, // outline
                        0xFF292C2B, // searchInputBg
                        0xFFFFB4AB, // disableAllText (M3 error container on dark)
                        0xFF5A1210, // disableAllBg
                        0xFF70F7E3, // enableAllText
                        0xFF005048  // enableAllBg
                );
            } else {
                return new Theme(
                        false,
                        0xFFF5F7F6, // bg
                        0xFFFFFFFF, // surface
                        0xFFF0F4F2, // surfaceContainer
                        0xFFEAEFEB, // headerCardBg
                        0xFFFFFFFF, // card
                        0xFFE6F4F1, // cardActive
                        0xFFD8E3E0, // cardBorder
                        0xFF006A60, // cardBorderActive
                        0xFF006A60, // primary
                        0xFFCCE8E3, // primaryContainer
                        0xFFFFFFFF, // onPrimary
                        0xFF00201C, // onPrimaryContainer
                        0xFF191C1D, // textPrimary
                        0xFF53605D, // textSecondary
                        0xFF8B9B97, // textTertiary
                        0xFFD8E3E0, // outline
                        0xFFEAEFEB, // searchInputBg
                        0xFFBA1A1A, // disableAllText
                        0xFFFFDAD6, // disableAllBg
                        0xFF006A60, // enableAllText
                        0xFFCCE8E3  // enableAllBg
                );
            }
        }
    }

    public static boolean isDarkTheme(Context context) {
        if (context != null) {
            try {
                int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (mode == Configuration.UI_MODE_NIGHT_YES) return true;
                if (mode == Configuration.UI_MODE_NIGHT_NO) return false;
            } catch (Throwable ignored) {}
        }
        return app.morphe.extension.shared.Utils.isDarkModeEnabled();
    }

    // Material 3 Fallback Palette
    private static final int M3_BG = 0xFFF5F7F6;
    private static final int M3_SURFACE = 0xFFFFFFFF;
    private static final int M3_CARD = 0xFFFFFFFF;
    private static final int M3_CARD_ACTIVE = 0xFFE6F4F1;
    private static final int M3_PRIMARY = 0xFF006A60;
    private static final int M3_PRIMARY_CONTAINER = 0xFFCCE8E3;
    private static final int M3_ON_PRIMARY = 0xFFFFFFFF;
    private static final int M3_TEXT_PRIMARY = 0xFF191C1D;
    private static final int M3_TEXT_SECONDARY = 0xFF53605D;
    private static final int M3_OUTLINE = 0xFFD8E3E0;

    private PhenotypeFlagManager() {}

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Settings Activity Floating Pill Injection
    // ─────────────────────────────────────────────────────────────────────────

    public static void injectSettingsCard(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        activity.runOnUiThread(() -> {
            try {
                View decor = activity.getWindow().getDecorView();
                decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        try {
                            FrameLayout content = activity.findViewById(android.R.id.content);
                            if (content != null && content.findViewWithTag(SETTINGS_PILL_TAG) == null) {
                                LinearLayout pill = createFloatingPill(activity);
                                content.addView(pill);
                                Logger.printInfo(() -> "Photos Flags floating pill attached to SettingsActivity");
                            }
                        } catch (Throwable t) {
                            Logger.printException(() -> "Error attaching Photos Flags pill", t);
                        }
                    }
                });
            } catch (Throwable t) {
                Logger.printException(() -> "Error in injectSettingsCard", t);
            }
        });
    }

    private static LinearLayout createFloatingPill(Activity activity) {
        float density = activity.getResources().getDisplayMetrics().density;
        Theme theme = Theme.get(activity);
        LinearLayout dock = new LinearLayout(activity);
        dock.setTag(SETTINGS_PILL_TAG);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.setMargins((int) (12 * density), 0, (int) (12 * density), (int) (20 * density));
        dock.setLayoutParams(lp);

        int btnHeight = (int) (44 * density);
        int marginH = (int) (4 * density);

        // Button 1: Flag Manager
        LinearLayout pillFlags = new LinearLayout(activity);
        pillFlags.setOrientation(LinearLayout.HORIZONTAL);
        pillFlags.setGravity(Gravity.CENTER);
        pillFlags.setClickable(true);
        pillFlags.setFocusable(true);
        pillFlags.setElevation(10 * density);
        LinearLayout.LayoutParams lpFlags = new LinearLayout.LayoutParams(0, btnHeight, 1.0f);
        lpFlags.setMargins(marginH, 0, marginH, 0);
        pillFlags.setLayoutParams(lpFlags);

        GradientDrawable bgFlags = new GradientDrawable();
        bgFlags.setCornerRadius(22 * density);
        bgFlags.setColor(theme.primary);
        pillFlags.setBackground(bgFlags);

        TextView iconFlags = new TextView(activity);
        iconFlags.setText("✨");
        iconFlags.setTextSize(15);
        iconFlags.setPadding(0, 0, (int) (4 * density), 0);
        pillFlags.addView(iconFlags);

        TextView labelFlags = new TextView(activity);
        labelFlags.setText("Flags");
        labelFlags.setTextSize(13);
        labelFlags.setTextColor(theme.onPrimary);
        labelFlags.setTypeface(null, Typeface.BOLD);
        pillFlags.addView(labelFlags);
        pillFlags.setOnClickListener(v -> showFlagManagerDialog(activity));
        dock.addView(pillFlags);

        // Button 2: Library Toolkit
        LinearLayout pillToolkit = new LinearLayout(activity);
        pillToolkit.setOrientation(LinearLayout.HORIZONTAL);
        pillToolkit.setGravity(Gravity.CENTER);
        pillToolkit.setClickable(true);
        pillToolkit.setFocusable(true);
        pillToolkit.setElevation(10 * density);
        LinearLayout.LayoutParams lpToolkit = new LinearLayout.LayoutParams(0, btnHeight, 1.0f);
        lpToolkit.setMargins(marginH, 0, marginH, 0);
        pillToolkit.setLayoutParams(lpToolkit);

        GradientDrawable bgToolkit = new GradientDrawable();
        bgToolkit.setCornerRadius(22 * density);
        bgToolkit.setColor(theme.isDark ? 0xFF004D40 : 0xFF005A54);
        pillToolkit.setBackground(bgToolkit);

        TextView iconToolkit = new TextView(activity);
        iconToolkit.setText("🛠️");
        iconToolkit.setTextSize(15);
        iconToolkit.setPadding(0, 0, (int) (4 * density), 0);
        pillToolkit.addView(iconToolkit);

        TextView labelToolkit = new TextView(activity);
        labelToolkit.setText("Toolkit");
        labelToolkit.setTextSize(13);
        labelToolkit.setTextColor(0xFFFFFFFF);
        labelToolkit.setTypeface(null, Typeface.BOLD);
        pillToolkit.addView(labelToolkit);
        pillToolkit.setOnClickListener(v -> app.morphe.extension.shared.patches.toolkit.PhotosToolkitDialog.show(activity));
        dock.addView(pillToolkit);

        // Button 3: Diagnostics & Logs
        LinearLayout pillLogs = new LinearLayout(activity);
        pillLogs.setOrientation(LinearLayout.HORIZONTAL);
        pillLogs.setGravity(Gravity.CENTER);
        pillLogs.setClickable(true);
        pillLogs.setFocusable(true);
        pillLogs.setElevation(10 * density);
        LinearLayout.LayoutParams lpLogs = new LinearLayout.LayoutParams(0, btnHeight, 1.0f);
        lpLogs.setMargins(marginH, 0, marginH, 0);
        pillLogs.setLayoutParams(lpLogs);

        GradientDrawable bgLogs = new GradientDrawable();
        bgLogs.setCornerRadius(22 * density);
        bgLogs.setColor(theme.isDark ? 0xFF282F2E : 0xFF2E3836);
        pillLogs.setBackground(bgLogs);

        TextView iconLogs = new TextView(activity);
        iconLogs.setText("📊");
        iconLogs.setTextSize(15);
        iconLogs.setPadding(0, 0, (int) (4 * density), 0);
        pillLogs.addView(iconLogs);

        TextView labelLogs = new TextView(activity);
        labelLogs.setText("Logs");
        labelLogs.setTextSize(13);
        labelLogs.setTextColor(0xFFFFFFFF);
        labelLogs.setTypeface(null, Typeface.BOLD);
        pillLogs.addView(labelLogs);
        pillLogs.setOnClickListener(v -> app.morphe.extension.shared.diagnostics.DiagnosticsDialog.show(activity));
        dock.addView(pillLogs);

        return dock;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Display Model & Virtualized Adapter
    // ─────────────────────────────────────────────────────────────────────────

    private static final int ITEM_TYPE_HEADER = 0;
    private static final int ITEM_TYPE_FLAG = 1;

    public static class DisplayItem {
        final int type;
        final String headerTitle;
        final CuratedFlag curatedFlag;
        final String customKey;
        Object value;

        DisplayItem(String headerTitle) {
            this.type = ITEM_TYPE_HEADER;
            this.headerTitle = headerTitle;
            this.curatedFlag = null;
            this.customKey = null;
            this.value = null;
        }

        DisplayItem(CuratedFlag curatedFlag, Object value) {
            this.type = ITEM_TYPE_FLAG;
            this.headerTitle = null;
            this.curatedFlag = curatedFlag;
            this.customKey = curatedFlag.key;
            this.value = value;
        }

        DisplayItem(String customKey, Object value) {
            this.type = ITEM_TYPE_FLAG;
            this.headerTitle = null;
            this.curatedFlag = null;
            this.customKey = customKey;
            this.value = value;
        }

        boolean isHeader() { return type == ITEM_TYPE_HEADER; }
        String getKey() { return customKey; }
        String getTitle() { return curatedFlag != null ? curatedFlag.title : customKey; }
        String getDescription() { return curatedFlag != null ? curatedFlag.description : null; }
        String getTrigger() { return curatedFlag != null ? curatedFlag.triggerTarget : null; }
        String getCategory() { return curatedFlag != null ? curatedFlag.category : null; }
    }

    private static class FlagViewHolder {
        LinearLayout root;
        TextView tvTitle;
        TextView tvTriggerBadge;
        TextView tvDesc;
        TextView tvKey;
        Switch swToggle;
        TextView valChip;
    }

    private static class HeaderViewHolder {
        LinearLayout root;
        TextView tvTitle;
        TextView tvTrigger;
        TextView tvArrow;
        TextView btnToggleAll;
    }

    public static class FlagAdapter extends BaseAdapter {
        private final Activity activity;
        private final SharedPreferences prefs;
        private final float density;
        private final Theme theme;
        private final List<DisplayItem> allItems = new ArrayList<>();
        private final List<DisplayItem> displayedItems = new ArrayList<>();
        private final Set<String> expandedCategories = new HashSet<>();
        private final Set<String> searchCollapsedCategories = new HashSet<>();
        private final TextView tvSub;
        private final LinearLayout emptyContainer;
        private int totalFlagsCount = 0;
        private int activeFlagsCount = 0;
        private String currentFilterQuery = "";

        public void expandAll() {
            if (!currentFilterQuery.isEmpty()) {
                searchCollapsedCategories.clear();
            } else {
                for (DisplayItem it : allItems) {
                    if (it.isHeader()) {
                        expandedCategories.add(it.headerTitle);
                    }
                }
            }
            filter(currentFilterQuery);
        }

        public void collapseAll() {
            if (!currentFilterQuery.isEmpty()) {
                for (DisplayItem it : allItems) {
                    if (it.isHeader()) {
                        searchCollapsedCategories.add(it.headerTitle);
                    }
                }
            } else {
                expandedCategories.clear();
            }
            filter(currentFilterQuery);
        }

        public FlagAdapter(Activity activity, SharedPreferences prefs, TextView tvSub, LinearLayout emptyContainer) {
            this.activity = activity;
            this.prefs = prefs;
            this.density = activity.getResources().getDisplayMetrics().density;
            this.theme = Theme.get(activity);
            this.tvSub = tvSub;
            this.emptyContainer = emptyContainer;
        }

        @Override public int getCount() { return displayedItems.size(); }
        @Override public DisplayItem getItem(int position) { return displayedItems.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return displayedItems.get(position).type; }
        @Override public boolean isEnabled(int position) { return !displayedItems.get(position).isHeader(); }

        private static boolean isFlagEnabled(SharedPreferences prefs, CuratedFlag cf) {
            if (!prefs.contains(cf.key)) return false;
            if (cf.type == PhotoFlagsRegistry.FlagType.BOOLEAN) {
                try {
                    return prefs.getBoolean(cf.key, false);
                } catch (Exception e) {
                    return false;
                }
            } else if (cf.type == PhotoFlagsRegistry.FlagType.LONG) {
                try {
                    return prefs.getLong(cf.key, 0L) > 0;
                } catch (Exception e) {
                    return false;
                }
            }
            return true;
        }

        private Object sanitizeValue(String key, Object rawVal) {
            if (rawVal instanceof String) {
                String s = ((String) rawVal).trim();
                int h = s.indexOf('#'); if (h != -1) s = s.substring(0, h).trim();
                int sl = s.indexOf("//"); if (sl != -1) s = s.substring(0, sl).trim();
                int sm = s.indexOf(';'); if (sm != -1) s = s.substring(0, sm).trim();
                s = stripQuotes(s);
                if (s.equalsIgnoreCase("true")) {
                    prefs.edit().remove(key).putBoolean(key, true).apply();
                    return Boolean.TRUE;
                } else if (s.equalsIgnoreCase("false")) {
                    prefs.edit().remove(key).putBoolean(key, false).apply();
                    return Boolean.FALSE;
                } else if (s.matches("^-?\\d+$")) {
                    try {
                        long lv = Long.parseLong(s);
                        prefs.edit().remove(key).putLong(key, lv).apply();
                        return lv;
                    } catch (Exception ignored) {}
                }
                return s;
            }
            return rawVal;
        }

        public void reloadData() {
            allItems.clear();
            Map<String, ?> all = prefs.getAll();
            int totalCount = 0;

            // 1. Curated Flags that are configured in SharedPreferences
            List<String> categories = PhotoFlagsRegistry.getCategories();
            for (String cat : categories) {
                List<CuratedFlag> flagsInCat = PhotoFlagsRegistry.getFlagsForCategory(cat);
                if (flagsInCat.isEmpty()) continue;
                DisplayItem catHeader = new DisplayItem(cat);
                List<DisplayItem> catFlags = new ArrayList<>();
                for (CuratedFlag f : flagsInCat) {
                    if (!all.containsKey(f.key)) continue; // Only show configured flags
                    totalCount++;
                    Object raw = all.get(f.key);
                    Object v = sanitizeValue(f.key, raw);
                    catFlags.add(new DisplayItem(f, v));
                }
                if (!catFlags.isEmpty()) {
                    allItems.add(catHeader);
                    allItems.addAll(catFlags);
                }
            }

            // 2. Custom / Imported Flags (Strictly non-curated overrides)
            Set<String> customKeys = prefs.getStringSet(CUSTOM_FLAGS_KEY, Collections.emptySet());
            Set<String> allCustom = new HashSet<>();
            for (String k : customKeys) {
                if (!k.startsWith("_") && !k.startsWith("__") && !PhotoFlagsRegistry.FLAG_MAP.containsKey(k)) {
                    allCustom.add(k);
                }
            }
            for (String k : all.keySet()) {
                if (!k.startsWith("_") && !k.startsWith("__") && !PhotoFlagsRegistry.FLAG_MAP.containsKey(k)) {
                    allCustom.add(k);
                }
            }

            if (!allCustom.isEmpty()) {
                List<String> sortedKeys = new ArrayList<>(allCustom);
                Collections.sort(sortedKeys);
                String headerTitle = "Custom Overrides (" + sortedKeys.size() + ")";
                allItems.add(new DisplayItem(headerTitle));
                for (String k : sortedKeys) {
                    totalCount++;
                    Object v = sanitizeValue(k, all.get(k));
                    allItems.add(new DisplayItem(k, v));
                }
            }

            this.totalFlagsCount = totalCount;
            filter(currentFilterQuery);
        }

        private void updateSubtitleText(int flagsShown, int totalMatched) {
            if (totalFlagsCount == 0) {
                emptyContainer.setVisibility(View.VISIBLE);
                renderEmptySlate(activity, emptyContainer, density, theme);
                tvSub.setText("0 Flags Configured");
            } else if (!currentFilterQuery.isEmpty() && totalMatched == 0) {
                emptyContainer.setVisibility(View.VISIBLE);
                renderEmptyMessage(activity, emptyContainer, "No flags matched \"" + currentFilterQuery + "\"", density, theme);
                tvSub.setText("0 Flags Matched (" + totalFlagsCount + " Total)");
            } else {
                emptyContainer.setVisibility(View.GONE);
                if (currentFilterQuery.isEmpty()) {
                    tvSub.setText(totalFlagsCount + " Flags Configured");
                } else {
                    tvSub.setText(totalMatched + " Found (" + totalFlagsCount + " Total)");
                }
            }
        }

        private void updateSubtitleText() {
            int flagsShown = 0;
            for (DisplayItem it : displayedItems) {
                if (!it.isHeader()) flagsShown++;
            }
            updateSubtitleText(flagsShown, countTotalMatches(currentFilterQuery));
        }

        private int countTotalMatches(String query) {
            if (query == null || query.isEmpty()) return totalFlagsCount;
            int count = 0;
            for (DisplayItem it : allItems) {
                if (!it.isHeader()) {
                    String target = it.getKey() + " " + it.getTitle() + " " + (it.getDescription() != null ? it.getDescription() : "") + " " + (it.getTrigger() != null ? it.getTrigger() : "") + " " + it.value;
                    if (target.toLowerCase().contains(query)) {
                        count++;
                    }
                }
            }
            return count;
        }

        private int countMatchesInCategory(String cat, String query) {
            if (query == null || query.isEmpty()) return 0;
            int count = 0;
            boolean inCat = false;
            for (DisplayItem it : allItems) {
                if (it.isHeader()) {
                    inCat = cat.equals(it.headerTitle);
                } else if (inCat) {
                    String target = it.getKey() + " " + it.getTitle() + " " + (it.getDescription() != null ? it.getDescription() : "") + " " + (it.getTrigger() != null ? it.getTrigger() : "") + " " + it.value;
                    if (target.toLowerCase().contains(query)) {
                        count++;
                    }
                }
            }
            return count;
        }

        public void filter(String query) {
            String newQuery = query == null ? "" : query.toLowerCase().trim();
            if (!newQuery.equals(this.currentFilterQuery)) {
                searchCollapsedCategories.clear();
            }
            this.currentFilterQuery = newQuery;
            displayedItems.clear();

            DisplayItem currentHeader = null;
            List<DisplayItem> currentSection = new ArrayList<>();
            int totalMatched = 0;

            for (DisplayItem it : allItems) {
                if (it.isHeader()) {
                    if (currentHeader != null) {
                        if (currentFilterQuery.isEmpty() || !currentSection.isEmpty()) {
                            displayedItems.add(currentHeader);
                            boolean isExpanded = currentFilterQuery.isEmpty()
                                    ? expandedCategories.contains(currentHeader.headerTitle)
                                    : !searchCollapsedCategories.contains(currentHeader.headerTitle);
                            if (isExpanded) {
                                displayedItems.addAll(currentSection);
                            }
                            totalMatched += currentSection.size();
                        }
                    }
                    currentHeader = it;
                    currentSection.clear();
                } else {
                    if (currentFilterQuery.isEmpty()) {
                        currentSection.add(it);
                    } else {
                        String target = it.getKey() + " " + it.getTitle() + " " + (it.getDescription() != null ? it.getDescription() : "") + " " + (it.getTrigger() != null ? it.getTrigger() : "") + " " + it.value;
                        if (target.toLowerCase().contains(currentFilterQuery)) {
                            currentSection.add(it);
                        }
                    }
                }
            }
            if (currentHeader != null) {
                if (currentFilterQuery.isEmpty() || !currentSection.isEmpty()) {
                    displayedItems.add(currentHeader);
                    boolean isExpanded = currentFilterQuery.isEmpty()
                            ? expandedCategories.contains(currentHeader.headerTitle)
                            : !searchCollapsedCategories.contains(currentHeader.headerTitle);
                    if (isExpanded) {
                        displayedItems.addAll(currentSection);
                    }
                    totalMatched += currentSection.size();
                }
            }

            notifyDataSetChanged();

            int flagsShown = 0;
            for (DisplayItem it : displayedItems) {
                if (!it.isHeader()) flagsShown++;
            }
            updateSubtitleText(flagsShown, totalMatched);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DisplayItem item = getItem(position);

            if (item.isHeader()) {
                HeaderViewHolder hHolder;
                if (convertView != null && convertView.getTag() instanceof HeaderViewHolder) {
                    hHolder = (HeaderViewHolder) convertView.getTag();
                } else {
                    hHolder = new HeaderViewHolder();
                    LinearLayout card = new LinearLayout(activity);
                    card.setOrientation(LinearLayout.VERTICAL);
                    int padH = (int) (14 * density);
                    int padV = (int) (10 * density);
                    card.setPadding(padH, padV, padH, padV);

                    LinearLayout topRow = new LinearLayout(activity);
                    topRow.setOrientation(LinearLayout.HORIZONTAL);
                    topRow.setGravity(Gravity.CENTER_VERTICAL);

                    TextView tvTitle = new TextView(activity);
                    tvTitle.setTextSize(13);
                    tvTitle.setTypeface(null, Typeface.BOLD);
                    LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    tvTitle.setLayoutParams(tLp);
                    topRow.addView(tvTitle);

                    TextView tvArrow = new TextView(activity);
                    tvArrow.setTextSize(14);
                    tvArrow.setTypeface(null, Typeface.BOLD);
                    int aPadH = (int) (6 * density);
                    tvArrow.setPadding(aPadH, 0, aPadH, 0);
                    topRow.addView(tvArrow);

                    TextView btnToggle = new TextView(activity);
                    btnToggle.setTextSize(11);
                    btnToggle.setTypeface(null, Typeface.BOLD);
                    int bPadH = (int) (10 * density);
                    int bPadV = (int) (5 * density);
                    btnToggle.setPadding(bPadH, bPadV, bPadH, bPadV);
                    btnToggle.setClickable(true);
                    topRow.addView(btnToggle);
                    card.addView(topRow);

                    TextView tvTrigger = new TextView(activity);
                    tvTrigger.setTextSize(11);
                    tvTrigger.setPadding(0, (int) (3 * density), 0, 0);
                    card.addView(tvTrigger);

                    hHolder.root = card;
                    hHolder.tvTitle = tvTitle;
                    hHolder.tvArrow = tvArrow;
                    hHolder.tvTrigger = tvTrigger;
                    hHolder.btnToggleAll = btnToggle;

                    convertView = card;
                    convertView.setTag(hHolder);
                }

                // Re-apply styling on recycled views
                hHolder.root.setBackground(createRoundedDrawable(theme.headerCardBg, 14 * density));
                hHolder.tvTitle.setTextColor(theme.primary);
                hHolder.tvArrow.setTextColor(theme.primary);
                hHolder.tvTrigger.setTextColor(theme.textSecondary);

                final String cat = item.headerTitle;
                final List<CuratedFlag> flagsInCat = PhotoFlagsRegistry.getFlagsForCategory(cat);

                int totalInCat = flagsInCat.size();
                int enabledInCat = 0;
                for (CuratedFlag cf : flagsInCat) {
                    if (isFlagEnabled(prefs, cf)) {
                        enabledInCat++;
                    }
                }

                boolean isExpanded = currentFilterQuery.isEmpty()
                        ? expandedCategories.contains(cat)
                        : !searchCollapsedCategories.contains(cat);

                hHolder.tvArrow.setText(isExpanded ? "▼" : "▶");
                if (currentFilterQuery.isEmpty()) {
                    hHolder.tvTitle.setText(cat + (totalInCat > 0 ? " (" + enabledInCat + "/" + totalInCat + ")" : ""));
                } else {
                    int matchedInCat = countMatchesInCategory(cat, currentFilterQuery);
                    hHolder.tvTitle.setText(cat + " (" + matchedInCat + " matched)");
                }
                hHolder.tvTrigger.setText(PhotoFlagsRegistry.getCategoryTriggerDescription(cat));

                View.OnClickListener toggleCollapse = v -> {
                    if (currentFilterQuery.isEmpty()) {
                        if (expandedCategories.contains(cat)) {
                            expandedCategories.remove(cat);
                        } else {
                            expandedCategories.add(cat);
                        }
                    } else {
                        if (searchCollapsedCategories.contains(cat)) {
                            searchCollapsedCategories.remove(cat);
                        } else {
                            searchCollapsedCategories.add(cat);
                        }
                    }
                    filter(currentFilterQuery);
                };

                hHolder.root.setClickable(true);
                hHolder.root.setOnClickListener(toggleCollapse);
                hHolder.tvTitle.setOnClickListener(toggleCollapse);
                hHolder.tvArrow.setOnClickListener(toggleCollapse);
                hHolder.tvTrigger.setOnClickListener(toggleCollapse);

                if (totalInCat > 0) {
                    hHolder.btnToggleAll.setVisibility(View.VISIBLE);
                    boolean allEnabled = enabledInCat == totalInCat;
                    if (allEnabled) {
                        hHolder.btnToggleAll.setText("DISABLE ALL");
                        hHolder.btnToggleAll.setTextColor(theme.disableAllText);
                        hHolder.btnToggleAll.setBackground(createRoundedDrawable(theme.disableAllBg, 8 * density));
                    } else {
                        hHolder.btnToggleAll.setText("ENABLE ALL");
                        hHolder.btnToggleAll.setTextColor(theme.enableAllText);
                        hHolder.btnToggleAll.setBackground(createRoundedDrawable(theme.enableAllBg, 8 * density));
                    }

                    hHolder.btnToggleAll.setOnClickListener(v -> {
                        boolean targetState = !allEnabled;
                        SharedPreferences.Editor edit = prefs.edit();
                        for (CuratedFlag cf : flagsInCat) {
                            if (cf.type == PhotoFlagsRegistry.FlagType.BOOLEAN) {
                                if (targetState) {
                                    edit.putBoolean(cf.key, true);
                                } else {
                                    edit.remove(cf.key);
                                }
                            } else if (cf.type == PhotoFlagsRegistry.FlagType.LONG) {
                                if (targetState) {
                                    edit.putLong(cf.key, ((Number) cf.defaultValue).longValue());
                                } else {
                                    edit.remove(cf.key);
                                }
                            }
                        }
                        edit.apply();
                        GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                        reloadData();
                        Toast.makeText(activity, (targetState ? "Enabled " : "Disabled ") + totalInCat + " flags in " + cat, Toast.LENGTH_SHORT).show();
                    });
                } else {
                    hHolder.btnToggleAll.setVisibility(View.GONE);
                }

                return convertView;
            }

            FlagViewHolder holder;
            if (convertView == null || !(convertView.getTag() instanceof FlagViewHolder)) {
                holder = new FlagViewHolder();
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int rPad = (int) (14 * density);
                row.setPadding(rPad, (int) (10 * density), rPad, (int) (10 * density));

                LinearLayout textCol = new LinearLayout(activity);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                textCol.setLayoutParams(colLp);

                TextView tvTitle = new TextView(activity);
                tvTitle.setTextSize(14);
                tvTitle.setTypeface(null, Typeface.BOLD);
                textCol.addView(tvTitle);

                TextView tvTriggerBadge = new TextView(activity);
                tvTriggerBadge.setTextSize(10);
                tvTriggerBadge.setTypeface(null, Typeface.BOLD);
                int bPad = (int) (6 * density);
                tvTriggerBadge.setPadding(bPad, (int) (2 * density), bPad, (int) (2 * density));
                LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tbLp.topMargin = (int) (2 * density);
                tbLp.bottomMargin = (int) (2 * density);
                tvTriggerBadge.setLayoutParams(tbLp);
                textCol.addView(tvTriggerBadge);

                TextView tvDesc = new TextView(activity);
                tvDesc.setTextSize(12);
                tvDesc.setPadding(0, (int) (2 * density), 0, (int) (2 * density));
                textCol.addView(tvDesc);

                TextView tvKey = new TextView(activity);
                tvKey.setTextSize(10);
                textCol.addView(tvKey);
                row.addView(textCol);

                Switch sw = new Switch(activity);
                row.addView(sw);

                TextView valChip = new TextView(activity);
                valChip.setTextSize(13);
                valChip.setTypeface(null, Typeface.BOLD);
                int p = (int) (10 * density);
                valChip.setPadding(p, (int) (6 * density), p, (int) (6 * density));
                row.addView(valChip);

                holder.root = row;
                holder.tvTitle = tvTitle;
                holder.tvTriggerBadge = tvTriggerBadge;
                holder.tvDesc = tvDesc;
                holder.tvKey = tvKey;
                holder.swToggle = sw;
                holder.valChip = valChip;

                convertView = row;
                convertView.setTag(holder);
            } else {
                holder = (FlagViewHolder) convertView.getTag();
            }

            // Re-apply styling on recycled views
            holder.tvTitle.setTextColor(theme.textPrimary);
            holder.tvDesc.setTextColor(theme.textSecondary);
            holder.tvKey.setTextColor(theme.textTertiary);
            holder.tvTriggerBadge.setTextColor(theme.primary);
            holder.tvTriggerBadge.setBackground(createRoundedDrawable(theme.isDark ? 0xFF173832 : 0xFFE6F4F1, 6 * density));
            holder.valChip.setTextColor(theme.primary);
            holder.valChip.setBackground(createRoundedDrawable(theme.primaryContainer, 8 * density));
            applySwitchTint(holder.swToggle, theme);

            // Bind Data
            String triggerStr = item.getTrigger();
            if (triggerStr != null && !triggerStr.isEmpty()) {
                holder.tvTriggerBadge.setVisibility(View.VISIBLE);
                holder.tvTriggerBadge.setText("⚡ Triggers: " + triggerStr);
            } else {
                holder.tvTriggerBadge.setVisibility(View.GONE);
            }

            if (item.curatedFlag != null) {
                holder.tvTitle.setText(item.curatedFlag.title);
                holder.tvDesc.setVisibility(View.VISIBLE);
                holder.tvDesc.setText(item.curatedFlag.description);
                holder.tvKey.setText("ID: " + item.curatedFlag.key + " • " + item.curatedFlag.type + ": " + item.value);
            } else {
                holder.tvTitle.setText(item.customKey);
                holder.tvDesc.setVisibility(View.GONE);
                String typeLabel = (item.value instanceof Boolean) ? "Boolean"
                        : (item.value instanceof Float || item.value instanceof Double) ? "Float"
                        : (item.value instanceof Number) ? "Long" : "String";
                holder.tvKey.setText(typeLabel + " • Value: " + item.value);
            }

            if (item.value instanceof Boolean) {
                holder.valChip.setVisibility(View.GONE);
                holder.swToggle.setVisibility(View.VISIBLE);

                boolean isChecked = Boolean.TRUE.equals(item.value);
                holder.root.setBackground(createCardDrawable(isChecked, density, theme));

                holder.swToggle.setOnClickListener(null);
                holder.swToggle.setChecked(isChecked);

                View.OnClickListener toggleAction = v -> {
                    boolean next = !Boolean.TRUE.equals(item.value);
                    item.value = next;
                    holder.swToggle.setChecked(next);
                    holder.root.setBackground(createCardDrawable(next, density, theme));
                    if (next) {
                        prefs.edit().putBoolean(item.getKey(), true).apply();
                        activeFlagsCount++;
                    } else {
                        prefs.edit().remove(item.getKey()).apply();
                        activeFlagsCount = Math.max(0, activeFlagsCount - 1);
                    }
                    if ("45531621".equals(item.getKey()) || "45531625".equals(item.getKey())) {
                        GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                    }
                    updateSubtitleText();
                    notifyDataSetChanged();
                    Toast.makeText(activity, "Updated: " + item.getTitle(), Toast.LENGTH_SHORT).show();
                };

                holder.swToggle.setOnClickListener(toggleAction);
                holder.root.setClickable(true);
                holder.root.setOnClickListener(toggleAction);
            } else {
                holder.swToggle.setVisibility(View.GONE);
                holder.valChip.setVisibility(View.VISIBLE);
                holder.root.setBackground(createCardDrawable(false, density, theme));

                String chipText = ((item.value instanceof Float || item.value instanceof Double) ? "[Float] "
                        : (item.value instanceof Number) ? "[Long] " : "[String] ") + item.value;
                holder.valChip.setText(chipText);

                holder.root.setClickable(true);
                holder.root.setOnClickListener(v -> showEditValueDialog(activity, prefs, item.getKey(), item.getTitle(), item.value, () -> {
                    Object newVal = prefs.getAll().get(item.getKey());
                    if (newVal != null) {
                        item.value = newVal;
                        notifyDataSetChanged();
                    }
                }));
            }

            return convertView;
        }

        private void applySwitchTint(Switch sw, Theme theme) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int[][] states = new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                };
                int[] thumbColors = new int[]{
                        theme.primary,
                        theme.textSecondary
                };
                int[] trackColors = new int[]{
                        theme.primaryContainer,
                        theme.isDark ? 0xFF353A38 : theme.outline
                };
                sw.setThumbTintList(new ColorStateList(states, thumbColors));
                sw.setTrackTintList(new ColorStateList(states, trackColors));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main Flag Manager Dialog (Single Clean View)
    // ─────────────────────────────────────────────────────────────────────────

    public static void showFlagManagerDialog(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        float density = activity.getResources().getDisplayMetrics().density;
        Theme theme = Theme.get(activity);
        SharedPreferences prefs = getPrefs(activity);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createRoundedDrawable(theme.bg, 28 * density));
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Top Bar
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int tbPad = (int) (16 * density);
        topBar.setPadding(tbPad, (int) (8 * density), (int) (10 * density), (int) (8 * density));
        topBar.setBackgroundColor(theme.surface);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleCol.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Photos Flags");
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(theme.textPrimary);
        tvTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText("Loading flags...");
        tvSub.setTextSize(11);
        tvSub.setTextColor(theme.textSecondary);
        titleCol.addView(tvSub);
        topBar.addView(titleCol);

        // Action Icons
        View btnSearch = createHeaderIconButton(activity, "🔍", (int) (44 * density), theme);
        View btnAdd = createHeaderIconButton(activity, "➕", (int) (44 * density), theme);
        View btnMenu = createHeaderIconButton(activity, "⋮", (int) (44 * density), theme);
        View btnClose = createHeaderIconButton(activity, "✕", (int) (44 * density), theme);

        topBar.addView(btnSearch);
        topBar.addView(btnAdd);
        topBar.addView(btnMenu);
        topBar.addView(btnClose);
        root.addView(topBar);

        // 2. Expandable Search Bar
        LinearLayout searchBox = new LinearLayout(activity);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(tbPad, (int) (6 * density), tbPad, (int) (10 * density));
        searchBox.setBackgroundColor(theme.surface);
        searchBox.setVisibility(View.GONE);

        LinearLayout inputWrapper = new LinearLayout(activity);
        inputWrapper.setOrientation(LinearLayout.HORIZONTAL);
        inputWrapper.setGravity(Gravity.CENTER_VERTICAL);
        inputWrapper.setBackground(createRoundedDrawable(theme.searchInputBg, 14 * density));
        LinearLayout.LayoutParams iwLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * density));
        inputWrapper.setLayoutParams(iwLp);

        EditText etSearch = new EditText(activity);
        etSearch.setHint("Search flags, keys or values...");
        etSearch.setTextSize(14);
        etSearch.setTextColor(theme.textPrimary);
        etSearch.setHintTextColor(theme.textSecondary);
        etSearch.setBackground(null);
        etSearch.setSingleLine(true);
        etSearch.setMaxLines(1);
        etSearch.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER);
        etSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        int sPad = (int) (14 * density);
        etSearch.setPadding(sPad, 0, (int) (6 * density), 0);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        etSearch.setLayoutParams(sLp);
        inputWrapper.addView(etSearch);

        TextView btnClearSearch = new TextView(activity);
        btnClearSearch.setText("✕");
        btnClearSearch.setTextSize(14);
        btnClearSearch.setTextColor(theme.textSecondary);
        btnClearSearch.setGravity(Gravity.CENTER);
        int cPad = (int) (12 * density);
        btnClearSearch.setPadding(cPad, 0, cPad, 0);
        btnClearSearch.setVisibility(View.GONE);
        btnClearSearch.setClickable(true);
        btnClearSearch.setFocusable(true);
        inputWrapper.addView(btnClearSearch);

        searchBox.addView(inputWrapper);
        root.addView(searchBox);

        // 3. Virtualized List View with Fast-Scroll & Empty Container
        FrameLayout listFrame = new FrameLayout(activity);
        LinearLayout.LayoutParams listFrameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listFrame.setLayoutParams(listFrameLp);

        ListView listView = new ListView(activity);
        listView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listView.setDivider(null);
        listView.setDividerHeight((int) (6 * density));
        listView.setPadding(tbPad, (int) (8 * density), tbPad, (int) (16 * density));
        listView.setClipToPadding(false);
        listView.setFastScrollEnabled(false);
        listView.setVerticalScrollBarEnabled(true);
        listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        listFrame.addView(listView);

        LinearLayout emptyContainer = new LinearLayout(activity);
        emptyContainer.setOrientation(LinearLayout.VERTICAL);
        emptyContainer.setGravity(Gravity.CENTER);
        emptyContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emptyContainer.setVisibility(View.GONE);
        listFrame.addView(emptyContainer);

        root.addView(listFrame);

        // 4. Bottom Action Dock
        LinearLayout bottomDock = new LinearLayout(activity);
        bottomDock.setOrientation(LinearLayout.VERTICAL);
        bottomDock.setGravity(Gravity.CENTER);
        int dPadH = (int) (16 * density);
        int dPadV = (int) (12 * density);
        bottomDock.setPadding(dPadH, dPadV, dPadH, dPadV);
        bottomDock.setBackgroundColor(theme.surface);
        bottomDock.setElevation(10f);

        Button btnApply = new Button(activity);
        btnApply.setText("⚡ Apply & Restart Photos");
        btnApply.setTextSize(15);
        btnApply.setTypeface(null, Typeface.BOLD);
        btnApply.setTextColor(theme.onPrimary);
        btnApply.setBackground(createRoundedDrawable(theme.primary, 26 * density));
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (52 * density));
        btnApply.setLayoutParams(applyLp);
        btnApply.setOnClickListener(v -> {
            dialog.dismiss();
            GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
            restartApp(activity);
        });
        bottomDock.addView(btnApply);
        root.addView(bottomDock);

        // Instantiate Adapter
        FlagAdapter adapter = new FlagAdapter(activity, prefs, tvSub, emptyContainer);
        listView.setAdapter(adapter);

        btnSearch.setOnClickListener(v -> {
            if (searchBox.getVisibility() == View.VISIBLE) {
                searchBox.setVisibility(View.GONE);
                etSearch.setText("");
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                }
            } else {
                searchBox.setVisibility(View.VISIBLE);
                etSearch.requestFocus();
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            adapter.filter("");
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                }
                etSearch.clearFocus();
                return true;
            }
            return false;
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        Runnable refreshUi = adapter::reloadData;

        btnAdd.setOnClickListener(v -> showAddCustomFlagDialog(activity, prefs, refreshUi));
        btnMenu.setOnClickListener(v -> showProperOptionsMenu(activity, prefs, adapter, refreshUi));

        // Debounced Search TextWatcher
        Runnable[] searchRunnable = new Runnable[1];
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (searchRunnable[0] != null) {
                    MAIN_HANDLER.removeCallbacks(searchRunnable[0]);
                }
                String q = s.toString();
                searchRunnable[0] = () -> adapter.filter(q);
                MAIN_HANDLER.postDelayed(searchRunnable[0], 200);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.setOnDismissListener(d -> {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && activity.getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
            }
        });

        refreshUi.run();
        dialog.setContentView(root);
        dialog.show();

        // Window size - popup-friendly (94% width, 88% height)
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            int dialogWidth = Math.min((int) (screenWidth * 0.94f), (int) (520 * density));
            int dialogHeight = (int) (screenHeight * 0.88f);
            window.setLayout(dialogWidth, dialogHeight);
            window.setGravity(Gravity.CENTER);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setNavigationBarColor(theme.surface);
                window.setStatusBarColor(theme.surface);
            }
        }
    }

    private static void renderEmptySlate(Activity activity, LinearLayout container, float density, Theme theme) {
        container.removeAllViews();
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int p = (int) (40 * density);
        box.setPadding(p, p, p, p);

        TextView icon = new TextView(activity);
        icon.setText("✨");
        icon.setTextSize(36);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);

        TextView title = new TextView(activity);
        title.setText("No Flags Configured");
        title.setTextSize(16);
        title.setTextColor(theme.textPrimary);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, (int) (10 * density), 0, (int) (4 * density));
        box.addView(title);

        TextView desc = new TextView(activity);
        desc.setText("Google Photos is running in stock mode.\nTap ➕ to add a flag, or tap ⋮ to import.");
        desc.setTextSize(13);
        desc.setTextColor(theme.textSecondary);
        desc.setGravity(Gravity.CENTER);
        box.addView(desc);

        container.addView(box);
    }

    private static void renderEmptySlate(Activity activity, LinearLayout container, float density) {
        renderEmptySlate(activity, container, density, Theme.get(activity));
    }

    private static void renderEmptyMessage(Activity activity, LinearLayout container, String msg, float density, Theme theme) {
        container.removeAllViews();
        TextView tv = new TextView(activity);
        tv.setText(msg);
        tv.setTextSize(13);
        tv.setTextColor(theme.textSecondary);
        tv.setGravity(Gravity.CENTER);
        int p = (int) (32 * density);
        tv.setPadding(p, p, p, p);
        container.addView(tv);
    }

    private static void renderEmptyMessage(Activity activity, LinearLayout container, String msg, float density) {
        renderEmptyMessage(activity, container, msg, density, Theme.get(activity));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Options Menu
    // ─────────────────────────────────────────────────────────────────────────

    private static void showProperOptionsMenu(Activity activity, SharedPreferences prefs, FlagAdapter adapter, Runnable onRefresh) {
        float density = activity.getResources().getDisplayMetrics().density;
        Theme theme = Theme.get(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding((int) (16 * density), (int) (4 * density), (int) (16 * density), (int) (16 * density));

        class MenuItem {
            final String icon;
            final String title;
            final String subtitle;
            final Runnable action;
            MenuItem(String icon, String title, String subtitle, Runnable action) {
                this.icon = icon;
                this.title = title;
                this.subtitle = subtitle;
                this.action = action;
            }
        }

        List<MenuItem> items = new ArrayList<>();

        // 1. Expand All Categories
        items.add(new MenuItem("📂", "Expand All Categories", "Expand all flag categories to view all flags", () -> {
            adapter.expandAll();
            Toast.makeText(activity, "Expanded all categories", Toast.LENGTH_SHORT).show();
        }));

        // 2. Collapse All Categories
        items.add(new MenuItem("📁", "Collapse All Categories", "Collapse all categories to headers only", () -> {
            adapter.collapseAll();
            Toast.makeText(activity, "Collapsed all categories", Toast.LENGTH_SHORT).show();
        }));

        // 3. Bulk Import from File (SAF)
        items.add(new MenuItem("📁", "Bulk Import from File (SAF)", "Select a .txt, .json, or .xml file to import flags", () -> {
            launchSafImport(activity, prefs, onRefresh);
        }));

        // 4. Bulk Paste Text
        items.add(new MenuItem("📋", "Bulk Paste Text", "Paste key=value lines, JSON, or XML directly", () -> {
            showBulkPasteDialog(activity, prefs, onRefresh);
        }));

        // 5. Export to File (SAF)
        items.add(new MenuItem("💾", "Export to File (SAF)", "Save all configured flags to a file", () -> {
            launchSafExport(activity, prefs);
        }));

        // 6. Copy All to Clipboard
        items.add(new MenuItem("📤", "Copy All to Clipboard", "Copy all configured flags to clipboard as JSON", () -> {
            copyAllToClipboard(activity, prefs);
        }));

        // 7. Load Recommended Presets
        items.add(new MenuItem("✨", "Load Recommended Presets", "Apply all 362 Morphe feature flags (story colors, AI tools, Create Tab, Navigation)", () -> {
            LinearLayout msgLayout = new LinearLayout(activity);
            msgLayout.setOrientation(LinearLayout.VERTICAL);
            int mPad = (int) (18 * density);
            msgLayout.setPadding(mPad, (int) (4 * density), mPad, (int) (12 * density));

            TextView tvMsg = new TextView(activity);
            tvMsg.setText("This will apply all 362 curated Morphe flags:\n\n• 226 Story colors & collage templates\n• 54 Story player & audio controls\n• 41 3D memories & cutout animations\n• AI video generation & editor tools\n• Create Tab storefront & 8 creative tools\n• Modern navigation & Collections V2\n\nExisting flags will be kept. New flags will be added on top.");
            tvMsg.setTextSize(13);
            tvMsg.setTextColor(theme.textPrimary);
            tvMsg.setLineSpacing(0, 1.25f);
            msgLayout.addView(tvMsg);

            Dialog presetDialog = createM3ActionDialog(activity, "✨ Load Recommended Presets", msgLayout, "Apply All", () -> {
                PhotoFlagsRegistry.applyCuratedDefaults(prefs);
                GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                Toast.makeText(activity, "✓ Loaded 362 Morphe recommended flags", Toast.LENGTH_SHORT).show();
                onRefresh.run();
            });
            presetDialog.show();
        }));

        // 8. Diagnostics & Logs
        items.add(new MenuItem("📊", "Diagnostics & Logs", "View session logs, errors, crashes, and export/share diagnostics", () -> {
            app.morphe.extension.shared.diagnostics.DiagnosticsDialog.show(activity);
        }));

        // 9. Clear All Flags
        items.add(new MenuItem("🗑️", "Clear All Flags", "Wipe all flags and restore stock photos state", () -> {
            prefs.edit().clear().apply();
            GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
            adapter.collapseAll();
            Toast.makeText(activity, "✓ Cleared all flags (stock photos state)", Toast.LENGTH_SHORT).show();
            onRefresh.run();
        }));

        Dialog menuDialog = createM3Dialog(activity, "⚙️ Flag Options", list);

        for (MenuItem item : items) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int p = (int) (14 * density);
            row.setPadding(p, p, p, p);
            row.setBackground(createCardDrawable(false, density, theme));
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.setMargins(0, 0, 0, (int) (8 * density));
            row.setLayoutParams(rLp);

            TextView tvIcon = new TextView(activity);
            tvIcon.setText(item.icon);
            tvIcon.setTextSize(20);
            tvIcon.setPadding(0, 0, (int) (14 * density), 0);
            row.addView(tvIcon);

            LinearLayout col = new LinearLayout(activity);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            col.setLayoutParams(cLp);

            TextView tvT = new TextView(activity);
            tvT.setText(item.title);
            tvT.setTextSize(15);
            tvT.setTextColor(theme.textPrimary);
            tvT.setTypeface(null, Typeface.BOLD);
            col.addView(tvT);

            TextView tvS = new TextView(activity);
            tvS.setText(item.subtitle);
            tvS.setTextSize(12);
            tvS.setTextColor(theme.textSecondary);
            tvS.setPadding(0, (int) (2 * density), 0, 0);
            col.addView(tvS);
            row.addView(col);

            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                menuDialog.dismiss();
                item.action.run();
            });

            list.addView(row);
        }

        menuDialog.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Loading / Progress Dialog
    // ─────────────────────────────────────────────────────────────────────────

    public static Dialog showLoadingDialog(Activity activity, String title, String message) {
        float density = activity.getResources().getDisplayMetrics().density;
        Theme theme = Theme.get(activity);
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(false);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int p = (int) (20 * density);
        root.setPadding(p, p, p, p);
        root.setBackground(createRoundedDrawable(theme.surface, 20 * density));

        ProgressBar pb = new ProgressBar(activity);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams((int) (40 * density), (int) (40 * density));
        pbLp.setMargins(0, 0, (int) (16 * density), 0);
        pb.setLayoutParams(pbLp);
        root.addView(pb);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(cLp);

        TextView tvT = new TextView(activity);
        tvT.setText(title);
        tvT.setTextSize(16);
        tvT.setTextColor(theme.textPrimary);
        tvT.setTypeface(null, Typeface.BOLD);
        textCol.addView(tvT);

        TextView tvM = new TextView(activity);
        tvM.setText(message);
        tvM.setTextSize(12);
        tvM.setTextColor(theme.textSecondary);
        tvM.setPadding(0, (int) (2 * density), 0, 0);
        textCol.addView(tvM);

        root.addView(textCol);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min((int) (screenWidth * 0.88f), (int) (420 * density)), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
        d.show();
        return d;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAF (Storage Access Framework) File Operations
    // ─────────────────────────────────────────────────────────────────────────

    public static class SafHelperFragment extends android.app.Fragment {
        private static final int REQ_OPEN_DOCUMENT = 8011;
        private static final int REQ_CREATE_DOCUMENT = 8012;

        public interface FileCallback {
            void onFileSelected(Uri uri);
        }

        private FileCallback openCallback;
        private FileCallback createCallback;

        public void setOpenCallback(FileCallback cb) { this.openCallback = cb; }
        public void setCreateCallback(FileCallback cb) { this.createCallback = cb; }

        public void openDocument(String[] mimeTypes) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            if (mimeTypes != null && mimeTypes.length > 0) {
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            }
            startActivityForResult(intent, REQ_OPEN_DOCUMENT);
        }

        public void createDocument(String fileName, String mimeType) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            startActivityForResult(intent, REQ_CREATE_DOCUMENT);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                if (requestCode == REQ_OPEN_DOCUMENT && openCallback != null) {
                    openCallback.onFileSelected(uri);
                } else if (requestCode == REQ_CREATE_DOCUMENT && createCallback != null) {
                    createCallback.onFileSelected(uri);
                }
            }
            if (getFragmentManager() != null) {
                getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
            }
        }
    }

    private static void launchSafImport(Activity activity, SharedPreferences prefs, Runnable onRefresh) {
        try {
            SafHelperFragment fragment = new SafHelperFragment();
            fragment.setOpenCallback(uri -> {
                if (uri == null) return;
                Dialog loading = showLoadingDialog(activity, "Importing Flags...", "Reading file and saving overrides...");
                IO_EXECUTOR.execute(() -> {
                    int count = 0;
                    try {
                        InputStream is = activity.getContentResolver().openInputStream(uri);
                        if (is != null) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                            count = importFlagsStream(activity, prefs, reader);
                            reader.close();
                            is.close();
                        }
                    } catch (Throwable t) {
                        Logger.printException(() -> "Error importing file from SAF", t);
                    }
                    final int finalCount = count;
                    activity.runOnUiThread(() -> {
                        loading.dismiss();
                        Toast.makeText(activity, "✓ Imported " + finalCount + " flags from file!", Toast.LENGTH_SHORT).show();
                        if (onRefresh != null) onRefresh.run();
                    });
                });
            });
            activity.getFragmentManager().beginTransaction().add(fragment, "saf_import").commitAllowingStateLoss();
            activity.getFragmentManager().executePendingTransactions();
            fragment.openDocument(new String[]{"text/plain", "application/json", "text/xml", "*/*"});
        } catch (Throwable t) {
            Logger.printException(() -> "Error launching SAF file picker", t);
            Toast.makeText(activity, "Could not open file picker", Toast.LENGTH_SHORT).show();
        }
    }

    private static void launchSafExport(Activity activity, SharedPreferences prefs) {
        try {
            SafHelperFragment fragment = new SafHelperFragment();
            fragment.setCreateCallback(uri -> {
                if (uri == null) return;
                IO_EXECUTOR.execute(() -> {
                    boolean success = false;
                    try {
                        OutputStream os = activity.getContentResolver().openOutputStream(uri);
                        if (os != null) {
                            String jsonStr = generateExportJson(prefs);
                            os.write(jsonStr.getBytes(StandardCharsets.UTF_8));
                            os.flush();
                            os.close();
                            success = true;
                        }
                    } catch (Throwable t) {
                        Logger.printException(() -> "Error exporting file to SAF", t);
                    }
                    final boolean finalSuccess = success;
                    activity.runOnUiThread(() -> {
                        if (finalSuccess) {
                            Toast.makeText(activity, "✓ Exported flags to file!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, "Failed to save file", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            });
            activity.getFragmentManager().beginTransaction().add(fragment, "saf_export").commitAllowingStateLoss();
            activity.getFragmentManager().executePendingTransactions();
            fragment.createDocument("morphe_photos_flags.json", "application/json");
        } catch (Throwable t) {
            Logger.printException(() -> "Error launching SAF file save", t);
            Toast.makeText(activity, "Could not open file saver", Toast.LENGTH_SHORT).show();
        }
    }

    private static void copyAllToClipboard(Activity activity, SharedPreferences prefs) {
        String jsonStr = generateExportJson(prefs);
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Photos Flags", jsonStr));
            Toast.makeText(activity, "✓ Copied flags to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    private static String generateExportJson(SharedPreferences prefs) {
        JSONObject json = new JSONObject();
        try {
            Map<String, ?> all = prefs.getAll();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                String k = entry.getKey();
                if (!k.startsWith("_") && !k.startsWith("__")) {
                    json.put(k, entry.getValue());
                }
            }
        } catch (Exception ignored) {}
        return json.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bulk Paste Dialog
    // ─────────────────────────────────────────────────────────────────────────

    private static void showBulkPasteDialog(Activity activity, SharedPreferences prefs, Runnable onRefresh) {
        float density = activity.getResources().getDisplayMetrics().density;
        Theme theme = Theme.get(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * density);
        layout.setPadding(p, 0, p, (int) (8 * density));

        TextView tvHelp = new TextView(activity);
        tvHelp.setText("Paste Key=Value lines, JSON preset, or Phenotype XML:");
        tvHelp.setTextSize(12);
        tvHelp.setTextColor(theme.textSecondary);
        tvHelp.setPadding(0, 0, 0, (int) (6 * density));
        layout.addView(tvHelp);

        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, 0, 0, (int) (8 * density));

        Button btnPasteClipboard = new Button(activity);
        btnPasteClipboard.setText("📋 Paste from Clipboard");
        btnPasteClipboard.setTextSize(12);
        btnPasteClipboard.setTypeface(null, Typeface.BOLD);
        btnPasteClipboard.setTextColor(theme.isDark ? theme.onPrimaryContainer : theme.primary);
        btnPasteClipboard.setBackground(createRoundedDrawable(theme.primaryContainer, 16 * density));
        btnPasteClipboard.setPadding((int) (12 * density), 0, (int) (12 * density), 0);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (36 * density));
        btnPasteClipboard.setLayoutParams(btnLp);
        actionRow.addView(btnPasteClipboard);

        TextView tvCountPreview = new TextView(activity);
        tvCountPreview.setText("0 flags detected");
        tvCountPreview.setTextSize(12);
        tvCountPreview.setTextColor(theme.textSecondary);
        tvCountPreview.setGravity(Gravity.END);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvCountPreview.setLayoutParams(cLp);
        actionRow.addView(tvCountPreview);
        layout.addView(actionRow);

        EditText etInput = new EditText(activity);
        etInput.setHint("Paste flags here...\ne.g.\n45705305=true\n45762698=2\n45531621=true");
        etInput.setTextSize(13);
        etInput.setTextColor(theme.textPrimary);
        etInput.setHintTextColor(theme.textSecondary);
        etInput.setBackground(createRoundedDrawable(theme.searchInputBg, 12 * density));
        int pad = (int) (12 * density);
        etInput.setPadding(pad, pad, pad, pad);
        etInput.setMinLines(6);
        etInput.setMaxLines(12);
        etInput.setGravity(Gravity.TOP);
        layout.addView(etInput);

        Runnable[] countRunnable = new Runnable[1];
        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (countRunnable[0] != null) {
                    MAIN_HANDLER.removeCallbacks(countRunnable[0]);
                }
                String text = s.toString();
                countRunnable[0] = () -> {
                    IO_EXECUTOR.execute(() -> {
                        int detected = countFlagsInText(text);
                        activity.runOnUiThread(() -> tvCountPreview.setText(detected + " flags detected"));
                    });
                };
                MAIN_HANDLER.postDelayed(countRunnable[0], 250);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnPasteClipboard.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    etInput.setText(text);
                    Toast.makeText(activity, "Pasted from clipboard", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(activity, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        });

        Dialog dialog = createM3ActionDialog(activity, "📋 Bulk Paste Flags", layout, "Import All", () -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                Dialog loading = showLoadingDialog(activity, "Importing Flags...", "Parsing flags and saving overrides...");
                IO_EXECUTOR.execute(() -> {
                    BufferedReader reader = new BufferedReader(new StringReader(text));
                    int count = importFlagsStream(activity, prefs, reader);
                    activity.runOnUiThread(() -> {
                        loading.dismiss();
                        Toast.makeText(activity, "✓ Imported " + count + " flags!", Toast.LENGTH_SHORT).show();
                        if (onRefresh != null) onRefresh.run();
                    });
                });
            }
        });
        dialog.show();
    }

    private static int countFlagsInText(String content) {
        if (content == null || content.isEmpty()) return 0;
        try {
            String trimmed = content.trim();
            if (trimmed.startsWith("{")) {
                JSONObject json = new JSONObject(content);
                int c = 0;
                Iterator<String> it = json.keys();
                while (it.hasNext()) {
                    if (!it.next().startsWith("_")) c++;
                }
                return c;
            }
        } catch (Exception ignored) {}

        int count = 0;
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("//") || l.startsWith(";") || l.startsWith("<!--")) {
                continue;
            }
            int eqIdx = l.indexOf('=');
            if (eqIdx > 0) {
                String k = l.substring(0, eqIdx).trim();
                if (!k.startsWith("#") && !k.startsWith("//") && !k.startsWith(";")) {
                    count++;
                }
            } else if (l.contains("<flag") || l.contains("<boolean") || l.contains("<long") || l.contains("<string")) {
                count++;
            }
        }
        return count;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fast Streaming Import & Parsing Engine
    // ─────────────────────────────────────────────────────────────────────────

    private static int importFlagsStream(Activity activity, SharedPreferences prefs, BufferedReader reader) {
        int count = 0;
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> customKeys = new HashSet<>(prefs.getStringSet(CUSTOM_FLAGS_KEY, Collections.emptySet()));

        try {
            List<String> allLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                allLines.add(line);
            }

            // Find first non-empty, non-comment line to detect format
            String firstContentLine = "";
            for (String l : allLines) {
                String t = l.trim();
                if (!t.isEmpty() && !t.startsWith("#") && !t.startsWith("//") && !t.startsWith(";") && !t.startsWith("<!--")) {
                    firstContentLine = t;
                    break;
                }
            }

            if (firstContentLine.startsWith("{")) {
                StringBuilder sb = new StringBuilder();
                for (String l : allLines) sb.append(l).append('\n');
                JSONObject json = new JSONObject(sb.toString());
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (k.startsWith("_")) continue;
                    Object v = json.get(k);
                    editor.remove(k);
                    applyEntry(editor, k, v);
                    if (!PhotoFlagsRegistry.FLAG_MAP.containsKey(k)) {
                        customKeys.add(k);
                    }
                    count++;
                }
            } else if (firstContentLine.contains("<flag") || firstContentLine.startsWith("<?xml") || firstContentLine.startsWith("<map") || firstContentLine.startsWith("<package")) {
                StringBuilder sb = new StringBuilder();
                for (String l : allLines) sb.append(l).append('\n');
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(new InputSource(new StringReader(sb.toString())));

                NodeList flags = doc.getElementsByTagName("flag");
                for (int i = 0; i < flags.getLength(); i++) {
                    Element el = (Element) flags.item(i);
                    String name = el.getAttribute("name");
                    String type = el.getAttribute("type");
                    String value = el.getAttribute("value");
                    if (name.isEmpty() || name.startsWith("_")) continue;

                    editor.remove(name);
                    if ("boolean".equalsIgnoreCase(type)) {
                        editor.putBoolean(name, Boolean.parseBoolean(value));
                    } else if ("float".equalsIgnoreCase(type) || "double".equalsIgnoreCase(type)) {
                        try { editor.putFloat(name, Float.parseFloat(value)); }
                        catch (Exception ex) { editor.putString(name, value); }
                    } else if ("long".equalsIgnoreCase(type) || "int".equalsIgnoreCase(type) || "integer".equalsIgnoreCase(type)) {
                        try { editor.putLong(name, Long.parseLong(value)); }
                        catch (Exception ex) { editor.putLong(name, 1L); }
                    } else {
                        editor.putString(name, value);
                    }
                    if (!PhotoFlagsRegistry.FLAG_MAP.containsKey(name)) {
                        customKeys.add(name);
                    }
                    count++;
                }

                NodeList booleans = doc.getElementsByTagName("boolean");
                for (int i = 0; i < booleans.getLength(); i++) {
                    Element el = (Element) booleans.item(i);
                    String name = el.getAttribute("name");
                    if (!name.isEmpty() && !name.startsWith("_")) {
                        editor.remove(name);
                        editor.putBoolean(name, Boolean.parseBoolean(el.getAttribute("value")));
                        if (!PhotoFlagsRegistry.FLAG_MAP.containsKey(name)) {
                            customKeys.add(name);
                        }
                        count++;
                    }
                }
                NodeList longs = doc.getElementsByTagName("long");
                for (int i = 0; i < longs.getLength(); i++) {
                    Element el = (Element) longs.item(i);
                    String name = el.getAttribute("name");
                    if (!name.isEmpty() && !name.startsWith("_")) {
                        editor.remove(name);
                        try { editor.putLong(name, Long.parseLong(el.getAttribute("value"))); }
                        catch (Exception ignored) {}
                        if (!PhotoFlagsRegistry.FLAG_MAP.containsKey(name)) {
                            customKeys.add(name);
                        }
                        count++;
                    }
                }
                NodeList strings = doc.getElementsByTagName("string");
                for (int i = 0; i < strings.getLength(); i++) {
                    Element el = (Element) strings.item(i);
                    String name = el.getAttribute("name");
                    if (!name.isEmpty() && !name.startsWith("_")) {
                        editor.remove(name);
                        editor.putString(name, el.getTextContent());
                        if (!PhotoFlagsRegistry.FLAG_MAP.containsKey(name)) {
                            customKeys.add(name);
                        }
                        count++;
                    }
                }
            } else {
                for (String l : allLines) {
                    if (parseAndApplyKeyValueLine(l, editor, customKeys)) {
                        count++;
                    }
                }
            }

            editor.putStringSet(CUSTOM_FLAGS_KEY, customKeys).apply();
            GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
        } catch (Exception e) {
            Logger.printException(() -> "Streaming import failed", e);
        }
        return count;
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2) {
            char f = s.charAt(0);
            char l = s.charAt(s.length() - 1);
            if ((f == '"' && l == '"') || (f == 39 && l == 39)) {
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    private static boolean parseAndApplyKeyValueLine(String line, SharedPreferences.Editor editor, Set<String> customKeys) {
        if (line == null) return false;
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//") || line.startsWith(";") || line.startsWith("<!--")) {
            return false;
        }

        int eqIdx = line.indexOf('=');
        if (eqIdx <= 0) return false;

        String k = line.substring(0, eqIdx).trim();
        if (k.isEmpty() || k.startsWith("#") || k.startsWith("//") || k.startsWith(";")) {
            return false;
        }

        String v = line.substring(eqIdx + 1).trim();

        // Strip inline comments (#, //, ;)
        int commentHash = v.indexOf('#');
        if (commentHash != -1) v = v.substring(0, commentHash).trim();

        int commentSlash = v.indexOf("//");
        if (commentSlash != -1) v = v.substring(0, commentSlash).trim();

        int commentSemi = v.indexOf(';');
        if (commentSemi != -1) v = v.substring(0, commentSemi).trim();

        // Strip surrounding quotes ("value" or 'value')
        v = stripQuotes(v);

        if (v.isEmpty()) return false;

        // Clean out any existing dirty type entry in SharedPreferences before putting correct type
        editor.remove(k);

        if (v.equalsIgnoreCase("true")) {
            editor.putBoolean(k, true);
        } else if (v.equalsIgnoreCase("false")) {
            editor.putBoolean(k, false);
        } else if (v.matches("^-?\\d+$")) {
            try {
                editor.putLong(k, Long.parseLong(v));
            } catch (Exception ex) {
                editor.putString(k, v);
            }
        } else if (v.matches("^-?\\d*\\.\\d+$")) {
            try {
                editor.putFloat(k, Float.parseFloat(v));
            } catch (Exception ex) {
                editor.putString(k, v);
            }
        } else {
            editor.putString(k, v);
        }

        if (!PhotoFlagsRegistry.FLAG_MAP.containsKey(k)) {
            customKeys.add(k);
        }
        return true;
    }

    private static void applyEntry(SharedPreferences.Editor editor, String key, Object val) {
        if (val instanceof Boolean) {
            editor.putBoolean(key, (Boolean) val);
        } else if (val instanceof Float || val instanceof Double) {
            editor.putFloat(key, ((Number) val).floatValue());
        } else if (val instanceof Number) {
            editor.putLong(key, ((Number) val).longValue());
        } else {
            editor.putString(key, String.valueOf(val));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single-Flag Addition & Value Edit Dialogs
    // ─────────────────────────────────────────────────────────────────────────

    private static void showAddCustomFlagDialog(Activity activity, SharedPreferences prefs, Runnable onRefresh) {
        float density = activity.getResources().getDisplayMetrics().density;
        Theme theme = Theme.get(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * density);
        layout.setPadding(p, 0, p, (int) (8 * density));

        TextView tvTypeLabel = new TextView(activity);
        tvTypeLabel.setText("Flag Data Type:");
        tvTypeLabel.setTextSize(12);
        tvTypeLabel.setTextColor(theme.textSecondary);
        tvTypeLabel.setPadding(0, 0, 0, (int) (6 * density));
        layout.addView(tvTypeLabel);

        LinearLayout typeRow = new LinearLayout(activity);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeRow.setGravity(Gravity.CENTER_VERTICAL);
        typeRow.setPadding(0, 0, 0, (int) (10 * density));

        int[] selectedType = new int[]{0}; // 0=Boolean, 1=Long, 2=Float, 3=String
        Button[] typeButtons = new Button[4];
        String[] typeNames = new String[]{"Boolean", "Long", "Float", "String"};

        EditText etKey = new EditText(activity);
        etKey.setHint("Flag ID (e.g. 45705305)");
        etKey.setTextSize(14);
        etKey.setTextColor(theme.textPrimary);
        etKey.setHintTextColor(theme.textSecondary);
        etKey.setBackground(createRoundedDrawable(theme.searchInputBg, 10 * density));
        int pad = (int) (10 * density);
        etKey.setPadding(pad, pad, pad, pad);

        EditText etVal = new EditText(activity);
        etVal.setText("true");
        etVal.setHint("Value (true / false)");
        etVal.setTextSize(14);
        etVal.setTextColor(theme.textPrimary);
        etVal.setHintTextColor(theme.textSecondary);
        etVal.setBackground(createRoundedDrawable(theme.searchInputBg, 10 * density));
        etVal.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vLp.setMargins(0, (int) (8 * density), 0, 0);
        etVal.setLayoutParams(vLp);

        Runnable updateTypeButtons = () -> {
            for (int i = 0; i < 4; i++) {
                boolean isSel = (selectedType[0] == i);
                typeButtons[i].setBackground(createRoundedDrawable(isSel ? theme.primary : theme.searchInputBg, 8 * density));
                typeButtons[i].setTextColor(isSel ? theme.onPrimary : theme.textPrimary);
            }
            if (selectedType[0] == 0) {
                etVal.setHint("true or false");
                if (etVal.getText().toString().isEmpty() || etVal.getText().toString().equals("0")) etVal.setText("true");
            } else if (selectedType[0] == 1) {
                etVal.setHint("Integer value (e.g. 2, 3)");
                if (etVal.getText().toString().equals("true") || etVal.getText().toString().equals("false")) etVal.setText("1");
            } else if (selectedType[0] == 2) {
                etVal.setHint("Decimal value (e.g. 1.5, 2.0)");
                if (etVal.getText().toString().equals("true") || etVal.getText().toString().equals("false")) etVal.setText("1.0");
            } else {
                etVal.setHint("String value");
            }
        };

        for (int i = 0; i < 4; i++) {
            final int tIdx = i;
            Button b = new Button(activity);
            b.setText(typeNames[i]);
            b.setTextSize(11);
            b.setTypeface(null, Typeface.BOLD);
            b.setPadding((int) (8 * density), (int) (4 * density), (int) (8 * density), (int) (4 * density));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, (int) (36 * density), 1f);
            if (i > 0) bLp.setMargins((int) (4 * density), 0, 0, 0);
            b.setLayoutParams(bLp);
            b.setOnClickListener(v -> {
                selectedType[0] = tIdx;
                updateTypeButtons.run();
            });
            typeButtons[i] = b;
            typeRow.addView(b);
        }
        updateTypeButtons.run();

        layout.addView(typeRow);
        layout.addView(etKey);
        layout.addView(etVal);

        Dialog dialog = createM3ActionDialog(activity, "➕ Add Custom Flag", layout, "Save", () -> {
            String k = etKey.getText().toString().trim();
            String v = etVal.getText().toString().trim();
            if (!k.isEmpty() && !v.isEmpty()) {
                SharedPreferences.Editor ed = prefs.edit();
                if (selectedType[0] == 0) {
                    ed.putBoolean(k, Boolean.parseBoolean(v));
                } else if (selectedType[0] == 1) {
                    try {
                        ed.putLong(k, Long.parseLong(v));
                    } catch (Exception ex) {
                        ed.putString(k, v);
                    }
                } else if (selectedType[0] == 2) {
                    try {
                        ed.putFloat(k, Float.parseFloat(v));
                    } catch (Exception ex) {
                        ed.putString(k, v);
                    }
                } else {
                    ed.putString(k, v);
                }
                if (!PhotoFlagsRegistry.FLAG_MAP.containsKey(k)) {
                    Set<String> custom = new HashSet<>(prefs.getStringSet(CUSTOM_FLAGS_KEY, Collections.emptySet()));
                    custom.add(k);
                    ed.putStringSet(CUSTOM_FLAGS_KEY, custom);
                }
                ed.apply();
                if ("45531621".equals(k) || "45531625".equals(k)) {
                    GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                }
                Toast.makeText(activity, "✓ Saved custom flag (" + typeNames[selectedType[0]] + ")", Toast.LENGTH_SHORT).show();
                onRefresh.run();
            }
        });
        dialog.show();
    }

    private static void showEditValueDialog(Activity activity, SharedPreferences prefs,
                                            String key, String title, Object currentVal,
                                            Runnable onRefresh) {
        float density = activity.getResources().getDisplayMetrics().density;
        Theme theme = Theme.get(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * density);
        layout.setPadding(p, 0, p, (int) (8 * density));

        String typeDesc = (currentVal instanceof Boolean) ? "Boolean"
                : (currentVal instanceof Float || currentVal instanceof Double) ? "Float (Decimal)"
                : (currentVal instanceof Number) ? "Long (Integer)" : "String";

        TextView tvDesc = new TextView(activity);
        tvDesc.setText("Editing " + typeDesc + " for:\n" + title);
        tvDesc.setTextSize(13);
        tvDesc.setTextColor(theme.textSecondary);
        tvDesc.setPadding(0, 0, 0, (int) (8 * density));
        layout.addView(tvDesc);

        EditText etVal = new EditText(activity);
        etVal.setText(String.valueOf(currentVal));
        etVal.setTextSize(14);
        etVal.setTextColor(theme.textPrimary);
        etVal.setHintTextColor(theme.textSecondary);
        etVal.setBackground(createRoundedDrawable(theme.searchInputBg, 10 * density));
        int pad = (int) (10 * density);
        etVal.setPadding(pad, pad, pad, pad);
        layout.addView(etVal);

        Dialog dialog = createM3ActionDialog(activity, "✏️ Edit Value", layout, "Save", () -> {
            String v = etVal.getText().toString().trim();
            if (!v.isEmpty()) {
                SharedPreferences.Editor ed = prefs.edit();
                if (currentVal instanceof Boolean) {
                    ed.putBoolean(key, Boolean.parseBoolean(v));
                } else if (currentVal instanceof Float || currentVal instanceof Double) {
                    try {
                        ed.putFloat(key, Float.parseFloat(v));
                    } catch (Exception ex) {
                        ed.putString(key, v);
                    }
                } else if (currentVal instanceof Number) {
                    try {
                        ed.putLong(key, Long.parseLong(v));
                    } catch (Exception ex) {
                        try {
                            ed.putFloat(key, Float.parseFloat(v));
                        } catch (Exception ex2) {
                            ed.putString(key, v);
                        }
                    }
                } else {
                    ed.putString(key, v);
                }
                ed.apply();
                if ("45531621".equals(key) || "45531625".equals(key)) {
                    GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                }
                Toast.makeText(activity, "Updated " + title, Toast.LENGTH_SHORT).show();
                onRefresh.run();
            }
        });
        dialog.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers & UI Components
    // ─────────────────────────────────────────────────────────────────────────

    private static View createHeaderIconButton(Activity activity, String icon, int sizePx, Theme theme) {
        TextView tv = new TextView(activity);
        tv.setText(icon);
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(theme.textPrimary);
        tv.setClickable(true);
        tv.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static View createHeaderIconButton(Activity activity, String icon, int sizePx) {
        return createHeaderIconButton(activity, icon, sizePx, Theme.get(activity));
    }

    private static GradientDrawable createCardDrawable(boolean active, float density, Theme theme) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(16 * density);
        gd.setColor(active ? theme.cardActive : theme.card);
        gd.setStroke(active ? (int) (1.5f * density) : (int) (1 * density),
                active ? theme.cardBorderActive : theme.cardBorder);
        return gd;
    }

    private static GradientDrawable createCardDrawable(boolean active, float density) {
        return createCardDrawable(active, density, Theme.get(null));
    }

    private static GradientDrawable createRoundedDrawable(int color, float radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(radiusPx);
        gd.setColor(color);
        return gd;
    }

    private static Dialog createM3Dialog(Activity activity, String title, View customView) {
        float density = activity.getResources().getDisplayMetrics().density;
        Theme theme = Theme.get(activity);
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createRoundedDrawable(theme.surface, 24 * density));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (18 * density);
        header.setPadding(pad, pad, pad, (int) (8 * density));

        TextView tvT = new TextView(activity);
        tvT.setText(title);
        tvT.setTextSize(17);
        tvT.setTextColor(theme.textPrimary);
        tvT.setTypeface(null, Typeface.BOLD);
        header.addView(tvT, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnClose = new TextView(activity);
        btnClose.setText("✕");
        btnClose.setTextSize(16);
        btnClose.setTextColor(theme.textSecondary);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setPadding((int) (8 * density), 0, 0, 0);
        btnClose.setOnClickListener(v -> d.dismiss());
        header.addView(btnClose);
        root.addView(header);

        if (customView != null) {
            root.addView(customView);
        }

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min((int) (screenWidth * 0.90f), (int) (460 * density)), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
        return d;
    }

    private static Dialog createM3ActionDialog(Activity activity, String title, View customView,
                                               String actionText, Runnable onAction) {
        float density = activity.getResources().getDisplayMetrics().density;
        Theme theme = Theme.get(activity);
        Dialog d = createM3Dialog(activity, title, customView);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        int p = (int) (16 * density);
        actions.setPadding(p, (int) (6 * density), p, p);

        Button btnCancel = new Button(activity);
        btnCancel.setText("Cancel");
        btnCancel.setTextSize(13);
        btnCancel.setTextColor(theme.textSecondary);
        btnCancel.setBackground(createRoundedDrawable(theme.surfaceContainer, 18 * density));
        btnCancel.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * density));
        cLp.setMargins(0, 0, (int) (8 * density), 0);
        actions.addView(btnCancel, cLp);

        Button btnOk = new Button(activity);
        btnOk.setText(actionText);
        btnOk.setTextSize(13);
        btnOk.setTypeface(null, Typeface.BOLD);
        btnOk.setTextColor(theme.onPrimary);
        btnOk.setBackground(createRoundedDrawable(theme.primary, 18 * density));
        btnOk.setOnClickListener(v -> {
            d.dismiss();
            if (onAction != null) onAction.run();
        });
        actions.addView(btnOk, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * density)));

        ((ViewGroup) d.findViewById(android.R.id.content)).getChildAt(0);
        ((LinearLayout) ((ViewGroup) d.findViewById(android.R.id.content)).getChildAt(0)).addView(actions);

        return d;
    }

    private static void restartApp(Activity activity) {
        try {
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(intent);
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } catch (Throwable t) {
            Logger.printException(() -> "Error restarting Photos app", t);
        }
    }
}
