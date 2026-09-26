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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.patches.flags.PhotoFlagsRegistry;
import app.morphe.extension.shared.patches.flags.PhotoFlagsRegistry.CuratedFlag;

/**
 * Clean, single-view Material 3 Phenotype Flag Manager for Morphe Google Photos.
 * Supports bulk file import/export via SAF, bulk clipboard paste, and universal flag data types.
 */
public final class PhenotypeFlagManager {

    private static final String PREF_NAME = "com.google.android.apps.photos.phenotype";
    private static final String SETTINGS_PILL_TAG = "morphe_photos_flags_pill";
    public static final String CUSTOM_FLAGS_KEY = "_morphe_custom_flag_ids";
    public static final String SEEDED_MARKER = "_morphe_flags_seeded";

    // Material 3 Palette
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
    private static final int M3_WARN_BG = 0xFFFFF0D4;
    private static final int M3_WARN_TEXT = 0xFF8A5100;
    private static final int M3_DANGER_BG = 0xFFFFDAD6;
    private static final int M3_DANGER_TEXT = 0xFFBA1A1A;

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
        LinearLayout pill = new LinearLayout(activity);
        pill.setTag(SETTINGS_PILL_TAG);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setClickable(true);
        pill.setFocusable(true);
        pill.setElevation(14f);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (int) (50 * density)
        );
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, 0, 0, (int) (24 * density));
        pill.setLayoutParams(lp);

        int padH = (int) (22 * density);
        pill.setPadding(padH, 0, padH, 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(25 * density);
        bg.setColor(M3_PRIMARY);
        pill.setBackground(bg);

        TextView icon = new TextView(activity);
        icon.setText("✨");
        icon.setTextSize(17);
        icon.setPadding(0, 0, (int) (10 * density), 0);
        pill.addView(icon);

        TextView label = new TextView(activity);
        label.setText("Flag Manager");
        label.setTextSize(14);
        label.setTextColor(M3_ON_PRIMARY);
        label.setTypeface(null, Typeface.BOLD);
        pill.addView(label);

        pill.setOnClickListener(v -> showFlagManagerDialog(activity));
        return pill;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main Flag Manager Dialog (Single Clean View)
    // ─────────────────────────────────────────────────────────────────────────

    public static void showFlagManagerDialog(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        float density = activity.getResources().getDisplayMetrics().density;
        SharedPreferences prefs = getPrefs(activity);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createRoundedDrawable(M3_BG, 28 * density));
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Top Bar
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int tbPad = (int) (16 * density);
        topBar.setPadding(tbPad, (int) (8 * density), (int) (10 * density), (int) (8 * density));
        topBar.setBackgroundColor(M3_SURFACE);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleCol.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Photos Flags");
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(M3_TEXT_PRIMARY);
        tvTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText("Loading flags...");
        tvSub.setTextSize(11);
        tvSub.setTextColor(M3_TEXT_SECONDARY);
        titleCol.addView(tvSub);
        topBar.addView(titleCol);

        // Action Icons
        View btnSearch = createHeaderIconButton(activity, "🔍", (int) (48 * density));
        View btnAdd = createHeaderIconButton(activity, "➕", (int) (48 * density));
        View btnMenu = createHeaderIconButton(activity, "⋮", (int) (48 * density));
        View btnClose = createHeaderIconButton(activity, "✕", (int) (48 * density));

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
        searchBox.setBackgroundColor(M3_SURFACE);
        searchBox.setVisibility(View.GONE);

        EditText etSearch = new EditText(activity);
        etSearch.setHint("Search flags, keys or values...");
        etSearch.setTextSize(14);
        etSearch.setTextColor(M3_TEXT_PRIMARY);
        etSearch.setHintTextColor(M3_TEXT_SECONDARY);
        etSearch.setBackground(createRoundedDrawable(0xFFEAEFEB, 14 * density));
        int sPad = (int) (12 * density);
        etSearch.setPadding(sPad, (int) (10 * density), sPad, (int) (10 * density));
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * density));
        etSearch.setLayoutParams(sLp);
        searchBox.addView(etSearch);
        root.addView(searchBox);

        btnSearch.setOnClickListener(v -> {
            if (searchBox.getVisibility() == View.VISIBLE) {
                searchBox.setVisibility(View.GONE);
                etSearch.setText("");
            } else {
                searchBox.setVisibility(View.VISIBLE);
                etSearch.requestFocus();
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // 3. Scrollable List Content Area
        ScrollView scrollView = new ScrollView(activity);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollView.setLayoutParams(scrollLp);

        LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(tbPad, (int) (8 * density), tbPad, (int) (16 * density));
        scrollView.addView(listContainer);
        root.addView(scrollView);

        // 4. Bottom Action Dock
        LinearLayout bottomDock = new LinearLayout(activity);
        bottomDock.setOrientation(LinearLayout.VERTICAL);
        bottomDock.setGravity(Gravity.CENTER);
        int dPadH = (int) (16 * density);
        int dPadV = (int) (12 * density);
        bottomDock.setPadding(dPadH, dPadV, dPadH, dPadV);
        bottomDock.setBackgroundColor(M3_SURFACE);
        bottomDock.setElevation(10f);

        Button btnApply = new Button(activity);
        btnApply.setText("⚡ Apply & Restart Photos");
        btnApply.setTextSize(15);
        btnApply.setTypeface(null, Typeface.BOLD);
        btnApply.setTextColor(M3_ON_PRIMARY);
        btnApply.setBackground(createRoundedDrawable(M3_PRIMARY, 26 * density));
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

        // Refresh UI Runnable
        Runnable[] refreshHolder = new Runnable[1];
        Runnable refreshUi = () -> {
            listContainer.removeAllViews();
            String query = etSearch.getText().toString().toLowerCase().trim();
            Map<String, ?> all = prefs.getAll();

            int totalConfigured = 0;
            int matchedCount = 0;

            // 1. Curated Flags from Registry (if any)
            List<String> categories = PhotoFlagsRegistry.getCategories();
            for (String cat : categories) {
                List<CuratedFlag> flagsInCat = PhotoFlagsRegistry.getFlagsForCategory(cat);
                List<CuratedFlag> matchingFlags = new ArrayList<>();

                for (CuratedFlag flag : flagsInCat) {
                    totalConfigured++;
                    Object val = all.get(flag.key);
                    String searchTarget = flag.key + " " + flag.title + " " + flag.description + " " + val;
                    if (query.isEmpty() || searchTarget.toLowerCase().contains(query)) {
                        matchingFlags.add(flag);
                    }
                }

                if (matchingFlags.isEmpty()) continue;
                matchedCount += matchingFlags.size();

                TextView tvCatHeader = new TextView(activity);
                tvCatHeader.setText(cat);
                tvCatHeader.setTextSize(13);
                tvCatHeader.setTextColor(M3_PRIMARY);
                tvCatHeader.setTypeface(null, Typeface.BOLD);
                tvCatHeader.setPadding(0, (int) (12 * density), 0, (int) (6 * density));
                listContainer.addView(tvCatHeader);

                for (CuratedFlag flag : matchingFlags) {
                    View flagRow = createCuratedFlagRow(activity, prefs, flag, all, refreshHolder[0]);
                    listContainer.addView(flagRow);
                }
            }

            // 2. Custom / Imported Flags
            Set<String> customKeys = prefs.getStringSet(CUSTOM_FLAGS_KEY, Collections.emptySet());
            Set<String> allKeysToDisplay = new HashSet<>(customKeys);

            // Also include any flag directly present in SharedPreferences that is not internal
            for (String k : all.keySet()) {
                if (!k.startsWith("_") && !k.startsWith("__") && !PhotoFlagsRegistry.FLAG_MAP.containsKey(k)) {
                    allKeysToDisplay.add(k);
                }
            }

            List<String> matchingCustom = new ArrayList<>();
            for (String ck : allKeysToDisplay) {
                if (!PhotoFlagsRegistry.FLAG_MAP.containsKey(ck)) {
                    totalConfigured++;
                    Object val = all.get(ck);
                    String st = ck + " " + val;
                    if (query.isEmpty() || st.toLowerCase().contains(query)) {
                        matchingCustom.add(ck);
                    }
                }
            }

            if (!matchingCustom.isEmpty()) {
                // Sort keys alphabetically/numerically
                Collections.sort(matchingCustom);
                matchedCount += matchingCustom.size();

                TextView tvCustomHeader = new TextView(activity);
                tvCustomHeader.setText(categories.isEmpty() ? "Active Flags (" + matchingCustom.size() + ")" : "Custom Overrides (" + matchingCustom.size() + ")");
                tvCustomHeader.setTextSize(13);
                tvCustomHeader.setTextColor(M3_PRIMARY);
                tvCustomHeader.setTypeface(null, Typeface.BOLD);
                tvCustomHeader.setPadding(0, (int) (12 * density), 0, (int) (6 * density));
                listContainer.addView(tvCustomHeader);

                for (String ck : matchingCustom) {
                    View customRow = createCustomFlagRow(activity, prefs, ck, all.get(ck), refreshHolder[0]);
                    listContainer.addView(customRow);
                }
            }

            tvSub.setText(totalConfigured + " Flags Configured");

            if (totalConfigured == 0) {
                renderEmptySlate(activity, listContainer, density);
            } else if (matchedCount == 0) {
                renderEmptyMessage(activity, listContainer, "No flags matched \"" + query + "\"", density);
            }
        };

        refreshHolder[0] = refreshUi;

        btnAdd.setOnClickListener(v -> showAddCustomFlagDialog(activity, prefs, refreshUi));
        btnMenu.setOnClickListener(v -> showProperOptionsMenu(activity, prefs, refreshUi));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshUi.run();
            }
            @Override public void afterTextChanged(Editable s) {}
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
        }
    }

    private static void renderEmptySlate(Activity activity, LinearLayout container, float density) {
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
        title.setTextColor(M3_TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, (int) (10 * density), 0, (int) (4 * density));
        box.addView(title);

        TextView desc = new TextView(activity);
        desc.setText("Tap ➕ above to add a flag, or tap ⋮ to bulk import or paste flags.");
        desc.setTextSize(13);
        desc.setTextColor(M3_TEXT_SECONDARY);
        desc.setGravity(Gravity.CENTER);
        box.addView(desc);

        container.addView(box);
    }

    private static void renderEmptyMessage(Activity activity, LinearLayout container, String msg, float density) {
        TextView tv = new TextView(activity);
        tv.setText(msg);
        tv.setTextSize(13);
        tv.setTextColor(M3_TEXT_SECONDARY);
        tv.setGravity(Gravity.CENTER);
        int p = (int) (32 * density);
        tv.setPadding(p, p, p, p);
        container.addView(tv);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row Builders
    // ─────────────────────────────────────────────────────────────────────────

    private static View createCuratedFlagRow(Activity activity, SharedPreferences prefs,
                                             CuratedFlag flag, Map<String, ?> all,
                                             Runnable onRefresh) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int rPad = (int) (14 * density);
        row.setPadding(rPad, (int) (12 * density), rPad, (int) (12 * density));

        Object val = all.containsKey(flag.key) ? all.get(flag.key) : flag.defaultValue;
        boolean isBool = flag.isBoolean();
        boolean isActive = isBool && Boolean.TRUE.equals(val);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, (int) (8 * density));
        row.setLayoutParams(rowLp);
        row.setBackground(createCardDrawable(isActive, density));

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(colLp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(flag.title);
        tvTitle.setTextSize(14);
        tvTitle.setTextColor(M3_TEXT_PRIMARY);
        tvTitle.setTypeface(null, Typeface.BOLD);
        textCol.addView(tvTitle);

        TextView tvDesc = new TextView(activity);
        tvDesc.setText(flag.description);
        tvDesc.setTextSize(12);
        tvDesc.setTextColor(M3_TEXT_SECONDARY);
        tvDesc.setPadding(0, (int) (2 * density), 0, (int) (2 * density));
        textCol.addView(tvDesc);

        TextView tvKey = new TextView(activity);
        tvKey.setText("ID: " + flag.key + " • " + flag.type + ": " + val);
        tvKey.setTextSize(10);
        tvKey.setTextColor(0xFF8B9B97);
        textCol.addView(tvKey);
        row.addView(textCol);

        if (isBool) {
            Switch sw = new Switch(activity);
            sw.setChecked(isActive);
            row.addView(sw);

            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                boolean next = !sw.isChecked();
                sw.setChecked(next);
                prefs.edit().putBoolean(flag.key, next).commit();
                if (flag.key.equals("45531621") || flag.key.equals("45531625")) {
                    GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                }
                Toast.makeText(activity, "Updated: " + flag.title, Toast.LENGTH_SHORT).show();
                onRefresh.run();
            });

            sw.setOnClickListener(v -> {
                prefs.edit().putBoolean(flag.key, sw.isChecked()).commit();
                if (flag.key.equals("45531621") || flag.key.equals("45531625")) {
                    GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                }
                Toast.makeText(activity, "Updated: " + flag.title, Toast.LENGTH_SHORT).show();
                onRefresh.run();
            });

        } else {
            TextView valChip = new TextView(activity);
            valChip.setText(String.valueOf(val));
            valChip.setTextSize(13);
            valChip.setTypeface(null, Typeface.BOLD);
            valChip.setTextColor(M3_PRIMARY);
            valChip.setBackground(createRoundedDrawable(M3_PRIMARY_CONTAINER, 8 * density));
            int p = (int) (10 * density);
            valChip.setPadding(p, (int) (6 * density), p, (int) (6 * density));
            row.addView(valChip);

            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> showEditValueDialog(activity, prefs, flag.key, flag.title, val, onRefresh));
        }

        return row;
    }

    private static View createCustomFlagRow(Activity activity, SharedPreferences prefs,
                                            String key, Object val, Runnable onRefresh) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int rPad = (int) (14 * density);
        row.setPadding(rPad, (int) (12 * density), rPad, (int) (12 * density));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, (int) (8 * density));
        row.setLayoutParams(rowLp);
        row.setBackground(createCardDrawable(false, density));

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(colLp);

        TextView tvKey = new TextView(activity);
        tvKey.setText(key);
        tvKey.setTextSize(14);
        tvKey.setTextColor(M3_TEXT_PRIMARY);
        tvKey.setTypeface(null, Typeface.BOLD);
        textCol.addView(tvKey);

        TextView tvVal = new TextView(activity);
        String typeLabel = (val instanceof Boolean) ? "Boolean"
                : (val instanceof Float || val instanceof Double) ? "Float"
                : (val instanceof Number) ? "Long" : "String";
        tvVal.setText(typeLabel + " • Value: " + val);
        tvVal.setTextSize(12);
        tvVal.setTextColor(M3_TEXT_SECONDARY);
        textCol.addView(tvVal);
        row.addView(textCol);

        if (val instanceof Boolean) {
            Switch sw = new Switch(activity);
            sw.setChecked((Boolean) val);
            row.addView(sw);
            row.setClickable(true);
            row.setOnClickListener(v -> {
                boolean next = !sw.isChecked();
                sw.setChecked(next);
                prefs.edit().putBoolean(key, next).commit();
                if (key.equals("45531621") || key.equals("45531625")) {
                    GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                }
                Toast.makeText(activity, "Custom flag updated.", Toast.LENGTH_SHORT).show();
                onRefresh.run();
            });
            sw.setOnClickListener(v -> {
                prefs.edit().putBoolean(key, sw.isChecked()).commit();
                if (key.equals("45531621") || key.equals("45531625")) {
                    GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                }
                Toast.makeText(activity, "Custom flag updated.", Toast.LENGTH_SHORT).show();
                onRefresh.run();
            });
        } else {
            TextView valChip = new TextView(activity);
            String chipText = ((val instanceof Float || val instanceof Double) ? "[Float] " : (val instanceof Number) ? "[Long] " : "[String] ") + val;
            valChip.setText(chipText);
            valChip.setTextSize(13);
            valChip.setTypeface(null, Typeface.BOLD);
            valChip.setTextColor(M3_PRIMARY);
            valChip.setBackground(createRoundedDrawable(M3_PRIMARY_CONTAINER, 8 * density));
            int p = (int) (10 * density);
            valChip.setPadding(p, (int) (6 * density), p, (int) (6 * density));
            row.addView(valChip);
            row.setClickable(true);
            row.setOnClickListener(v -> showEditValueDialog(activity, prefs, key, key, val, onRefresh));
        }

        return row;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Options Menu
    // ─────────────────────────────────────────────────────────────────────────

    private static void showProperOptionsMenu(Activity activity, SharedPreferences prefs, Runnable onRefresh) {
        float density = activity.getResources().getDisplayMetrics().density;
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

        // 1. Bulk Import from File (SAF)
        items.add(new MenuItem("📁", "Bulk Import from File (SAF)", "Select a .txt, .json, or .xml file to import flags", () -> {
            launchSafImport(activity, prefs, onRefresh);
        }));

        // 2. Bulk Paste Text
        items.add(new MenuItem("📋", "Bulk Paste Text", "Paste key=value lines or JSON directly", () -> {
            showBulkPasteDialog(activity, prefs, onRefresh);
        }));

        // 3. Export to File (SAF)
        items.add(new MenuItem("💾", "Export to File (SAF)", "Save all configured flags to a file", () -> {
            launchSafExport(activity, prefs);
        }));

        // 4. Copy All to Clipboard
        items.add(new MenuItem("📤", "Copy All to Clipboard", "Copy all configured flags to clipboard as JSON", () -> {
            copyAllToClipboard(activity, prefs);
        }));

        // 5. Clear All Flags
        items.add(new MenuItem("🗑️", "Clear All Flags", "Wipe all flags and restore stock photos state", () -> {
            prefs.edit().clear().apply();
            GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
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
            row.setBackground(createCardDrawable(false, density));
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
            tvT.setTextColor(M3_TEXT_PRIMARY);
            tvT.setTypeface(null, Typeface.BOLD);
            col.addView(tvT);

            TextView tvS = new TextView(activity);
            tvS.setText(item.subtitle);
            tvS.setTextSize(12);
            tvS.setTextColor(M3_TEXT_SECONDARY);
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
                try {
                    InputStream is = activity.getContentResolver().openInputStream(uri);
                    if (is != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        reader.close();
                        is.close();
                        int count = importFlagsUniversal(activity, prefs, sb.toString());
                        Toast.makeText(activity, "✓ Imported " + count + " flags from file!", Toast.LENGTH_SHORT).show();
                        onRefresh.run();
                    }
                } catch (Throwable t) {
                    Logger.printException(() -> "Error importing file from SAF", t);
                    Toast.makeText(activity, "Failed to read file: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
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
                try {
                    OutputStream os = activity.getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        String jsonStr = generateExportJson(prefs);
                        os.write(jsonStr.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        os.close();
                        Toast.makeText(activity, "✓ Exported flags to file!", Toast.LENGTH_SHORT).show();
                    }
                } catch (Throwable t) {
                    Logger.printException(() -> "Error exporting file to SAF", t);
                    Toast.makeText(activity, "Failed to save file: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
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
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * density);
        layout.setPadding(p, 0, p, (int) (8 * density));

        TextView tvHelp = new TextView(activity);
        tvHelp.setText("Paste Key=Value lines, JSON preset, or Phenotype XML:");
        tvHelp.setTextSize(12);
        tvHelp.setTextColor(M3_TEXT_SECONDARY);
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
        btnPasteClipboard.setTextColor(M3_PRIMARY);
        btnPasteClipboard.setBackground(createRoundedDrawable(M3_PRIMARY_CONTAINER, 16 * density));
        btnPasteClipboard.setPadding((int) (12 * density), 0, (int) (12 * density), 0);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (36 * density));
        btnPasteClipboard.setLayoutParams(btnLp);
        actionRow.addView(btnPasteClipboard);

        TextView tvCountPreview = new TextView(activity);
        tvCountPreview.setText("0 flags detected");
        tvCountPreview.setTextSize(12);
        tvCountPreview.setTextColor(M3_TEXT_SECONDARY);
        tvCountPreview.setGravity(Gravity.END);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvCountPreview.setLayoutParams(cLp);
        actionRow.addView(tvCountPreview);
        layout.addView(actionRow);

        EditText etInput = new EditText(activity);
        etInput.setHint("Paste flags here...\ne.g.\n45705305=true\n45762698=2\n45531621=true");
        etInput.setTextSize(13);
        etInput.setTextColor(M3_TEXT_PRIMARY);
        etInput.setHintTextColor(M3_TEXT_SECONDARY);
        etInput.setBackground(createRoundedDrawable(0xFFEAEFEB, 12 * density));
        int pad = (int) (12 * density);
        etInput.setPadding(pad, pad, pad, pad);
        etInput.setMinLines(6);
        etInput.setMaxLines(12);
        etInput.setGravity(Gravity.TOP);
        layout.addView(etInput);

        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int detected = countFlagsInText(s.toString());
                tvCountPreview.setText(detected + " flags detected");
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
                int count = importFlagsUniversal(activity, prefs, text);
                Toast.makeText(activity, "✓ Imported " + count + " flags!", Toast.LENGTH_SHORT).show();
                onRefresh.run();
            }
        });
        dialog.show();
    }

    private static int countFlagsInText(String content) {
        if (content == null || content.isEmpty()) return 0;
        try {
            if (content.startsWith("{")) {
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
            if (l.contains("=") && !l.startsWith("#")) {
                count++;
            } else if (l.contains("<flag")) {
                count++;
            }
        }
        return count;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Import & Parsing Logic
    // ─────────────────────────────────────────────────────────────────────────

    private static int importFlagsUniversal(Activity activity, SharedPreferences prefs, String content) {
        int count = 0;
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> customKeys = new HashSet<>(prefs.getStringSet(CUSTOM_FLAGS_KEY, Collections.emptySet()));

        try {
            // 1. Try JSON
            if (content.startsWith("{")) {
                JSONObject json = new JSONObject(content);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (k.startsWith("_")) continue;
                    Object v = json.get(k);
                    applyEntry(editor, k, v);
                    customKeys.add(k);
                    count++;
                }
                editor.putStringSet(CUSTOM_FLAGS_KEY, customKeys).apply();
                GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                return count;
            }

            // 2. Try XML
            if (content.contains("<flag")) {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(new InputSource(new StringReader(content)));
                NodeList flags = doc.getElementsByTagName("flag");
                for (int i = 0; i < flags.getLength(); i++) {
                    Element el = (Element) flags.item(i);
                    String name = el.getAttribute("name");
                    String type = el.getAttribute("type");
                    String value = el.getAttribute("value");
                    if (name.isEmpty()) continue;

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
                    customKeys.add(name);
                    count++;
                }
                editor.putStringSet(CUSTOM_FLAGS_KEY, customKeys).apply();
                GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
                return count;
            }

            // 3. Try Key=Value lines
            String[] lines = content.split("\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String k = parts[0].trim();
                    String v = parts[1].trim();
                    if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
                        editor.putBoolean(k, Boolean.parseBoolean(v));
                    } else if (v.contains(".")) {
                        try { editor.putFloat(k, Float.parseFloat(v)); }
                        catch (Exception ex) {
                            try { editor.putLong(k, Long.parseLong(v)); }
                            catch (Exception ex2) { editor.putString(k, v); }
                        }
                    } else {
                        try { editor.putLong(k, Long.parseLong(v)); }
                        catch (Exception ex) { editor.putString(k, v); }
                    }
                    customKeys.add(k);
                    count++;
                }
            }
            editor.putStringSet(CUSTOM_FLAGS_KEY, customKeys).apply();
            GooglePhotosAccountAvatar.syncOneGoogleFlags(activity);
        } catch (Exception e) {
            Logger.printException(() -> "Import failed", e);
        }
        return count;
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
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * density);
        layout.setPadding(p, 0, p, (int) (8 * density));

        TextView tvTypeLabel = new TextView(activity);
        tvTypeLabel.setText("Flag Data Type:");
        tvTypeLabel.setTextSize(12);
        tvTypeLabel.setTextColor(M3_TEXT_SECONDARY);
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
        etKey.setTextColor(M3_TEXT_PRIMARY);
        etKey.setBackground(createRoundedDrawable(0xFFEAEFEB, 10 * density));
        int pad = (int) (10 * density);
        etKey.setPadding(pad, pad, pad, pad);

        EditText etVal = new EditText(activity);
        etVal.setText("true");
        etVal.setHint("Value (true / false)");
        etVal.setTextSize(14);
        etVal.setTextColor(M3_TEXT_PRIMARY);
        etVal.setBackground(createRoundedDrawable(0xFFEAEFEB, 10 * density));
        etVal.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vLp.setMargins(0, (int) (8 * density), 0, 0);
        etVal.setLayoutParams(vLp);

        Runnable updateTypeButtons = () -> {
            for (int i = 0; i < 4; i++) {
                boolean isSel = (selectedType[0] == i);
                typeButtons[i].setBackground(createRoundedDrawable(isSel ? M3_PRIMARY : 0xFFEAEFEB, 8 * density));
                typeButtons[i].setTextColor(isSel ? M3_ON_PRIMARY : M3_TEXT_PRIMARY);
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
                Set<String> custom = new HashSet<>(prefs.getStringSet(CUSTOM_FLAGS_KEY, Collections.emptySet()));
                custom.add(k);
                ed.putStringSet(CUSTOM_FLAGS_KEY, custom).apply();
                if (k.equals("45531621") || k.equals("45531625")) {
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
        tvDesc.setTextColor(M3_TEXT_SECONDARY);
        tvDesc.setPadding(0, 0, 0, (int) (8 * density));
        layout.addView(tvDesc);

        EditText etVal = new EditText(activity);
        etVal.setText(String.valueOf(currentVal));
        etVal.setTextSize(14);
        etVal.setTextColor(M3_TEXT_PRIMARY);
        etVal.setBackground(createRoundedDrawable(0xFFEAEFEB, 10 * density));
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
                ed.commit();
                if (key.equals("45531621") || key.equals("45531625")) {
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

    private static View createHeaderIconButton(Activity activity, String icon, int sizePx) {
        TextView tv = new TextView(activity);
        tv.setText(icon);
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(M3_TEXT_PRIMARY);
        tv.setClickable(true);
        tv.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static GradientDrawable createCardDrawable(boolean active, float density) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(16 * density);
        gd.setColor(active ? M3_CARD_ACTIVE : M3_CARD);
        gd.setStroke(active ? (int) (1.5f * density) : (int) (1 * density),
                active ? M3_PRIMARY : M3_OUTLINE);
        return gd;
    }

    private static GradientDrawable createRoundedDrawable(int color, float radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(radiusPx);
        gd.setColor(color);
        return gd;
    }

    private static Dialog createM3Dialog(Activity activity, String title, View customView) {
        float density = activity.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createRoundedDrawable(M3_SURFACE, 24 * density));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (18 * density);
        header.setPadding(pad, pad, pad, (int) (8 * density));

        TextView tvT = new TextView(activity);
        tvT.setText(title);
        tvT.setTextSize(17);
        tvT.setTextColor(M3_TEXT_PRIMARY);
        tvT.setTypeface(null, Typeface.BOLD);
        header.addView(tvT, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnClose = new TextView(activity);
        btnClose.setText("✕");
        btnClose.setTextSize(16);
        btnClose.setTextColor(M3_TEXT_SECONDARY);
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
        Dialog d = createM3Dialog(activity, title, customView);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        int p = (int) (16 * density);
        actions.setPadding(p, (int) (6 * density), p, p);

        Button btnCancel = new Button(activity);
        btnCancel.setText("Cancel");
        btnCancel.setTextSize(13);
        btnCancel.setTextColor(M3_TEXT_SECONDARY);
        btnCancel.setBackground(createRoundedDrawable(0xFFEAEFEB, 18 * density));
        btnCancel.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * density));
        cLp.setMargins(0, 0, (int) (8 * density), 0);
        actions.addView(btnCancel, cLp);

        Button btnOk = new Button(activity);
        btnOk.setText(actionText);
        btnOk.setTextSize(13);
        btnOk.setTypeface(null, Typeface.BOLD);
        btnOk.setTextColor(M3_ON_PRIMARY);
        btnOk.setBackground(createRoundedDrawable(M3_PRIMARY, 18 * density));
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
