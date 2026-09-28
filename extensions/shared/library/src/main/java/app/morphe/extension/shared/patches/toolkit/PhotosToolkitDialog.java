package app.morphe.extension.shared.patches.toolkit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.patches.toolkit.PhotosDatabaseScanner.AlbumSummary;
import app.morphe.extension.shared.patches.toolkit.PhotosDatabaseScanner.MediaItemSummary;
import app.morphe.extension.shared.patches.toolkit.PhotosDatabaseScanner.ScanResult;

/**
 * 3-Stage Selection Library Toolkit for Morphe Google Photos.
 * Matches the Google-Photos-Toolkit (GPTK) interface:
 *  Stage 1: SELECT SOURCE (Library, Favorites, Archive, Trash, Camera)
 *  Stage 2: FILTERS (Space: Consuming vs Non-Consuming vs All, with size presets)
 *  Stage 3: CHOOSE ACTION (Add to Existing Album, New Album, Trash, Export CSV, Clear Log)
 *  Live Console / Terminal Log: Monospace real-time execution log
 */
public final class PhotosToolkitDialog {

    private static final ExecutorService SCAN_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    // GPTK Dark Theme Palette
    private static final int BG_COLOR = 0xFF111216;
    private static final int CARD_BG = 0xFF1A1C23;
    private static final int CARD_BORDER = 0xFF2B2F3D;
    private static final int CONSOLE_BG = 0xFF0B0C10;
    private static final int CONSOLE_TEXT = 0xFF86EFAC; // Mint green
    private static final int CONSOLE_DIM = 0xFF64748B;
    private static final int ACCENT_BLUE = 0xFF2563EB;
    private static final int ACCENT_ACTIVE = 0xFF3B82F6;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFF94A3B8;
    private static final int BTN_INACTIVE = 0xFF232630;
    private static final int BTN_BORDER = 0xFF34394A;

    public enum SourceType {
        LIBRARY("Library", "📚"),
        FAVORITES("Favorites", "⭐"),
        ARCHIVE("Archive", "🗄️"),
        TRASH("Trash", "🗑️"),
        CAMERA("Camera", "📸");

        public final String label;
        public final String icon;

        SourceType(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public enum SpaceMode {
        CONSUMING("⚡ Consuming (Quota > 0)"),
        NON_CONSUMING("🌿 Non-Consuming (0 Quota)"),
        ALL("🌐 All Media (Any Space)");

        public final String label;

        SpaceMode(String label) {
            this.label = label;
        }
    }

    // Active state
    private static SourceType currentSource = SourceType.LIBRARY;
    private static SpaceMode currentSpaceMode = SpaceMode.CONSUMING;
    private static long minSizeBytes = 0;
    private static ScanResult activeScanResult = null;
    private static final StringBuilder logBuffer = new StringBuilder();

    private PhotosToolkitDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(BG_COLOR));
        }

        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_COLOR);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ── 1. Top App Bar & Notch/Cutout Handling ─────────────────────────────
        int statusBarHeight = 0;
        int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = activity.getResources().getDimensionPixelSize(resourceId);
        }
        if (statusBarHeight <= 0) {
            statusBarHeight = (int) (32 * density);
        }

        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding((int) (16 * density), statusBarHeight + (int) (8 * density), (int) (16 * density), (int) (10 * density));
        topBar.setBackgroundColor(BG_COLOR);

        // WindowInsets listener to dynamically handle camera cutouts / notches
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    top = Math.max(top, cutout.getSafeInsetTop());
                }
            }
            if (top > 0) {
                topBar.setPadding((int) (16 * density), top + (int) (8 * density), (int) (16 * density), (int) (10 * density));
            }
            return insets;
        });

        TextView logoTv = new TextView(activity);
        logoTv.setText("⚡ Google Photos Toolkit");
        logoTv.setTextSize(16);
        logoTv.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        logoTv.setTextColor(TEXT_WHITE);
        logoTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        topBar.addView(logoTv);

        TextView closeBtn = new TextView(activity);
        closeBtn.setText("✕");
        closeBtn.setTextSize(20);
        closeBtn.setTextColor(TEXT_MUTED);
        closeBtn.setPadding((int) (10 * density), (int) (4 * density), (int) (6 * density), (int) (4 * density));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        topBar.addView(closeBtn);

        root.addView(topBar);

        // ── 2. Scrollable Body containing 3 Stages ────────────────────────────
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding((int) (14 * density), (int) (8 * density), (int) (14 * density), (int) (16 * density));

        // Breadcrumbs: 1 SOURCE -> 2 FILTER -> 3 ACTION (safely below top bar & notch)
        LinearLayout breadcrumbLayout = new LinearLayout(activity);
        breadcrumbLayout.setOrientation(LinearLayout.HORIZONTAL);
        breadcrumbLayout.setGravity(Gravity.CENTER);
        breadcrumbLayout.setPadding(0, 0, 0, (int) (8 * density));

        TextView step1 = createStepBadge(activity, density, "1", "SOURCE", true);
        TextView arrow1 = createArrowView(activity, density);
        TextView step2 = createStepBadge(activity, density, "2", "FILTER", true);
        TextView arrow2 = createArrowView(activity, density);
        TextView step3 = createStepBadge(activity, density, "3", "ACTION", true);

        breadcrumbLayout.addView(step1);
        breadcrumbLayout.addView(arrow1);
        breadcrumbLayout.addView(step2);
        breadcrumbLayout.addView(arrow2);
        breadcrumbLayout.addView(step3);
        body.addView(breadcrumbLayout);

        // Loading ProgressBar
        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);
        body.addView(progressBar);

        // ── STAGE 1: SELECT SOURCE ───────────────────────────────────────────
        LinearLayout stage1Card = createCard(activity, density);
        LinearLayout stage1Header = createSectionHeader(activity, density, "1  SOURCE MEDIA", "Select library subset to process");
        stage1Card.addView(stage1Header);

        HorizontalScrollView sourceHsv = new HorizontalScrollView(activity);
        sourceHsv.setHorizontalScrollBarEnabled(false);
        LinearLayout sourceRow = new LinearLayout(activity);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);
        sourceRow.setPadding(0, (int) (4 * density), 0, (int) (4 * density));

        List<Button> sourceButtons = new ArrayList<>();
        for (SourceType st : SourceType.values()) {
            Button btn = new Button(activity);
            btn.setText(st.icon + " " + st.label);
            btn.setTextSize(11);
            btn.setPadding((int) (10 * density), (int) (6 * density), (int) (10 * density), (int) (6 * density));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, (int) (6 * density), 0);
            btn.setLayoutParams(lp);

            updateButtonStyle(btn, density, st == currentSource);
            sourceButtons.add(btn);
            sourceRow.addView(btn);
        }
        sourceHsv.addView(sourceRow);
        stage1Card.addView(sourceHsv);

        TextView sourceCountTv = new TextView(activity);
        sourceCountTv.setTextSize(11);
        sourceCountTv.setTextColor(TEXT_MUTED);
        sourceCountTv.setPadding(0, (int) (4 * density), 0, 0);
        stage1Card.addView(sourceCountTv);

        body.addView(stage1Card);

        // ── STAGE 2: FILTERS (SPACE & SIZE) ──────────────────────────────────
        LinearLayout stage2Card = createCard(activity, density);

        LinearLayout s2HeaderRow = new LinearLayout(activity);
        s2HeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        s2HeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout stage2Header = createSectionHeader(activity, density, "2  FILTERS", "Filter by Google account quota & size");
        stage2Header.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        s2HeaderRow.addView(stage2Header);

        TextView resetFiltersBtn = new TextView(activity);
        resetFiltersBtn.setText("↺ RESET");
        resetFiltersBtn.setTextSize(11);
        resetFiltersBtn.setTextColor(ACCENT_ACTIVE);
        resetFiltersBtn.setTypeface(null, Typeface.BOLD);
        resetFiltersBtn.setPadding((int) (8 * density), (int) (4 * density), (int) (8 * density), (int) (4 * density));
        s2HeaderRow.addView(resetFiltersBtn);
        stage2Card.addView(s2HeaderRow);

        // Subheader: SPACE CONSUMPTION
        TextView spaceSub = new TextView(activity);
        spaceSub.setText("SPACE CONSUMPTION");
        spaceSub.setTextSize(10);
        spaceSub.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        spaceSub.setTextColor(TEXT_MUTED);
        spaceSub.setPadding(0, (int) (6 * density), 0, (int) (4 * density));
        stage2Card.addView(spaceSub);

        // Segmented Horizontal Row for Space Mode
        LinearLayout spaceSegmentRow = new LinearLayout(activity);
        spaceSegmentRow.setOrientation(LinearLayout.HORIZONTAL);
        spaceSegmentRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<Button> spaceModeButtons = new ArrayList<>();
        final String[] spaceModeLabels = {"⚡ Consuming", "🌿 Free Saver", "🌐 All Media"};

        for (int i = 0; i < SpaceMode.values().length; i++) {
            SpaceMode mode = SpaceMode.values()[i];
            Button btn = new Button(activity);
            btn.setText(spaceModeLabels[i]);
            btn.setTextSize(10);
            btn.setPadding((int) (4 * density), (int) (7 * density), (int) (4 * density), (int) (7 * density));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            if (i < SpaceMode.values().length - 1) {
                lp.setMargins(0, 0, (int) (4 * density), 0);
            }
            btn.setLayoutParams(lp);

            updateButtonStyle(btn, density, mode == currentSpaceMode);
            spaceModeButtons.add(btn);
            spaceSegmentRow.addView(btn);
        }
        stage2Card.addView(spaceSegmentRow);

        TextView spaceHintTv = new TextView(activity);
        spaceHintTv.setTextSize(10);
        spaceHintTv.setTextColor(TEXT_MUTED);
        spaceHintTv.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
        stage2Card.addView(spaceHintTv);

        Runnable updateSpaceHint = () -> {
            if (currentSpaceMode == SpaceMode.CONSUMING) {
                spaceHintTv.setText("• Matches photos/videos charging Google Account quota");
            } else if (currentSpaceMode == SpaceMode.NON_CONSUMING) {
                spaceHintTv.setText("• Storage saver & Pixel free uploads (0 quota charged)");
            } else {
                spaceHintTv.setText("• All media items regardless of quota status");
            }
        };
        updateSpaceHint.run();

        // Size Threshold Row (5 compact weighted buttons)
        TextView sizeSub = new TextView(activity);
        sizeSub.setText("MINIMUM SIZE THRESHOLD");
        sizeSub.setTextSize(10);
        sizeSub.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        sizeSub.setTextColor(TEXT_MUTED);
        sizeSub.setPadding(0, (int) (6 * density), 0, (int) (4 * density));
        stage2Card.addView(sizeSub);

        LinearLayout sizeRow = new LinearLayout(activity);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final long[] sizeThresholds = {0, 5L * 1024 * 1024, 10L * 1024 * 1024, 50L * 1024 * 1024, 100L * 1024 * 1024};
        final String[] sizeLabels = {"Any", "> 5 MB", "> 10 MB", "> 50 MB", "> 100 MB"};
        List<Button> sizeButtons = new ArrayList<>();

        for (int i = 0; i < sizeLabels.length; i++) {
            long thresh = sizeThresholds[i];
            Button b = new Button(activity);
            b.setText(sizeLabels[i]);
            b.setTextSize(10);
            b.setPadding((int) (2 * density), (int) (6 * density), (int) (2 * density), (int) (6 * density));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            if (i < sizeLabels.length - 1) {
                bLp.setMargins(0, 0, (int) (4 * density), 0);
            }
            b.setLayoutParams(bLp);

            updateButtonStyle(b, density, thresh == minSizeBytes);
            sizeButtons.add(b);
            sizeRow.addView(b);
        }
        stage2Card.addView(sizeRow);

        // Matched Status Banner
        TextView matchBanner = new TextView(activity);
        matchBanner.setTextSize(12);
        matchBanner.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        matchBanner.setTextColor(0xFF38BDF8); // Light Cyan
        matchBanner.setPadding(0, (int) (8 * density), 0, (int) (2 * density));
        stage2Card.addView(matchBanner);

        body.addView(stage2Card);

        // ── STAGE 3: CHOOSE ACTION ───────────────────────────────────────────
        LinearLayout stage3Card = createCard(activity, density);
        LinearLayout stage3Header = createSectionHeader(activity, density, "3  ACTIONS", "Execute operation on matching media");
        stage3Card.addView(stage3Header);

        // Action Row 1: Add to Album & New Album
        LinearLayout actionRow1 = new LinearLayout(activity);
        actionRow1.setOrientation(LinearLayout.HORIZONTAL);
        actionRow1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionRow1.setPadding(0, 0, 0, (int) (5 * density));

        Button addToAlbumBtn = new Button(activity);
        addToAlbumBtn.setText("📁 ADD TO ALBUM");
        addToAlbumBtn.setTextSize(11);
        addToAlbumBtn.setTypeface(null, Typeface.BOLD);
        styleActionButton(addToAlbumBtn, density, true);
        LinearLayout.LayoutParams lpR1A = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpR1A.setMargins(0, 0, (int) (4 * density), 0);
        addToAlbumBtn.setLayoutParams(lpR1A);
        actionRow1.addView(addToAlbumBtn);

        Button newAlbumBtn = new Button(activity);
        newAlbumBtn.setText("➕ NEW ALBUM");
        newAlbumBtn.setTextSize(11);
        newAlbumBtn.setTypeface(null, Typeface.BOLD);
        styleActionButton(newAlbumBtn, density, true);
        LinearLayout.LayoutParams lpR1B = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpR1B.setMargins((int) (4 * density), 0, 0, 0);
        newAlbumBtn.setLayoutParams(lpR1B);
        actionRow1.addView(newAlbumBtn);
        stage3Card.addView(actionRow1);

        // Action Row 2: Preview & Photos App Upload Flow
        LinearLayout actionRow2 = new LinearLayout(activity);
        actionRow2.setOrientation(LinearLayout.HORIZONTAL);
        actionRow2.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionRow2.setPadding(0, 0, 0, (int) (5 * density));

        Button previewItemsBtn = new Button(activity);
        previewItemsBtn.setText("👁️ PREVIEW (0)");
        previewItemsBtn.setTextSize(11);
        previewItemsBtn.setTypeface(null, Typeface.BOLD);
        styleActionButton(previewItemsBtn, density, false);
        LinearLayout.LayoutParams lpR2Preview = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpR2Preview.setMargins(0, 0, (int) (4 * density), 0);
        previewItemsBtn.setLayoutParams(lpR2Preview);
        actionRow2.addView(previewItemsBtn);

        Button photosAppBtn = new Button(activity);
        photosAppBtn.setText("📤 VIA PHOTOS APP");
        photosAppBtn.setTextSize(11);
        photosAppBtn.setTypeface(null, Typeface.BOLD);
        styleActionButton(photosAppBtn, density, false);
        LinearLayout.LayoutParams lpR2App = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpR2App.setMargins((int) (4 * density), 0, 0, 0);
        photosAppBtn.setLayoutParams(lpR2App);
        actionRow2.addView(photosAppBtn);
        stage3Card.addView(actionRow2);

        // Action Row 3: Trash & Archive
        LinearLayout actionRow3 = new LinearLayout(activity);
        actionRow3.setOrientation(LinearLayout.HORIZONTAL);
        actionRow3.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionRow3.setPadding(0, 0, 0, (int) (5 * density));

        Button trashBtn = new Button(activity);
        trashBtn.setText("🗑️ TRASH");
        trashBtn.setTextSize(11);
        trashBtn.setTypeface(null, Typeface.BOLD);
        styleActionButton(trashBtn, density, false);
        LinearLayout.LayoutParams lpR3A = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpR3A.setMargins(0, 0, (int) (4 * density), 0);
        trashBtn.setLayoutParams(lpR3A);
        actionRow3.addView(trashBtn);

        Button archiveBtn = new Button(activity);
        archiveBtn.setText("📦 ARCHIVE");
        archiveBtn.setTextSize(11);
        archiveBtn.setTypeface(null, Typeface.BOLD);
        styleActionButton(archiveBtn, density, false);
        LinearLayout.LayoutParams lpR3B = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpR3B.setMargins((int) (4 * density), 0, 0, 0);
        archiveBtn.setLayoutParams(lpR3B);
        actionRow3.addView(archiveBtn);
        stage3Card.addView(actionRow3);

        // Action Row 4: Favorite & Export CSV
        LinearLayout actionRow4 = new LinearLayout(activity);
        actionRow4.setOrientation(LinearLayout.HORIZONTAL);
        actionRow4.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionRow4.setPadding(0, 0, 0, (int) (5 * density));

        Button favoriteBtn = new Button(activity);
        favoriteBtn.setText("⭐ FAVORITE");
        favoriteBtn.setTextSize(11);
        favoriteBtn.setTypeface(null, Typeface.BOLD);
        styleActionButton(favoriteBtn, density, false);
        LinearLayout.LayoutParams lpR4A = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpR4A.setMargins(0, 0, (int) (4 * density), 0);
        favoriteBtn.setLayoutParams(lpR4A);
        actionRow4.addView(favoriteBtn);

        Button exportCsvBtn = new Button(activity);
        exportCsvBtn.setText("💾 EXPORT CSV");
        exportCsvBtn.setTextSize(11);
        exportCsvBtn.setTypeface(null, Typeface.BOLD);
        styleActionButton(exportCsvBtn, density, false);
        LinearLayout.LayoutParams lpR4B = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpR4B.setMargins((int) (4 * density), 0, 0, 0);
        exportCsvBtn.setLayoutParams(lpR4B);
        actionRow4.addView(exportCsvBtn);
        stage3Card.addView(actionRow4);

        // ── LIVE TERMINAL CONSOLE ─────────────────────────────────────────────
        LinearLayout consoleHeaderRow = new LinearLayout(activity);
        consoleHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        consoleHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        consoleHeaderRow.setPadding(0, (int) (6 * density), 0, (int) (4 * density));

        TextView consoleHeader = new TextView(activity);
        consoleHeader.setText("LIVE CONSOLE LOG");
        consoleHeader.setTextSize(10);
        consoleHeader.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        consoleHeader.setTextColor(TEXT_MUTED);
        consoleHeader.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        consoleHeaderRow.addView(consoleHeader);

        TextView clearLogBtn = new TextView(activity);
        clearLogBtn.setText("🧹 CLEAR");
        clearLogBtn.setTextSize(10);
        clearLogBtn.setTextColor(ACCENT_ACTIVE);
        clearLogBtn.setTypeface(null, Typeface.BOLD);
        clearLogBtn.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));
        consoleHeaderRow.addView(clearLogBtn);

        stage3Card.addView(consoleHeaderRow);

        ScrollView consoleScrollView = new ScrollView(activity);
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (110 * density));
        consoleScrollView.setLayoutParams(csLp);

        GradientDrawable consoleBg = new GradientDrawable();
        consoleBg.setColor(CONSOLE_BG);
        consoleBg.setCornerRadius(8 * density);
        consoleBg.setStroke((int) (1 * density), CARD_BORDER);
        consoleScrollView.setBackground(consoleBg);
        consoleScrollView.setPadding((int) (8 * density), (int) (6 * density), (int) (8 * density), (int) (6 * density));

        TextView consoleTv = new TextView(activity);
        consoleTv.setTypeface(Typeface.MONOSPACE);
        consoleTv.setTextSize(10);
        consoleTv.setTextColor(CONSOLE_TEXT);
        consoleTv.setLineSpacing(2 * density, 1.1f);
        consoleScrollView.addView(consoleTv);

        stage3Card.addView(consoleScrollView);

        // Footer: v1.1.0 and AUTO-SCROLL
        LinearLayout footerRow = new LinearLayout(activity);
        footerRow.setOrientation(LinearLayout.HORIZONTAL);
        footerRow.setPadding(0, (int) (6 * density), 0, 0);

        TextView verTv = new TextView(activity);
        verTv.setText("v1.1.0 • Google Photos Toolkit Mobile");
        verTv.setTextSize(10);
        verTv.setTextColor(CONSOLE_DIM);
        verTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        footerRow.addView(verTv);

        TextView autoScrollTv = new TextView(activity);
        autoScrollTv.setText("☑ AUTO-SCROLL");
        autoScrollTv.setTextSize(10);
        autoScrollTv.setTextColor(ACCENT_ACTIVE);
        footerRow.addView(autoScrollTv);

        stage3Card.addView(footerRow);
        body.addView(stage3Card);

        scrollView.addView(body);
        root.addView(scrollView);

        dialog.setContentView(root);
        dialog.show();

        // ── Helper to append logs to console (Thread Safe) ─────────────────────
        java.util.function.Consumer<String> appendLog = (msg) -> {
            String time = TIME_FORMAT.format(new Date());
            String line = "[" + time + "] " + msg + "\n";
            MAIN_HANDLER.post(() -> {
                logBuffer.append(line);
                consoleTv.setText(logBuffer.toString());
                consoleScrollView.post(() -> consoleScrollView.fullScroll(View.FOCUS_DOWN));
            });
        };

        // If buffer was empty, start with init logs
        if (logBuffer.length() == 0) {
            appendLog.accept("GPTK Core v1.1.0 Initialized");
            appendLog.accept("Waiting for database scan...");
        } else {
            consoleTv.setText(logBuffer.toString());
            consoleScrollView.post(() -> consoleScrollView.fullScroll(View.FOCUS_DOWN));
        }

        // ── Update Logic for Filtered Items ───────────────────────────────────
        final List<MediaItemSummary> filteredList = new ArrayList<>();

        Runnable updateFilteredItems = () -> {
            filteredList.clear();
            if (activeScanResult == null) {
                matchBanner.setText("Scanning media...");
                return;
            }

            List<MediaItemSummary> sourceList;
            switch (currentSource) {
                case FAVORITES:
                    sourceList = activeScanResult.favoriteItems;
                    break;
                case ARCHIVE:
                    sourceList = activeScanResult.archivedItems;
                    break;
                case TRASH:
                    sourceList = activeScanResult.trashedItems;
                    break;
                case CAMERA:
                    sourceList = activeScanResult.cameraItems;
                    break;
                case LIBRARY:
                default:
                    sourceList = activeScanResult.allMediaItems;
                    break;
            }

            sourceCountTv.setText(currentSource.label + ": " + sourceList.size() + " items available in database");

            long totalQuotaBytes = 0;
            long totalSizeBytes = 0;

            for (MediaItemSummary item : sourceList) {
                if (item.sizeBytes < minSizeBytes) continue;

                boolean matchesSpace;
                if (currentSpaceMode == SpaceMode.CONSUMING) {
                    matchesSpace = item.isSpaceConsuming();
                } else if (currentSpaceMode == SpaceMode.NON_CONSUMING) {
                    matchesSpace = !item.isSpaceConsuming();
                } else {
                    matchesSpace = true;
                }

                if (matchesSpace) {
                    filteredList.add(item);
                    totalQuotaBytes += item.quotaChargedBytes;
                    totalSizeBytes += item.sizeBytes;
                }
            }

            String quotaStr = totalQuotaBytes > 0 ? " (" + formatSize(totalQuotaBytes) + " quota)" : " (" + formatSize(totalSizeBytes) + " size)";
            matchBanner.setText("⚡ " + filteredList.size() + " items match filter" + quotaStr);
            previewItemsBtn.setText("👁️ PREVIEW (" + filteredList.size() + ")");
        };

        // ── Setup Click Handlers ──────────────────────────────────────────────

        // 1. Source Buttons
        for (int i = 0; i < SourceType.values().length; i++) {
            final SourceType st = SourceType.values()[i];
            final Button b = sourceButtons.get(i);
            b.setOnClickListener(v -> {
                currentSource = st;
                for (int j = 0; j < SourceType.values().length; j++) {
                    updateButtonStyle(sourceButtons.get(j), density, SourceType.values()[j] == currentSource);
                }
                updateFilteredItems.run();
                appendLog.accept("Source changed to: " + st.label + " (" + filteredList.size() + " match)");
            });
        }

        // 2. Space Mode Buttons
        for (int i = 0; i < SpaceMode.values().length; i++) {
            final SpaceMode mode = SpaceMode.values()[i];
            final Button b = spaceModeButtons.get(i);
            b.setOnClickListener(v -> {
                currentSpaceMode = mode;
                for (int j = 0; j < SpaceMode.values().length; j++) {
                    updateButtonStyle(spaceModeButtons.get(j), density, SpaceMode.values()[j] == currentSpaceMode);
                }
                updateSpaceHint.run();
                updateFilteredItems.run();
                appendLog.accept("Space Filter applied: " + mode.label + " (" + filteredList.size() + " items)");
            });
        }

        // 3. Size Buttons
        for (int i = 0; i < sizeLabels.length; i++) {
            final long thresh = sizeThresholds[i];
            final Button b = sizeButtons.get(i);
            b.setOnClickListener(v -> {
                minSizeBytes = thresh;
                for (int j = 0; j < sizeThresholds.length; j++) {
                    updateButtonStyle(sizeButtons.get(j), density, sizeThresholds[j] == minSizeBytes);
                }
                updateFilteredItems.run();
                appendLog.accept("Size Threshold: " + (thresh > 0 ? "> " + formatSize(thresh) : "Any") + " (" + filteredList.size() + " match)");
            });
        }

        // 4. Reset Filters
        resetFiltersBtn.setOnClickListener(v -> {
            currentSpaceMode = SpaceMode.CONSUMING;
            minSizeBytes = 0;
            // update UI
            for (int j = 0; j < SpaceMode.values().length; j++) {
                updateButtonStyle(spaceModeButtons.get(j), density, SpaceMode.values()[j] == currentSpaceMode);
            }
            updateSpaceHint.run();
            for (int j = 0; j < sizeThresholds.length; j++) {
                updateButtonStyle(sizeButtons.get(j), density, sizeThresholds[j] == minSizeBytes);
            }
            updateFilteredItems.run();
            appendLog.accept("Filters reset to default: Space-Consuming");
        });

        // Define reusable library refresh routine
        final Runnable[] refreshLibraryRef = new Runnable[1];
        refreshLibraryRef[0] = () -> {
            progressBar.setVisibility(View.VISIBLE);
            SCAN_EXECUTOR.execute(() -> {
                ScanResult result = PhotosDatabaseScanner.scanLibrary(activity);
                MAIN_HANDLER.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    activeScanResult = result;
                    updateFilteredItems.run();

                    appendLog.accept("Database: " + result.scannedDatabase + " (" + result.totalMediaCount + " media items)");
                    if (!result.existingAlbums.isEmpty()) {
                        appendLog.accept("Cached Albums Discovered: " + result.existingAlbums.size() + " album(s)");
                        for (AlbumSummary a : result.existingAlbums) {
                            appendLog.accept(" • Album: \"" + a.title + "\" (" + a.itemCount + " items)");
                        }
                    } else {
                        appendLog.accept("Albums: 0 collections found in local cache");
                    }
                    appendLog.accept("Filter Ready: " + filteredList.size() + " items match Space-Consuming");
                });
            });
        };
        Runnable refreshLibrary = () -> refreshLibraryRef[0].run();

        // 5. Actions: Add to Album
        addToAlbumBtn.setOnClickListener(v -> {
            if (filteredList.isEmpty()) {
                Toast.makeText(activity, "No items match current filter", Toast.LENGTH_SHORT).show();
                return;
            }
            showAddToExistingAlbumDialog(activity, filteredList, activeScanResult, appendLog, refreshLibrary);
        });

        // 5b. Actions: New Album
        newAlbumBtn.setOnClickListener(v -> {
            if (filteredList.isEmpty()) {
                Toast.makeText(activity, "No items match current filter", Toast.LENGTH_SHORT).show();
                return;
            }
            showCreateNewAlbumDialog(activity, filteredList, activeScanResult, appendLog, refreshLibrary);
        });

        // 5c. Actions: Preview Items
        previewItemsBtn.setOnClickListener(v -> {
            if (filteredList.isEmpty()) {
                Toast.makeText(activity, "No items match current filter", Toast.LENGTH_SHORT).show();
                return;
            }
            showItemsPreviewDialog(activity, filteredList);
        });

        // 6. Actions: Direct Photos App flow
        photosAppBtn.setOnClickListener(v -> {
            if (filteredList.isEmpty()) {
                Toast.makeText(activity, "No items match current filter", Toast.LENGTH_SHORT).show();
                appendLog.accept("Action aborted: No matching items");
                return;
            }
            executeDirectPhotosAppFlow(activity, filteredList, appendLog);
        });

        // 7. Actions: Trash
        trashBtn.setOnClickListener(v -> {
            if (filteredList.isEmpty()) {
                Toast.makeText(activity, "No items match current filter", Toast.LENGTH_SHORT).show();
                return;
            }
            executeTrashAction(activity, filteredList, activeScanResult != null ? activeScanResult.scannedDatabase : null, appendLog, refreshLibrary);
        });

        // 7b. Actions: Archive
        archiveBtn.setOnClickListener(v -> {
            if (filteredList.isEmpty()) {
                Toast.makeText(activity, "No items match current filter", Toast.LENGTH_SHORT).show();
                return;
            }
            executeArchiveAction(activity, filteredList, activeScanResult != null ? activeScanResult.scannedDatabase : null, appendLog, refreshLibrary);
        });

        // 7c. Actions: Favorite
        favoriteBtn.setOnClickListener(v -> {
            if (filteredList.isEmpty()) {
                Toast.makeText(activity, "No items match current filter", Toast.LENGTH_SHORT).show();
                return;
            }
            executeFavoriteAction(activity, filteredList, activeScanResult != null ? activeScanResult.scannedDatabase : null, appendLog, refreshLibrary);
        });

        // 8. Actions: Export CSV
        exportCsvBtn.setOnClickListener(v -> {
            if (filteredList.isEmpty()) {
                Toast.makeText(activity, "No items match current filter", Toast.LENGTH_SHORT).show();
                return;
            }
            exportCsv(activity, filteredList, appendLog);
        });

        // 9. Actions: Clear Log
        clearLogBtn.setOnClickListener(v -> {
            logBuffer.setLength(0);
            consoleTv.setText("");
            appendLog.accept("Log cleared");
        });

        // ── Initial Asynchronous Scan ─────────────────────────────────────────
        refreshLibrary.run();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Album Actions (Direct In-App DB Execution & Direct Photos Component)
    // ─────────────────────────────────────────────────────────────────────────

    private static void showAddToExistingAlbumDialog(Activity activity, List<MediaItemSummary> items,
                                                     PhotosDatabaseScanner.ScanResult scanResult,
                                                     java.util.function.Consumer<String> appendLog,
                                                     Runnable onComplete) {
        if (items == null || items.isEmpty()) {
            appendLog.accept(">> Error: No items to add.");
            return;
        }

        if (scanResult == null || scanResult.existingAlbums.isEmpty()) {
            new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("📁 Add to Album")
                    .setMessage("No existing albums were found in local cache.\n\nWould you like to create a new album for these " + items.size() + " items?")
                    .setPositiveButton("Create New Album", (d, w) -> {
                        showCreateNewAlbumDialog(activity, items, scanResult, appendLog, onComplete);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        List<AlbumSummary> albums = scanResult.existingAlbums;
        String[] titles = new String[albums.size() + 1];
        titles[0] = "➕ [ Create New Album ]";
        for (int i = 0; i < albums.size(); i++) {
            AlbumSummary a = albums.get(i);
            titles[i + 1] = "📁 " + a.title + " (" + a.itemCount + " items)";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("📁 Select Album (" + items.size() + " items)");
        builder.setItems(titles, (dialog, which) -> {
            if (which == 0) {
                showCreateNewAlbumDialog(activity, items, scanResult, appendLog, onComplete);
            } else {
                AlbumSummary chosen = albums.get(which - 1);
                executeAddToAlbum(activity, items, chosen.title, chosen.mediaKey,
                        scanResult.scannedDatabase, appendLog, onComplete);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static void showCreateNewAlbumDialog(Activity activity, List<MediaItemSummary> items,
                                                 PhotosDatabaseScanner.ScanResult scanResult,
                                                 java.util.function.Consumer<String> appendLog,
                                                 Runnable onComplete) {
        if (items == null || items.isEmpty()) {
            appendLog.accept(">> Error: No items to add.");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("➕ Create New Album");

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        float density = activity.getResources().getDisplayMetrics().density;
        layout.setPadding((int) (20 * density), (int) (12 * density), (int) (20 * density), (int) (8 * density));

        TextView promptTv = new TextView(activity);
        promptTv.setText("Enter title for new album with " + items.size() + " items:");
        promptTv.setTextColor(TEXT_MUTED);
        promptTv.setTextSize(13);
        layout.addView(promptTv);

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setTextColor(TEXT_WHITE);
        input.setHint("e.g. Cleaned Media, Vacation...");
        input.setHintTextColor(0xFF71717A);
        String defaultTitle = "Album " + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        input.setText(defaultTitle);
        input.setSelection(defaultTitle.length());
        layout.addView(input);

        builder.setView(layout);
        builder.setPositiveButton("Create & Add", (dialog, which) -> {
            String title = input.getText().toString().trim();
            if (title.isEmpty()) {
                title = defaultTitle;
            }
            executeAddToAlbum(activity, items, title, null,
                    scanResult != null ? scanResult.scannedDatabase : null,
                    appendLog, onComplete);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static void executeAddToAlbum(Activity activity, List<MediaItemSummary> items,
                                          String albumName, String albumMediaKey,
                                          String scannedDb,
                                          java.util.function.Consumer<String> appendLog,
                                          Runnable onComplete) {
        if (items == null || items.isEmpty()) {
            appendLog.accept(">> Error: No items to add.");
            return;
        }

        appendLog.accept(">> Action: ADD TO ALBUM (" + items.size() + " items -> \"" + albumName + "\")");
        appendLog.accept("Executing native SQLite insertion with non-null protobuf...");

        SCAN_EXECUTOR.execute(() -> {
            PhotosDatabaseScanner.AlbumActionResult res = PhotosDatabaseScanner.addItemsToAlbum(
                    activity, scannedDb, items, albumMediaKey, albumName, appendLog
            );
            MAIN_HANDLER.post(() -> {
                if (res.success) {
                    Toast.makeText(activity, "Added " + res.count + " items to \"" + res.albumTitle + "\"", Toast.LENGTH_LONG).show();
                    appendLog.accept(">> SUCCESS: " + res.message);
                    if (onComplete != null) onComplete.run();
                } else {
                    Toast.makeText(activity, "Failed to add items: " + res.message, Toast.LENGTH_LONG).show();
                    appendLog.accept(">> FAILED: " + res.message);
                }
            });
        });
    }

    private static void executeDirectPhotosAppFlow(Activity activity, List<MediaItemSummary> items,
                                                  java.util.function.Consumer<String> appendLog) {
        if (items == null || items.isEmpty()) {
            appendLog.accept(">> Error: No items to dispatch.");
            return;
        }

        appendLog.accept(">> Action: DIRECT PHOTOS APP DISPATCH (" + items.size() + " items)");

        ArrayList<Uri> uris = new ArrayList<>();
        int localCount = 0;
        int remoteOnlyCount = 0;

        try {
            try {
                android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder().build());
            } catch (Throwable ignored) {}

            Class<?> fpClass = null;
            java.lang.reflect.Method getUriMethod = null;
            try {
                fpClass = Class.forName("androidx.core.content.FileProvider");
                getUriMethod = fpClass.getMethod("getUriForFile", Context.class, String.class, File.class);
            } catch (Throwable ignored) {}

            String authority = activity.getPackageName() + ".fileprovider";

            for (MediaItemSummary item : items) {
                if (item.filepath != null && !item.filepath.isEmpty()) {
                    File f = new File(item.filepath);
                    if (f.exists()) {
                        Uri uri = null;
                        if (getUriMethod != null) {
                            try {
                                uri = (Uri) getUriMethod.invoke(null, activity, authority, f);
                            } catch (Throwable ignored) {}
                        }
                        if (uri == null) {
                            uri = Uri.fromFile(f);
                        }
                        uris.add(uri);
                        localCount++;
                        continue;
                    }
                }
                remoteOnlyCount++;
            }

            appendLog.accept("Resolved " + localCount + " local file URIs (" + remoteOnlyCount + " cloud-only items)");

            if (uris.isEmpty()) {
                appendLog.accept(">> Note: Selected " + items.size() + " items are stored in Google Cloud without local cached copies.");
                Toast.makeText(activity, "Items are stored in Google Cloud only. View in Preview or Export CSV.", Toast.LENGTH_LONG).show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("*/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // Explicitly target Google Photos upload activity directly, skipping OS share sheet
            intent.setComponent(new ComponentName(activity.getPackageName(), "com.google.android.apps.photos.upload.intent.UploadContentActivity"));

            activity.startActivity(intent);
            appendLog.accept(">> Dispatched directly to Google Photos UploadContentActivity for " + uris.size() + " items.");
        } catch (Throwable t) {
            appendLog.accept(">> Error launching Photos upload: " + t.getMessage());
            Toast.makeText(activity, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void executeTrashAction(Activity activity, List<MediaItemSummary> items,
                                           String scannedDb,
                                           java.util.function.Consumer<String> appendLog,
                                           Runnable onComplete) {
        if (items == null || items.isEmpty()) {
            appendLog.accept(">> Error: No items to trash.");
            return;
        }

        AlertDialog.Builder confirm = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        confirm.setTitle("🗑️ Move to Trash?");
        confirm.setMessage("Are you sure you want to move " + items.size() + " items to Google Photos Trash?\n\nItems will be hidden from library and marked for deletion.");
        confirm.setPositiveButton("Trash " + items.size() + " Items", (d, w) -> {
            appendLog.accept(">> Action: BATCH TRASH (Native DEX Execution)");
            appendLog.accept("Moving " + items.size() + " items to Trash in " + (scannedDb != null ? scannedDb : "active database") + "...");
            SCAN_EXECUTOR.execute(() -> {
                int count = PhotosDatabaseScanner.batchTrashItems(activity, scannedDb, items, appendLog);
                MAIN_HANDLER.post(() -> {
                    appendLog.accept(">> SUCCESS: Moved " + count + " items to Trash.");
                    Toast.makeText(activity, "Moved " + count + " items to Trash", Toast.LENGTH_SHORT).show();
                    if (onComplete != null) onComplete.run();
                });
            });
        });
        confirm.setNegativeButton("Cancel", null);
        confirm.show();
    }

    private static void executeArchiveAction(Activity activity, List<MediaItemSummary> items,
                                             String scannedDb,
                                             java.util.function.Consumer<String> appendLog,
                                             Runnable onComplete) {
        if (items == null || items.isEmpty()) {
            appendLog.accept(">> Error: No items to archive.");
            return;
        }

        AlertDialog.Builder confirm = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        confirm.setTitle("📦 Archive Photos?");
        confirm.setMessage("Move " + items.size() + " items to Google Photos Archive?\n\nArchived items will be hidden from the main Photos tab.");
        confirm.setPositiveButton("Archive " + items.size() + " Items", (d, w) -> {
            appendLog.accept(">> Action: BATCH ARCHIVE (Native DEX Execution)");
            appendLog.accept("Archiving " + items.size() + " items in " + (scannedDb != null ? scannedDb : "active database") + "...");
            SCAN_EXECUTOR.execute(() -> {
                int count = PhotosDatabaseScanner.batchArchiveItems(activity, scannedDb, items, true, appendLog);
                MAIN_HANDLER.post(() -> {
                    appendLog.accept(">> SUCCESS: Archived " + count + " items.");
                    Toast.makeText(activity, "Archived " + count + " items", Toast.LENGTH_SHORT).show();
                    if (onComplete != null) onComplete.run();
                });
            });
        });
        confirm.setNegativeButton("Cancel", null);
        confirm.show();
    }

    private static void executeFavoriteAction(Activity activity, List<MediaItemSummary> items,
                                              String scannedDb,
                                              java.util.function.Consumer<String> appendLog,
                                              Runnable onComplete) {
        if (items == null || items.isEmpty()) {
            appendLog.accept(">> Error: No items to favorite.");
            return;
        }

        appendLog.accept(">> Action: BATCH FAVORITE (Native DEX Execution)");
        appendLog.accept("Favoriting " + items.size() + " items in " + (scannedDb != null ? scannedDb : "active database") + "...");
        SCAN_EXECUTOR.execute(() -> {
            int count = PhotosDatabaseScanner.batchFavoriteItems(activity, scannedDb, items, true, appendLog);
            MAIN_HANDLER.post(() -> {
                appendLog.accept(">> SUCCESS: Favorited " + count + " items.");
                Toast.makeText(activity, "Favorited " + count + " items", Toast.LENGTH_SHORT).show();
                if (onComplete != null) onComplete.run();
            });
        });
    }

    private static void exportCsv(Activity activity, List<MediaItemSummary> items,
                                  java.util.function.Consumer<String> appendLog) {
        appendLog.accept(">> Action: EXPORT CSV");
        SCAN_EXECUTOR.execute(() -> {
            try {
                File dir = activity.getExternalFilesDir(null);
                if (dir == null) dir = activity.getFilesDir();
                File csvFile = new File(dir, "photos_toolkit_export_" + System.currentTimeMillis() + ".csv");

                try (FileOutputStream fos = new FileOutputStream(csvFile)) {
                    String header = "ID,MediaKey,DedupKey,Filename,SizeBytes,QuotaBytes,IsSpaceConsuming,CaptureTimestamp,IsFavorite,IsArchived,Filepath\n";
                    fos.write(header.getBytes(StandardCharsets.UTF_8));

                    for (MediaItemSummary item : items) {
                        String line = String.format(Locale.US,
                                "%d,\"%s\",\"%s\",\"%s\",%d,%d,%b,%d,%b,%b,\"%s\"\n",
                                item.id, item.mediaKey, item.dedupKey, item.filename.replace("\"", "\"\""),
                                item.sizeBytes, item.quotaChargedBytes, item.isSpaceConsuming(),
                                item.timestamp, item.isFavorite, item.isArchived, item.filepath.replace("\"", "\"\""));
                        fos.write(line.getBytes(StandardCharsets.UTF_8));
                    }
                }

                MAIN_HANDLER.post(() -> {
                    appendLog.accept("CSV Exported: " + csvFile.getAbsolutePath() + " (" + items.size() + " rows)");
                    Toast.makeText(activity, "Exported " + items.size() + " rows to " + csvFile.getName(), Toast.LENGTH_LONG).show();

                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/csv");
                    try {
                        Class<?> fpClass = Class.forName("androidx.core.content.FileProvider");
                        java.lang.reflect.Method getUriMethod = fpClass.getMethod("getUriForFile",
                                Context.class, String.class, File.class);
                        Uri uri = (Uri) getUriMethod.invoke(null, activity, activity.getPackageName() + ".fileprovider", csvFile);
                        sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Throwable t) {
                        sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(csvFile));
                    }
                    sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Google Photos Toolkit Export");
                    activity.startActivity(Intent.createChooser(sendIntent, "Share CSV Export"));
                });
            } catch (Throwable t) {
                MAIN_HANDLER.post(() -> appendLog.accept("CSV Export Failed: " + t.getMessage()));
            }
        });
    }

    private static void showItemsPreviewDialog(Activity activity, List<MediaItemSummary> items) {
        float density = activity.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(BG_COLOR));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_COLOR);

        // Header
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (14 * density));
        bar.setBackgroundColor(BG_COLOR);

        TextView back = new TextView(activity);
        back.setText("✕");
        back.setTextSize(20);
        back.setTextColor(TEXT_WHITE);
        back.setPadding((int) (4 * density), (int) (4 * density), (int) (12 * density), (int) (4 * density));
        back.setOnClickListener(v -> d.dismiss());
        bar.addView(back);

        TextView tit = new TextView(activity);
        tit.setText("Matching Media (" + items.size() + ")");
        tit.setTextSize(17);
        tit.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tit.setTextColor(TEXT_WHITE);
        bar.addView(tit);
        root.addView(bar);

        ListView listView = new ListView(activity);
        listView.setDivider(new ColorDrawable(CARD_BORDER));
        listView.setDividerHeight((int) (1 * density));
        listView.setAdapter(new ItemsPreviewAdapter(activity, items, density));
        root.addView(listView);

        d.setContentView(root);
        d.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Helpers & Styling
    // ─────────────────────────────────────────────────────────────────────────

    private static LinearLayout createCard(Context context, float density) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int) (8 * density));
        card.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_BG);
        bg.setCornerRadius(10 * density);
        bg.setStroke((int) (1 * density), CARD_BORDER);
        card.setBackground(bg);
        return card;
    }

    private static LinearLayout createSectionHeader(Context context, float density, String stepTitle, String subtitle) {
        LinearLayout hl = new LinearLayout(context);
        hl.setOrientation(LinearLayout.VERTICAL);

        TextView tv = new TextView(context);
        tv.setText(stepTitle);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        tv.setTextColor(TEXT_WHITE);
        tv.setLetterSpacing(0.04f);
        hl.addView(tv);

        if (subtitle != null) {
            TextView sub = new TextView(context);
            sub.setText(subtitle);
            sub.setTextSize(10);
            sub.setTextColor(TEXT_MUTED);
            sub.setPadding(0, (int) (1 * density), 0, (int) (4 * density));
            hl.addView(sub);
        } else {
            tv.setPadding(0, 0, 0, (int) (4 * density));
        }
        return hl;
    }

    private static TextView createStepBadge(Context context, float density, String num, String label, boolean active) {
        TextView tv = new TextView(context);
        tv.setText(num + " " + label);
        tv.setTextSize(11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(active ? ACCENT_ACTIVE : CONSOLE_DIM);
        tv.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));
        return tv;
    }

    private static TextView createArrowView(Context context, float density) {
        TextView tv = new TextView(context);
        tv.setText("→");
        tv.setTextSize(11);
        tv.setTextColor(CONSOLE_DIM);
        tv.setPadding((int) (2 * density), 0, (int) (2 * density), 0);
        return tv;
    }

    private static void updateButtonStyle(Button btn, float density, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(6 * density);
        if (active) {
            gd.setColor(ACCENT_BLUE);
            btn.setTextColor(TEXT_WHITE);
            btn.setTypeface(null, Typeface.BOLD);
        } else {
            gd.setColor(BTN_INACTIVE);
            gd.setStroke((int) (1 * density), BTN_BORDER);
            btn.setTextColor(TEXT_MUTED);
            btn.setTypeface(null, Typeface.NORMAL);
        }
        btn.setBackground(gd);
    }

    private static void updateCardRowStyle(LinearLayout row, float density, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(6 * density);
        if (active) {
            gd.setColor(0xFF1E2838); // Dark highlighted blue
            gd.setStroke((int) (1 * density), ACCENT_BLUE);
        } else {
            gd.setColor(0xFF161820);
            gd.setStroke((int) (1 * density), CARD_BORDER);
        }
        row.setBackground(gd);
    }

    private static void styleActionButton(Button btn, float density, boolean isPrimary) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6 * density);
        if (isPrimary) {
            bg.setColor(ACCENT_BLUE);
            btn.setTextColor(TEXT_WHITE);
        } else {
            bg.setColor(BTN_INACTIVE);
            bg.setStroke((int) (1 * density), BTN_BORDER);
            btn.setTextColor(TEXT_WHITE);
        }
        btn.setBackground(bg);
        btn.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preview Adapter
    // ─────────────────────────────────────────────────────────────────────────

    private static class ItemsPreviewAdapter extends BaseAdapter {
        private final Activity activity;
        private final List<MediaItemSummary> items;
        private final float density;

        public ItemsPreviewAdapter(Activity activity, List<MediaItemSummary> items, float density) {
            this.activity = activity;
            this.items = items;
            this.density = density;
        }

        @Override
        public int getCount() {
            return Math.min(items.size(), 300); // capped for performance
        }

        @Override
        public MediaItemSummary getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView == null) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding((int) (16 * density), (int) (10 * density), (int) (16 * density), (int) (10 * density));

                TextView icon = new TextView(activity);
                icon.setTag("icon");
                icon.setTextSize(18);
                icon.setPadding(0, 0, (int) (12 * density), 0);
                row.addView(icon);

                LinearLayout col = new LinearLayout(activity);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView title = new TextView(activity);
                title.setTag("title");
                title.setTextSize(13);
                title.setTextColor(TEXT_WHITE);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(title);

                TextView subtitle = new TextView(activity);
                subtitle.setTag("subtitle");
                subtitle.setTextSize(11);
                subtitle.setTextColor(TEXT_MUTED);
                col.addView(subtitle);

                row.addView(col);

                TextView badge = new TextView(activity);
                badge.setTag("badge");
                badge.setTextSize(11);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setTextColor(0xFF38BDF8);
                row.addView(badge);
            } else {
                row = (LinearLayout) convertView;
            }

            MediaItemSummary item = getItem(position);
            TextView icon = row.findViewWithTag("icon");
            TextView title = row.findViewWithTag("title");
            TextView subtitle = row.findViewWithTag("subtitle");
            TextView badge = row.findViewWithTag("badge");

            icon.setText(item.isVideo() ? "🎬" : "🖼️");
            title.setText(item.filename);

            String dateStr = item.timestamp > 0 ? DATE_FORMAT.format(new Date(item.timestamp)) : "";
            subtitle.setText(formatSize(item.sizeBytes) + (dateStr.isEmpty() ? "" : " • " + dateStr));

            if (item.quotaChargedBytes > 0) {
                badge.setText("⚡ " + formatSize(item.quotaChargedBytes));
                badge.setTextColor(0xFF38BDF8);
            } else {
                badge.setText("FREE");
                badge.setTextColor(0xFF34D399);
            }

            row.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && !item.mediaKey.isEmpty()) {
                    cm.setPrimaryClip(ClipData.newPlainText("MediaKey", item.mediaKey));
                    Toast.makeText(activity, "Copied Media Key: " + item.mediaKey, Toast.LENGTH_SHORT).show();
                }
            });

            return row;
        }
    }
}
