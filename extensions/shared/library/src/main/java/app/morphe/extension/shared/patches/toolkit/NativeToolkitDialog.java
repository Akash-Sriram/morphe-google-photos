package app.morphe.extension.shared.patches.toolkit;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import java.util.Calendar;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.patches.PhenotypeFlagManager;
import app.morphe.extension.shared.patches.PhenotypeFlagManager.MaterialVectorDrawable;
import app.morphe.extension.shared.patches.PhenotypeFlagManager.Theme;

/**
 * 1:1 Complete Native Google Photos Toolkit Dialog.
 * Follows the authoritative 3-step workflow from google_photos_toolkit.user.js:
 *   Step 1: Choose Source (7 sources)
 *   Step 2: Filter Media (All 21 filters with Space inside, Similarity perceptual hash)
 *   Step 3: Choose Action (All 14 actions)
 * Follows Flag Manager's Material 3 Theme tokens (theme.surface, 28dp rounded modal, M3 colors).
 */
public class NativeToolkitDialog {

    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(4);
    private static final ConcurrentHashMap<String, Bitmap> THUMB_CACHE = new ConcurrentHashMap<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    private static StorageScanner.AccountInfo currentAccount = null;
    private static StorageScanner.FilterCriteria currentCriteria = new StorageScanner.FilterCriteria();
    private static List<StorageScanner.MediaItem> allQueriedItems = new ArrayList<>();
    private static List<StorageScanner.AlbumInfo> availableAlbums = new ArrayList<>();

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        activity.runOnUiThread(() -> {
            try {
                showDialogInternal(activity);
            } catch (Throwable t) {
                Logger.printException(() -> "Error showing NativeToolkitDialog", t);
                Toast.makeText(activity, "Error opening Toolkit: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void showDialogInternal(Activity activity) {
        float density = activity.getResources().getDisplayMetrics().density;
        Theme theme = Theme.get(activity);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createRoundedDrawable(theme.surface, 28 * density));
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 28 * density);
                }
            });
            root.setClipToOutline(true);
        }

        // State holder references
        final Runnable[] runScanRef = new Runnable[1];

        // ─────────────────────────────────────────────────────────────────────
        // Top Header Bar
        // ─────────────────────────────────────────────────────────────────────
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int tbPad = (int) (18 * density);
        topBar.setPadding(tbPad, (int) (14 * density), (int) (12 * density), (int) (10 * density));

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Google Photos Toolkit");
        tvTitle.setTextSize(17.5f);
        tvTitle.setTextColor(theme.primary);
        tvTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(tvTitle);

        if (currentAccount == null) {
            currentAccount = StorageScanner.getActiveAccount(activity);
        }
        final List<StorageScanner.AccountInfo> allAccs = StorageScanner.getAvailableAccounts(activity);

        TextView tvSub = new TextView(activity);
        String accLabel = currentAccount != null ? currentAccount.accountName : "No Account";
        tvSub.setText(accLabel + " • Tap to switch");
        tvSub.setTextSize(11.5f);
        tvSub.setTextColor(theme.primary);
        tvSub.setClickable(true);
        tvSub.setFocusable(true);
        tvSub.setOnClickListener(v -> {
            if (allAccs.size() <= 1) return;
            Dialog accDialog = new Dialog(activity);
            accDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout accBox = new LinearLayout(activity);
            accBox.setOrientation(LinearLayout.VERTICAL);
            accBox.setBackground(createRoundedDrawable(theme.surface, 20 * density));
            int p = (int) (18 * density);
            accBox.setPadding(p, p, p, (int) (12 * density));

            TextView accT = new TextView(activity);
            accT.setText("Select Account");
            accT.setTextSize(15f);
            accT.setTextColor(theme.textPrimary);
            accT.setTypeface(null, Typeface.BOLD);
            accT.setPadding(0, 0, 0, (int) (10 * density));
            accBox.addView(accT);

            for (StorageScanner.AccountInfo a : allAccs) {
                TextView row = new TextView(activity);
                row.setText(a.accountName + " (Account " + a.accountId + ")");
                row.setTextSize(13f);
                row.setTextColor(theme.textPrimary);
                row.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
                row.setBackground(createActionPillDrawable(theme.surfaceContainer, 10 * density));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = (int) (4 * density);
                row.setLayoutParams(lp);
                row.setOnClickListener(rv -> {
                    currentAccount = a;
                    tvSub.setText(a.accountName + " • Tap to switch");
                    accDialog.dismiss();
                    if (runScanRef[0] != null) runScanRef[0].run();
                });
                accBox.addView(row);
            }
            accDialog.setContentView(accBox);
            accDialog.show();
            Window w = accDialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                w.setLayout((int) (screenWidth * 0.85f), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        titleCol.addView(tvSub);
        topBar.addView(titleCol);

        View btnSync = createHeaderIconButton(activity, new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_SYNC, theme.textPrimary), (int) (40 * density), (int) (20 * density));
        View btnClose = createHeaderIconButton(activity, new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_CLOSE, theme.textPrimary), (int) (40 * density), (int) (20 * density));
        topBar.addView(btnSync);
        topBar.addView(btnClose);
        root.addView(topBar);

        // Divider
        View divTop = new View(activity);
        divTop.setBackgroundColor(theme.outline);
        divTop.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density)));
        root.addView(divTop);

        // ─────────────────────────────────────────────────────────────────────
        // Main Scrollable Workflow Container
        // ─────────────────────────────────────────────────────────────────────
        ScrollView mainScrollView = new ScrollView(activity);
        mainScrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mainScrollView.setPadding(tbPad, (int) (8 * density), tbPad, (int) (16 * density));
        mainScrollView.setClipToPadding(false);

        LinearLayout workflowLayout = new LinearLayout(activity);
        workflowLayout.setOrientation(LinearLayout.VERTICAL);
        workflowLayout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mainScrollView.addView(workflowLayout);
        root.addView(mainScrollView);

        TextView tvScanButtonLabel = new TextView(activity);
        TextView tvResultsStatus = new TextView(activity);
        LinearLayout resultsListContainer = new LinearLayout(activity);
        resultsListContainer.setOrientation(LinearLayout.VERTICAL);

        // ─────────────────────────────────────────────────────────────────────
        // STEP 1: CHOOSE SOURCE
        // ─────────────────────────────────────────────────────────────────────
        workflowLayout.addView(createSectionHeader(activity, 1, "Choose Source", density, theme));

        LinearLayout sourcePillsRow = new LinearLayout(activity);
        sourcePillsRow.setOrientation(LinearLayout.HORIZONTAL);
        sourcePillsRow.setPadding(0, (int) (4 * density), 0, (int) (10 * density));

        ScrollView sourceScroll = new ScrollView(activity);
        android.widget.HorizontalScrollView hSourceScroll = new android.widget.HorizontalScrollView(activity);
        hSourceScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout sourcePillsInner = new LinearLayout(activity);
        sourcePillsInner.setOrientation(LinearLayout.HORIZONTAL);

        StorageScanner.SourceType[] sources = StorageScanner.SourceType.values();
        List<TextView> sourceViews = new ArrayList<>();

        for (StorageScanner.SourceType src : sources) {
            TextView pill = new TextView(activity);
            pill.setText(src.displayName);
            pill.setTextSize(12f);
            pill.setTypeface(null, Typeface.BOLD);
            pill.setClickable(true);
            pill.setFocusable(true);
            int pH = (int) (12 * density);
            int pV = (int) (6.5f * density);
            pill.setPadding(pH, pV, pH, pV);
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pLp.rightMargin = (int) (6 * density);
            pill.setLayoutParams(pLp);

            boolean isSelected = (src == currentCriteria.source);
            pill.setTextColor(isSelected ? theme.onPrimaryContainer : theme.textSecondary);
            pill.setBackground(createRoundedDrawable(isSelected ? theme.primaryContainer : theme.surfaceContainer, 12 * density));

            sourceViews.add(pill);
            sourcePillsInner.addView(pill);
        }
        hSourceScroll.addView(sourcePillsInner);
        workflowLayout.addView(hSourceScroll);

        // Step 2 & 3 containers & maps
        Map<String, View>[] filterCardsRef = new Map[1];
        Map<String, TextView> actionButtonsMap = new HashMap<>();

        // Wire Source pill click listener
        for (int i = 0; i < sources.length; i++) {
            final StorageScanner.SourceType src = sources[i];
            TextView pill = sourceViews.get(i);
            pill.setOnClickListener(v -> {
                currentCriteria.source = src;
                for (int j = 0; j < sources.length; j++) {
                    boolean sel = (sources[j] == currentCriteria.source);
                    sourceViews.get(j).setTextColor(sel ? theme.onPrimaryContainer : theme.textSecondary);
                    sourceViews.get(j).setBackground(createRoundedDrawable(sel ? theme.primaryContainer : theme.surfaceContainer, 12 * density));
                }
                updateFilterVisibility(src, filterCardsRef[0]);
                updateActionStates(src, currentCriteria, actionButtonsMap);
                if (runScanRef[0] != null) runScanRef[0].run();
            });
        }

        // ─────────────────────────────────────────────────────────────────────
        // STEP 2: CONFIGURE FILTERS (All 21 Filters from user.js)
        // ─────────────────────────────────────────────────────────────────────
        workflowLayout.addView(createSectionHeader(activity, 2, "Configure Filters (21)", density, theme));

        LinearLayout filtersAccordionContainer = new LinearLayout(activity);
        filtersAccordionContainer.setOrientation(LinearLayout.VERTICAL);
        workflowLayout.addView(filtersAccordionContainer);

        filterCardsRef[0] = build21FiltersAccordion(activity, filtersAccordionContainer, density, theme, runScanRef);
        updateFilterVisibility(currentCriteria.source, filterCardsRef[0]);

        // Scan / Filter Trigger Buttons
        LinearLayout filterActionRow = new LinearLayout(activity);
        filterActionRow.setOrientation(LinearLayout.HORIZONTAL);
        filterActionRow.setPadding(0, (int) (10 * density), 0, (int) (14 * density));

        TextView btnResetFilters = new TextView(activity);
        btnResetFilters.setText("Reset Filters");
        btnResetFilters.setTextSize(13f);
        btnResetFilters.setTypeface(null, Typeface.BOLD);
        btnResetFilters.setTextColor(theme.primary);
        btnResetFilters.setGravity(Gravity.CENTER);
        btnResetFilters.setPadding((int) (14 * density), (int) (9 * density), (int) (14 * density), (int) (9 * density));
        btnResetFilters.setBackground(createActionPillDrawable(theme.surfaceContainer, 16 * density));
        btnResetFilters.setOnClickListener(v -> {
            StorageScanner.SourceType savedSource = currentCriteria.source;
            currentCriteria = new StorageScanner.FilterCriteria();
            currentCriteria.source = savedSource;
            filterCardsRef[0] = build21FiltersAccordion(activity, filtersAccordionContainer, density, theme, runScanRef);
            updateFilterVisibility(currentCriteria.source, filterCardsRef[0]);
            updateActionStates(currentCriteria.source, currentCriteria, actionButtonsMap);
            if (runScanRef[0] != null) runScanRef[0].run();
            Toast.makeText(activity, "Reset all filters to default", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams rfLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rfLp.rightMargin = (int) (8 * density);
        btnResetFilters.setLayoutParams(rfLp);
        filterActionRow.addView(btnResetFilters);

        LinearLayout btnApplyScan = new LinearLayout(activity);
        btnApplyScan.setOrientation(LinearLayout.HORIZONTAL);
        btnApplyScan.setGravity(Gravity.CENTER);
        btnApplyScan.setClickable(true);
        btnApplyScan.setFocusable(true);
        btnApplyScan.setBackground(createActionPillDrawable(theme.primary, 16 * density));
        btnApplyScan.setPadding((int) (16 * density), (int) (9 * density), (int) (16 * density), (int) (9 * density));
        btnApplyScan.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));

        tvScanButtonLabel.setText("Apply & Scan");
        tvScanButtonLabel.setTextSize(13.5f);
        tvScanButtonLabel.setTypeface(null, Typeface.BOLD);
        tvScanButtonLabel.setTextColor(theme.onPrimary);
        btnApplyScan.addView(tvScanButtonLabel);

        btnApplyScan.setOnClickListener(v -> {
            updateActionStates(currentCriteria.source, currentCriteria, actionButtonsMap);
            if (runScanRef[0] != null) runScanRef[0].run();
        });
        filterActionRow.addView(btnApplyScan);
        workflowLayout.addView(filterActionRow);

        // ─────────────────────────────────────────────────────────────────────
        // STEP 3: CHOOSE ACTION (All 14 Actions from user.js)
        // ─────────────────────────────────────────────────────────────────────
        workflowLayout.addView(createSectionHeader(activity, 3, "Choose Action (14)", density, theme));

        LinearLayout actionGrid = buildActionButtonsGrid(activity, density, theme, () -> allQueriedItems, runScanRef, actionButtonsMap);
        updateActionStates(currentCriteria.source, currentCriteria, actionButtonsMap);
        workflowLayout.addView(actionGrid);

        // ─────────────────────────────────────────────────────────────────────
        // RESULTS & MATCHED MEDIA LIST
        // ─────────────────────────────────────────────────────────────────────
        LinearLayout resultsHeader = new LinearLayout(activity);
        resultsHeader.setOrientation(LinearLayout.VERTICAL);
        resultsHeader.setPadding(0, (int) (16 * density), 0, (int) (6 * density));

        TextView tvResTitle = new TextView(activity);
        tvResTitle.setText("Matched Media Results");
        tvResTitle.setTextSize(14f);
        tvResTitle.setTypeface(null, Typeface.BOLD);
        tvResTitle.setTextColor(theme.textPrimary);
        resultsHeader.addView(tvResTitle);

        tvResultsStatus.setText("Scanning items...");
        tvResultsStatus.setTextSize(11.5f);
        tvResultsStatus.setTextColor(theme.textSecondary);
        resultsHeader.addView(tvResultsStatus);
        workflowLayout.addView(resultsHeader);

        workflowLayout.addView(resultsListContainer);

        // ─────────────────────────────────────────────────────────────────────
        // Query Execution Engine
        // ─────────────────────────────────────────────────────────────────────
        runScanRef[0] = () -> {
            tvResultsStatus.setText("Scanning media matching filters...");
            tvScanButtonLabel.setText("Scanning...");
            THREAD_POOL.execute(() -> {
                try {
                    if (currentAccount == null) {
                        currentAccount = StorageScanner.getActiveAccount(activity);
                    }
                    if (currentAccount != null) {
                        availableAlbums = StorageScanner.getAvailableAlbums(currentAccount);
                    }

                    List<StorageScanner.MediaItem> items = StorageScanner.queryMedia(currentAccount, currentCriteria);
                    allQueriedItems.clear();
                    allQueriedItems.addAll(items);

                    // If Similarity filter active, compute hashes & cluster
                    if (currentCriteria.similarityThreshold > 0 && !items.isEmpty()) {
                        int hSize = currentCriteria.imageHeight > 0 ? currentCriteria.imageHeight : 16;
                        int maxCandidates = Math.min(items.size(), 100);
                        List<StorageScanner.MediaItem> candidates = new ArrayList<>(items.subList(0, maxCandidates));

                        MAIN_HANDLER.post(() -> tvResultsStatus.setText("Clustering visual similarity (checking top " + maxCandidates + " items)..."));

                        for (StorageScanner.MediaItem it : candidates) {
                            Bitmap bm = THUMB_CACHE.get(it.remoteUrl != null ? it.remoteUrl : "");
                            if (bm == null && it.remoteUrl != null && !it.remoteUrl.isEmpty()) {
                                bm = fetchThumbSync(it.remoteUrl);
                                if (bm != null) {
                                    THUMB_CACHE.put(it.remoteUrl, bm);
                                }
                            }
                            if (bm != null) {
                                it.perceptualHash = StorageScanner.calculateAverageHash(bm, hSize);
                            }
                        }
                        List<List<StorageScanner.MediaItem>> clusters = StorageScanner.clusterSimilarItems(candidates, currentCriteria.similarityThreshold, hSize);
                        allQueriedItems.clear();
                        if (!clusters.isEmpty()) {
                            List<StorageScanner.MediaItem> clusteredItems = new ArrayList<>();
                            for (List<StorageScanner.MediaItem> cl : clusters) clusteredItems.addAll(cl);
                            allQueriedItems.addAll(clusteredItems);
                        }
                    }

                    MAIN_HANDLER.post(() -> {
                        tvScanButtonLabel.setText("Scan & Filter (" + allQueriedItems.size() + ")");
                        int consuming = 0;
                        long totalQuotaBytes = 0;
                        for (StorageScanner.MediaItem it : allQueriedItems) {
                            if (it.quotaBytes > 0) {
                                consuming++;
                                totalQuotaBytes += it.quotaBytes;
                            }
                        }
                        tvResultsStatus.setText(allQueriedItems.size() + " matched • " + consuming + " consuming (" + StorageScanner.formatBytes(totalQuotaBytes) + ")");
                        renderResultsList(activity, resultsListContainer, allQueriedItems, density, theme);
                    });
                } catch (Throwable t) {
                    Logger.printException(() -> "Error in runScan", t);
                    MAIN_HANDLER.post(() -> {
                        tvScanButtonLabel.setText("Scan & Filter");
                        tvResultsStatus.setText("Scan error: " + t.getMessage());
                    });
                }
            });
        };

        btnSync.setOnClickListener(v -> {
            runScanRef[0].run();
            Toast.makeText(activity, "Refreshing query...", Toast.LENGTH_SHORT).show();
        });
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Window size - popup-friendly (94% width, 88% height)
        dialog.setContentView(root);
        dialog.show();

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

        // Run initial scan
        runScanRef[0].run();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Section Header Builder (Step Badges)
    // ─────────────────────────────────────────────────────────────────────────
    private static LinearLayout createSectionHeader(Activity activity, int stepNum, String title, float density, Theme theme) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (12 * density), 0, (int) (8 * density));

        TextView badge = new TextView(activity);
        badge.setText(String.valueOf(stepNum));
        badge.setTextSize(11f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextColor(theme.onPrimary);
        badge.setGravity(Gravity.CENTER);
        int bSize = (int) (20 * density);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(bSize, bSize);
        bLp.rightMargin = (int) (8 * density);
        badge.setLayoutParams(bLp);
        badge.setBackground(createRoundedDrawable(theme.primary, 10 * density));
        row.addView(badge);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(theme.textPrimary);
        row.addView(tvTitle);

        return row;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 2: All 21 Filters Accordion Builder (Direct from user.js)
    // ─────────────────────────────────────────────────────────────────────────
    private static Map<String, View> build21FiltersAccordion(Activity activity, LinearLayout container, float density, Theme theme, Runnable[] runScanRef) {
        container.removeAllViews();
        Set<String> expandedSet = new HashSet<>();
        Map<String, View> cards = new HashMap<>();

        // 1. Select Albums
        cards.put("includeAlbums", addFilterCard(activity, container, density, theme, expandedSet, "Select Albums",
                "Target specific albums (Albums source)",
                () -> currentCriteria.albumsInclude.isEmpty() ? null : currentCriteria.albumsInclude.size() + " selected",
                (content, updateBadge) -> {
                    if (availableAlbums.isEmpty()) {
                        TextView tvEmpty = new TextView(activity);
                        tvEmpty.setText("No albums loaded. Tap Refresh top-right.");
                        tvEmpty.setTextColor(theme.textSecondary);
                        content.addView(tvEmpty);
                    } else {
                        for (StorageScanner.AlbumInfo alb : availableAlbums) {
                            CheckBox cb = new CheckBox(activity);
                            cb.setText(alb.title + " (" + alb.itemCount + ")");
                            cb.setTextColor(theme.textPrimary);
                            cb.setChecked(currentCriteria.albumsInclude.contains(alb.mediaKey));
                            cb.setOnCheckedChangeListener((b, c) -> {
                                if (c) currentCriteria.albumsInclude.add(alb.mediaKey);
                                else currentCriteria.albumsInclude.remove(alb.mediaKey);
                                updateBadge.run();
                                if (runScanRef != null && runScanRef[0] != null) {
                                    runScanRef[0].run();
                                }
                            });
                            content.addView(cb);
                        }
                    }
                }));

        // 2. Search Query
        cards.put("search", addFilterCard(activity, container, density, theme, expandedSet, "Search Query",
                "Query search results",
                () -> (currentCriteria.searchQuery != null && !currentCriteria.searchQuery.trim().isEmpty()) ? "\"" + currentCriteria.searchQuery.trim() + "\"" : null,
                (content, updateBadge) -> {
                    EditText et = createFilterInput(activity, density, theme, "Enter search query...");
                    et.setText(currentCriteria.searchQuery != null ? currentCriteria.searchQuery : "");
                    et.addTextChangedListener(new SimpleWatcher(s -> {
                        currentCriteria.searchQuery = s;
                        updateBadge.run();
                    }));
                    content.addView(et);
                }));

        // 3. Exclude Albums
        cards.put("excludeAlbums", addFilterCard(activity, container, density, theme, expandedSet, "Exclude Albums",
                "Omit media present in selected albums",
                () -> currentCriteria.albumsExclude.isEmpty() ? null : currentCriteria.albumsExclude.size() + " excluded",
                (content, updateBadge) -> {
                    for (StorageScanner.AlbumInfo alb : availableAlbums) {
                        CheckBox cb = new CheckBox(activity);
                        cb.setText("Exclude: " + alb.title);
                        cb.setTextColor(theme.textPrimary);
                        cb.setChecked(currentCriteria.albumsExclude.contains(alb.mediaKey));
                        cb.setOnCheckedChangeListener((b, c) -> {
                            if (c) currentCriteria.albumsExclude.add(alb.mediaKey);
                            else currentCriteria.albumsExclude.remove(alb.mediaKey);
                            updateBadge.run();
                            if (runScanRef != null && runScanRef[0] != null) {
                                runScanRef[0].run();
                            }
                        });
                        content.addView(cb);
                    }
                }));

        // 4. Date Interval
        cards.put("dateInterval", addFilterCard(activity, container, density, theme, expandedSet, "Date Interval",
                "Filter by capture or upload timestamps",
                () -> {
                    SimpleDateFormat badgeFmt = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
                    if (currentCriteria.lowerBoundaryDate > 0 && currentCriteria.higherBoundaryDate > 0) {
                        return badgeFmt.format(new Date(currentCriteria.lowerBoundaryDate)) + " - " + badgeFmt.format(new Date(currentCriteria.higherBoundaryDate));
                    } else if (currentCriteria.lowerBoundaryDate > 0) {
                        return "From " + badgeFmt.format(new Date(currentCriteria.lowerBoundaryDate));
                    } else if (currentCriteria.higherBoundaryDate > 0) {
                        return "To " + badgeFmt.format(new Date(currentCriteria.higherBoundaryDate));
                    }
                    return null;
                },
                (content, updateBadge) -> {
                    LinearLayout rowFrom = createDateTimePickerRow(activity, density, theme, "From Date & Time:", currentCriteria.lowerBoundaryDate, epochMs -> {
                        currentCriteria.lowerBoundaryDate = epochMs;
                        updateBadge.run();
                    });
                    content.addView(rowFrom);

                    LinearLayout rowTo = createDateTimePickerRow(activity, density, theme, "To Date & Time:", currentCriteria.higherBoundaryDate, epochMs -> {
                        currentCriteria.higherBoundaryDate = epochMs;
                        updateBadge.run();
                    });
                    content.addView(rowTo);

                    RadioGroup rgInt = createRadioGroup(activity, new String[]{"Include Range", "Exclude Range"},
                            "include".equals(currentCriteria.intervalType) ? 0 : 1, theme, idx -> {
                                currentCriteria.intervalType = idx == 0 ? "include" : "exclude";
                                updateBadge.run();
                            });
                    content.addView(rgInt);

                    RadioGroup rgType = createRadioGroup(activity, new String[]{"Date Taken", "Date Uploaded"},
                            "taken".equals(currentCriteria.dateType) ? 0 : 1, theme, idx -> {
                                currentCriteria.dateType = idx == 0 ? "taken" : "uploaded";
                                updateBadge.run();
                            });
                    content.addView(rgType);
                }));

        // 5. Filename Regex
        cards.put("filename", addFilterCard(activity, container, density, theme, expandedSet, "Filename",
                "Regex matching on file name",
                () -> (currentCriteria.fileNameRegex != null && !currentCriteria.fileNameRegex.trim().isEmpty()) ? currentCriteria.fileNameRegex.trim() : null,
                (content, updateBadge) -> {
                    EditText et = createFilterInput(activity, density, theme, "Regex (e.g. ^IMG_.*\\.jpg$)");
                    et.setText(currentCriteria.fileNameRegex != null ? currentCriteria.fileNameRegex : "");
                    et.addTextChangedListener(new SimpleWatcher(s -> {
                        currentCriteria.fileNameRegex = s;
                        updateBadge.run();
                    }));
                    content.addView(et);

                    RadioGroup rg = createRadioGroup(activity, new String[]{"Include Matches", "Exclude Matches"},
                            "include".equals(currentCriteria.fileNameMatchType) ? 0 : 1, theme, idx -> {
                                currentCriteria.fileNameMatchType = idx == 0 ? "include" : "exclude";
                                updateBadge.run();
                            });
                    content.addView(rg);
                }));

        // 6. Description Regex
        cards.put("description", addFilterCard(activity, container, density, theme, expandedSet, "Description",
                "Regex matching on caption/EXIF",
                () -> (currentCriteria.descriptionRegex != null && !currentCriteria.descriptionRegex.trim().isEmpty()) ? currentCriteria.descriptionRegex.trim() : null,
                (content, updateBadge) -> {
                    EditText et = createFilterInput(activity, density, theme, "Regex (e.g. vacation)");
                    et.setText(currentCriteria.descriptionRegex != null ? currentCriteria.descriptionRegex : "");
                    et.addTextChangedListener(new SimpleWatcher(s -> {
                        currentCriteria.descriptionRegex = s;
                        updateBadge.run();
                    }));
                    content.addView(et);

                    RadioGroup rg = createRadioGroup(activity, new String[]{"Include Matches", "Exclude Matches"},
                            "include".equals(currentCriteria.descriptionMatchType) ? 0 : 1, theme, idx -> {
                                currentCriteria.descriptionMatchType = idx == 0 ? "include" : "exclude";
                                updateBadge.run();
                            });
                    content.addView(rg);
                }));

        // 7. Space (Inside filters accordion!)
        cards.put("space", addFilterCard(activity, container, density, theme, expandedSet, "Space",
                "Filter storage quota impact",
                () -> currentCriteria.space == StorageScanner.SpaceFilter.CONSUMING ? "Consuming Quota" :
                        currentCriteria.space == StorageScanner.SpaceFilter.NON_CONSUMING ? "Non-Consuming" : null,
                (content, updateBadge) -> {
                    int sel = currentCriteria.space == StorageScanner.SpaceFilter.CONSUMING ? 1 :
                            currentCriteria.space == StorageScanner.SpaceFilter.NON_CONSUMING ? 2 : 0;
                    RadioGroup rg = createRadioGroup(activity, new String[]{"Any", "Consuming Quota", "Non-Consuming (Free / 0 B)"},
                            sel, theme, idx -> {
                                currentCriteria.space = idx == 1 ? StorageScanner.SpaceFilter.CONSUMING :
                                        idx == 2 ? StorageScanner.SpaceFilter.NON_CONSUMING : StorageScanner.SpaceFilter.ANY;
                                updateBadge.run();
                            });
                    content.addView(rg);
                }));

        // 8. Similarity
        cards.put("similarity", addFilterCard(activity, container, density, theme, expandedSet, "Similarity",
                "Group visually similar images via perceptual hashing",
                () -> currentCriteria.similarityThreshold > 0 ? "Threshold " + currentCriteria.similarityThreshold : null,
                (content, updateBadge) -> {
                    TextView tvNote = new TextView(activity);
                    tvNote.setText("Calculates 64-bit dHash/aHash distances between thumbnails to cluster duplicates.\nTip: Filter by Size, Date or Album first to quickly cluster candidates.");
                    tvNote.setTextSize(11.5f);
                    tvNote.setTextColor(theme.textSecondary);
                    tvNote.setPadding(0, 0, 0, (int) (6 * density));
                    content.addView(tvNote);

                    EditText etThresh = createFilterInput(activity, density, theme, "Threshold (0.01 - 1.0, e.g. 0.90)");
                    if (currentCriteria.similarityThreshold > 0) etThresh.setText(String.valueOf(currentCriteria.similarityThreshold));
                    etThresh.addTextChangedListener(new SimpleWatcher(s -> {
                        try { currentCriteria.similarityThreshold = Float.parseFloat(s); } catch (Exception e) { currentCriteria.similarityThreshold = 0; }
                        updateBadge.run();
                    }));
                    content.addView(etThresh);

                    EditText etH = createFilterInput(activity, density, theme, "Image Hash Size (default: 16)");
                    etH.setText(String.valueOf(currentCriteria.imageHeight));
                    etH.addTextChangedListener(new SimpleWatcher(s -> {
                        try { currentCriteria.imageHeight = Integer.parseInt(s); } catch (Exception e) { currentCriteria.imageHeight = 16; }
                        updateBadge.run();
                    }));
                    content.addView(etH);
                }));

        // 9. Size
        cards.put("size", addFilterCard(activity, container, density, theme, expandedSet, "Size",
                "Filter by file size (bytes or e.g. 5MB, 1.5GB)",
                () -> {
                    if (currentCriteria.lowerBoundarySize > 0 && currentCriteria.higherBoundarySize > 0) {
                        return StorageScanner.formatBytes(currentCriteria.lowerBoundarySize) + " - " + StorageScanner.formatBytes(currentCriteria.higherBoundarySize);
                    } else if (currentCriteria.lowerBoundarySize > 0) {
                        return "> " + StorageScanner.formatBytes(currentCriteria.lowerBoundarySize);
                    } else if (currentCriteria.higherBoundarySize > 0) {
                        return "< " + StorageScanner.formatBytes(currentCriteria.higherBoundarySize);
                    }
                    return null;
                },
                (content, updateBadge) -> {
                    EditText etMin = createFilterInput(activity, density, theme, "More Than (e.g. 5MB, 5000000)");
                    if (currentCriteria.lowerBoundarySize > 0) etMin.setText(StorageScanner.formatBytes(currentCriteria.lowerBoundarySize));
                    etMin.addTextChangedListener(new SimpleWatcher(s -> {
                        currentCriteria.lowerBoundarySize = parseBytesInput(s);
                        updateBadge.run();
                    }));
                    content.addView(etMin);

                    EditText etMax = createFilterInput(activity, density, theme, "Less Than (e.g. 50MB, 50000000)");
                    if (currentCriteria.higherBoundarySize > 0) etMax.setText(StorageScanner.formatBytes(currentCriteria.higherBoundarySize));
                    etMax.addTextChangedListener(new SimpleWatcher(s -> {
                        currentCriteria.higherBoundarySize = parseBytesInput(s);
                        updateBadge.run();
                    }));
                    content.addView(etMax);
                }));

        // 10. Resolution
        cards.put("resolution", addFilterCard(activity, container, density, theme, expandedSet, "Resolution",
                "Width and height in pixels",
                () -> (currentCriteria.minWidth > 0 || currentCriteria.maxWidth > 0 || currentCriteria.minHeight > 0 || currentCriteria.maxHeight > 0) ? "Resolution set" : null,
                (content, updateBadge) -> {
                    EditText etMinW = createFilterInput(activity, density, theme, "Min Width (px)");
                    if (currentCriteria.minWidth > 0) etMinW.setText(String.valueOf(currentCriteria.minWidth));
                    etMinW.addTextChangedListener(new SimpleWatcher(s -> {
                        try { currentCriteria.minWidth = Integer.parseInt(s); } catch (Exception e) { currentCriteria.minWidth = 0; }
                        updateBadge.run();
                    }));
                    content.addView(etMinW);

                    EditText etMaxW = createFilterInput(activity, density, theme, "Max Width (px)");
                    if (currentCriteria.maxWidth > 0) etMaxW.setText(String.valueOf(currentCriteria.maxWidth));
                    etMaxW.addTextChangedListener(new SimpleWatcher(s -> {
                        try { currentCriteria.maxWidth = Integer.parseInt(s); } catch (Exception e) { currentCriteria.maxWidth = 0; }
                        updateBadge.run();
                    }));
                    content.addView(etMaxW);

                    EditText etMinH = createFilterInput(activity, density, theme, "Min Height (px)");
                    if (currentCriteria.minHeight > 0) etMinH.setText(String.valueOf(currentCriteria.minHeight));
                    etMinH.addTextChangedListener(new SimpleWatcher(s -> {
                        try { currentCriteria.minHeight = Integer.parseInt(s); } catch (Exception e) { currentCriteria.minHeight = 0; }
                        updateBadge.run();
                    }));
                    content.addView(etMinH);

                    EditText etMaxH = createFilterInput(activity, density, theme, "Max Height (px)");
                    if (currentCriteria.maxHeight > 0) etMaxH.setText(String.valueOf(currentCriteria.maxHeight));
                    etMaxH.addTextChangedListener(new SimpleWatcher(s -> {
                        try { currentCriteria.maxHeight = Integer.parseInt(s); } catch (Exception e) { currentCriteria.maxHeight = 0; }
                        updateBadge.run();
                    }));
                    content.addView(etMaxH);
                }));

        // 11. Duration
        cards.put("duration", addFilterCard(activity, container, density, theme, expandedSet, "Duration",
                "Video length in seconds",
                () -> (currentCriteria.minDuration > 0 || currentCriteria.maxDuration > 0) ? "Duration set" : null,
                (content, updateBadge) -> {
                    EditText etMinD = createFilterInput(activity, density, theme, "Min Duration (s)");
                    if (currentCriteria.minDuration > 0) etMinD.setText(String.valueOf(currentCriteria.minDuration));
                    etMinD.addTextChangedListener(new SimpleWatcher(s -> {
                        try { currentCriteria.minDuration = Float.parseFloat(s); } catch (Exception e) { currentCriteria.minDuration = 0; }
                        updateBadge.run();
                    }));
                    content.addView(etMinD);

                    EditText etMaxD = createFilterInput(activity, density, theme, "Max Duration (s)");
                    if (currentCriteria.maxDuration > 0) etMaxD.setText(String.valueOf(currentCriteria.maxDuration));
                    etMaxD.addTextChangedListener(new SimpleWatcher(s -> {
                        try { currentCriteria.maxDuration = Float.parseFloat(s); } catch (Exception e) { currentCriteria.maxDuration = 0; }
                        updateBadge.run();
                    }));
                    content.addView(etMaxD);
                }));

        // 12. Quality
        cards.put("quality", addFilterCard(activity, container, density, theme, expandedSet, "Quality",
                "Storage saver vs original quality",
                () -> "original".equals(currentCriteria.quality) ? "Original" :
                        "storage-saver".equals(currentCriteria.quality) ? "Storage Saver" : null,
                (content, updateBadge) -> {
                    int sel = "original".equals(currentCriteria.quality) ? 1 : "storage-saver".equals(currentCriteria.quality) ? 2 : 0;
                    RadioGroup rg = createRadioGroup(activity, new String[]{"Any", "Original", "Storage Saver"},
                            sel, theme, idx -> {
                                currentCriteria.quality = idx == 1 ? "original" : idx == 2 ? "storage-saver" : null;
                                updateBadge.run();
                            });
                    content.addView(rg);
                }));

        // 13. Type
        cards.put("type", addFilterCard(activity, container, density, theme, expandedSet, "Type",
                "Image, video, or live photo",
                () -> "image".equals(currentCriteria.mediaType) ? "Images" :
                        "video".equals(currentCriteria.mediaType) ? "Videos" :
                                "live".equals(currentCriteria.mediaType) ? "Live Photos" : null,
                (content, updateBadge) -> {
                    int sel = "image".equals(currentCriteria.mediaType) ? 1 : "video".equals(currentCriteria.mediaType) ? 2 : "live".equals(currentCriteria.mediaType) ? 3 : 0;
                    RadioGroup rg = createRadioGroup(activity, new String[]{"Any", "Image", "Video", "Live Photo"},
                            sel, theme, idx -> {
                                currentCriteria.mediaType = idx == 1 ? "image" : idx == 2 ? "video" : idx == 3 ? "live" : null;
                                updateBadge.run();
                            });
                    content.addView(rg);
                }));

        // 14. Upload Status
        cards.put("uploadStatus", addFilterCard(activity, container, density, theme, expandedSet, "Upload Status",
                "Full vs partial backup",
                () -> "full".equals(currentCriteria.uploadStatus) ? "Full" :
                        "partial".equals(currentCriteria.uploadStatus) ? "Partial" : null,
                (content, updateBadge) -> {
                    int sel = "full".equals(currentCriteria.uploadStatus) ? 1 : "partial".equals(currentCriteria.uploadStatus) ? 2 : 0;
                    RadioGroup rg = createRadioGroup(activity, new String[]{"Any", "Full", "Partial"},
                            sel, theme, idx -> {
                                currentCriteria.uploadStatus = idx == 1 ? "full" : idx == 2 ? "partial" : null;
                                updateBadge.run();
                            });
                    content.addView(rg);
                }));

        // 15. Archived
        cards.put("archive", addFilterCard(activity, container, density, theme, expandedSet, "Archived",
                "Archive status",
                () -> Boolean.TRUE.equals(currentCriteria.archived) ? "Archived" :
                        Boolean.FALSE.equals(currentCriteria.archived) ? "Unarchived" : null,
                (content, updateBadge) -> {
                    int sel = Boolean.TRUE.equals(currentCriteria.archived) ? 1 : Boolean.FALSE.equals(currentCriteria.archived) ? 2 : 0;
                    RadioGroup rg = createRadioGroup(activity, new String[]{"Any", "Yes", "No"},
                            sel, theme, idx -> {
                                currentCriteria.archived = idx == 1 ? Boolean.TRUE : idx == 2 ? Boolean.FALSE : null;
                                updateBadge.run();
                            });
                    content.addView(rg);
                }));

        // 16. Ownership
        cards.put("owned", addFilterCard(activity, container, density, theme, expandedSet, "Ownership",
                "Owned vs not owned",
                () -> Boolean.TRUE.equals(currentCriteria.owned) ? "Owned" :
                        Boolean.FALSE.equals(currentCriteria.owned) ? "Not Owned" : null,
                (content, updateBadge) -> {
                    int sel = Boolean.TRUE.equals(currentCriteria.owned) ? 1 : Boolean.FALSE.equals(currentCriteria.owned) ? 2 : 0;
                    RadioGroup rg = createRadioGroup(activity, new String[]{"Any", "Owned", "Not Owned"},
                            sel, theme, idx -> {
                                currentCriteria.owned = idx == 1 ? Boolean.TRUE : idx == 2 ? Boolean.FALSE : null;
                                updateBadge.run();
                            });
                    content.addView(rg);
                }));

        // 17. Location & Bounding Box
        cards.put("location", addFilterCard(activity, container, density, theme, expandedSet, "Location",
                "GPS coordinates & geographic area",
                () -> Boolean.TRUE.equals(currentCriteria.hasLocation) ? "Has Location" :
                        Boolean.FALSE.equals(currentCriteria.hasLocation) ? "No Location" :
                                (currentCriteria.boundSouth != null || currentCriteria.boundNorth != null) ? "Geo Bounded" : null,
                (content, updateBadge) -> {
                    int sel = Boolean.TRUE.equals(currentCriteria.hasLocation) ? 1 : Boolean.FALSE.equals(currentCriteria.hasLocation) ? 2 : 0;
                    RadioGroup rg = createRadioGroup(activity, new String[]{"Any", "Has Location", "No Location"},
                            sel, theme, idx -> {
                                currentCriteria.hasLocation = idx == 1 ? Boolean.TRUE : idx == 2 ? Boolean.FALSE : null;
                                updateBadge.run();
                            });
                    content.addView(rg);

                    EditText etS = createFilterInput(activity, density, theme, "South Latitude (-90 to 90)");
                    etS.addTextChangedListener(new SimpleWatcher(s -> {
                        try { currentCriteria.boundSouth = Double.parseDouble(s); } catch (Exception e) { currentCriteria.boundSouth = null; }
                        updateBadge.run();
                    }));
                    content.addView(etS);

                    EditText etN = createFilterInput(activity, density, theme, "North Latitude (-90 to 90)");
                    etN.addTextChangedListener(new SimpleWatcher(s -> {
                        try { currentCriteria.boundNorth = Double.parseDouble(s); } catch (Exception e) { currentCriteria.boundNorth = null; }
                        updateBadge.run();
                    }));
                    content.addView(etN);
                }));

        // 18. Favorite
        cards.put("favorite", addFilterCard(activity, container, density, theme, expandedSet, "Favorite",
                "Favorite / starred status",
                () -> Boolean.TRUE.equals(currentCriteria.favorite) ? "Favorited" :
                        Boolean.FALSE.equals(currentCriteria.favorite) ? "Not Favorited" : null,
                (content, updateBadge) -> {
                    int sel = Boolean.TRUE.equals(currentCriteria.favorite) ? 1 : Boolean.FALSE.equals(currentCriteria.favorite) ? 2 : 0;
                    RadioGroup rg = createRadioGroup(activity, new String[]{"Any", "Yes", "No"},
                            sel, theme, idx -> {
                                currentCriteria.favorite = idx == 1 ? Boolean.TRUE : idx == 2 ? Boolean.FALSE : null;
                                updateBadge.run();
                            });
                    content.addView(rg);
                }));

        // 19. Exclude Shared Links
        cards.put("excludeShared", addFilterCard(activity, container, density, theme, expandedSet, "Exclude Shared",
                "Omit shared links",
                () -> currentCriteria.excludeShared ? "Active" : null,
                (content, updateBadge) -> {
                    CheckBox cbShared = new CheckBox(activity);
                    cbShared.setText("Exclude Shared Links");
                    cbShared.setTextColor(theme.textPrimary);
                    cbShared.setChecked(currentCriteria.excludeShared);
                    cbShared.setOnCheckedChangeListener((b, c) -> {
                        currentCriteria.excludeShared = c;
                        updateBadge.run();
                    });
                    content.addView(cbShared);
                }));

        // 20. Exclude Favorites
        cards.put("excludeFavorites", addFilterCard(activity, container, density, theme, expandedSet, "Exclude Favorites",
                "Omit favorited items",
                () -> currentCriteria.excludeFavorites ? "Active" : null,
                (content, updateBadge) -> {
                    CheckBox cbFav = new CheckBox(activity);
                    cbFav.setText("Exclude Favorites");
                    cbFav.setTextColor(theme.textPrimary);
                    cbFav.setChecked(currentCriteria.excludeFavorites);
                    cbFav.setOnCheckedChangeListener((b, c) -> {
                        currentCriteria.excludeFavorites = c;
                        updateBadge.run();
                    });
                    content.addView(cbFav);
                }));

        // 21. Sort by Size
        cards.put("sortBySize", addFilterCard(activity, container, density, theme, expandedSet, "Sorting",
                "Result sort order",
                () -> currentCriteria.sortBySize ? "Sort by size" : null,
                (content, updateBadge) -> {
                    CheckBox cbSort = new CheckBox(activity);
                    cbSort.setText("Sort by size");
                    cbSort.setTextColor(theme.textPrimary);
                    cbSort.setChecked(currentCriteria.sortBySize);
                    cbSort.setOnCheckedChangeListener((b, c) -> {
                        currentCriteria.sortBySize = c;
                        updateBadge.run();
                    });
                    content.addView(cbSort);
                }));

        return cards;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 3: Choose Action Grid Builder (All 14 Actions from user.js)
    // ─────────────────────────────────────────────────────────────────────────
    public interface ItemsProvider {
        List<StorageScanner.MediaItem> get();
    }

    private static LinearLayout buildActionButtonsGrid(Activity activity, float density, Theme theme,
                                                       ItemsProvider itemsProvider, Runnable[] refreshRunner,
                                                       Map<String, TextView> actionButtonsOut) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);

        String[][] actions = new String[][]{
                {"toExistingAlbum", "Add to Album"},
                {"toNewAlbum", "New Album"},
                {"removeFromAlbum", "Remove from Album"},
                {"toTrash", "Trash"},
                {"restoreTrash", "Restore"},
                {"toArchive", "Archive"},
                {"unArchive", "Un-Archive"},
                {"toFavorite", "Favorite"},
                {"unFavorite", "Un-Favorite"},
                {"lock", "Lock"},
                {"unLock", "Unlock"},
                {"copyDescFromOther", "Copy EXIF Desc"},
                {"setDateFromFilename", "Date from Name"},
                {"exportMetadata", "Export Metadata (CSV)"}
        };

        LinearLayout currentRow = null;
        for (int i = 0; i < actions.length; i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(activity);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                container.addView(currentRow);
            }

            final String actionId = actions[i][0];
            final String actionLabel = actions[i][1];

            TextView btn = new TextView(activity);
            btn.setText(actionLabel);
            btn.setTextSize(12.5f);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setTextColor(theme.textPrimary);
            btn.setGravity(Gravity.CENTER);
            btn.setClickable(true);
            btn.setFocusable(true);
            int padV = (int) (9 * density);
            btn.setPadding((int) (8 * density), padV, (int) (8 * density), padV);
            btn.setBackground(createActionPillDrawable(theme.surfaceContainer, 12 * density));

            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            bLp.bottomMargin = (int) (6 * density);
            if (i % 2 == 0) bLp.rightMargin = (int) (6 * density);
            btn.setLayoutParams(bLp);

            btn.setOnClickListener(v -> {
                if (btn.getAlpha() < 0.6f) {
                    // Action disabled for current source/context
                    String disabledHint = getActionDisabledHint(actionId, currentCriteria);
                    Toast.makeText(activity, disabledHint != null ? disabledHint : "Action not applicable for current source", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<StorageScanner.MediaItem> items = itemsProvider.get();
                if (items.isEmpty()) {
                    if ("toTrash".equals(actionId) && currentCriteria.source == StorageScanner.SourceType.ALBUMS && !currentCriteria.albumsInclude.isEmpty()) {
                        String param = TextUtils.join(",", currentCriteria.albumsInclude);
                        showActionConfirmDialog(activity, "Delete " + currentCriteria.albumsInclude.size() + " empty album(s)?", () -> {
                            executeDbActionAsync(activity, actionId, items, param, refreshRunner);
                        }, density, theme);
                        return;
                    }
                    Toast.makeText(activity, "No matching items to perform " + actionLabel, Toast.LENGTH_SHORT).show();
                    return;
                }

                if ("exportMetadata".equals(actionId)) {
                    exportCsvFile(activity, items);
                } else if ("toNewAlbum".equals(actionId)) {
                    showNewAlbumDialog(activity, items, refreshRunner, density, theme);
                } else if ("toExistingAlbum".equals(actionId)) {
                    showAlbumPickerDialog(activity, items, refreshRunner, density, theme);
                } else if ("removeFromAlbum".equals(actionId)) {
                    String param = !currentCriteria.albumsInclude.isEmpty() ? TextUtils.join(",", currentCriteria.albumsInclude) : null;
                    final String targetAlbumKeys = param;
                    showActionConfirmDialog(activity, "Remove " + items.size() + " items from album?", () -> {
                        executeDbActionAsync(activity, actionId, items, targetAlbumKeys, refreshRunner);
                    }, density, theme);
                } else if ("toTrash".equals(actionId)) {
                    String param = null;
                    String confirmMsg;
                    if (currentCriteria.source == StorageScanner.SourceType.ALBUMS && !currentCriteria.albumsInclude.isEmpty()) {
                        param = TextUtils.join(",", currentCriteria.albumsInclude);
                        confirmMsg = "Move " + items.size() + " items to Trash and delete " + currentCriteria.albumsInclude.size() + " album(s)?";
                    } else {
                        confirmMsg = "Move " + items.size() + " items to Trash?";
                    }
                    final String targetAlbumKeys = param;
                    showActionConfirmDialog(activity, confirmMsg, () -> {
                        executeDbActionAsync(activity, actionId, items, targetAlbumKeys, refreshRunner);
                    }, density, theme);
                } else if ("lock".equals(actionId)) {
                    showActionConfirmDialog(activity, "Lock " + items.size() + " items into Locked Folder?", () -> {
                        executeDbActionAsync(activity, actionId, items, null, refreshRunner);
                    }, density, theme);
                } else if ("copyDescFromOther".equals(actionId)) {
                    showActionConfirmDialog(activity, "Copy captions/descriptions for " + items.size() + " items?", () -> {
                        executeDbActionAsync(activity, actionId, items, null, refreshRunner);
                    }, density, theme);
                } else {
                    executeDbActionAsync(activity, actionId, items, null, refreshRunner);
                }
            });

            if (actionButtonsOut != null) actionButtonsOut.put(actionId, btn);
            if (currentRow != null) currentRow.addView(btn);
        }

        return container;
    }

    private static String getActionDisabledHint(String actionId, StorageScanner.FilterCriteria crit) {
        if ("removeFromAlbum".equals(actionId)) return "Source must be Albums to remove items from an album";
        if ("toTrash".equals(actionId)) return "Items are already in Trash";
        if ("restoreTrash".equals(actionId)) return "Source must be Trash to restore items";
        if ("lock".equals(actionId)) return "Items are already in Locked Folder";
        if ("unLock".equals(actionId)) return "Source must be Locked Folder to unlock items";
        if ("toArchive".equals(actionId)) return "Items are already archived";
        if ("unArchive".equals(actionId)) return "Items are not archived";
        if ("toFavorite".equals(actionId)) return "Items are already favorited";
        if ("unFavorite".equals(actionId)) return "Items are not favorited";
        if ("copyDescFromOther".equals(actionId)) return "Cannot copy description on trashed items";
        return null;
    }

    private static void updateActionStates(StorageScanner.SourceType src, StorageScanner.FilterCriteria crit, Map<String, TextView> actionButtons) {
        if (actionButtons == null || actionButtons.isEmpty()) return;

        boolean archivedOnly = Boolean.TRUE.equals(crit.archived);
        boolean archivedExcluded = Boolean.FALSE.equals(crit.archived);
        boolean favoritesOnly = Boolean.TRUE.equals(crit.favorite) || src == StorageScanner.SourceType.FAVORITES;
        boolean favoritesExcluded = Boolean.FALSE.equals(crit.favorite) || crit.excludeFavorites;

        setActionState(actionButtons.get("removeFromAlbum"), src == StorageScanner.SourceType.ALBUMS);
        setActionState(actionButtons.get("toTrash"), src != StorageScanner.SourceType.TRASH);
        setActionState(actionButtons.get("restoreTrash"), src == StorageScanner.SourceType.TRASH);
        setActionState(actionButtons.get("lock"), src != StorageScanner.SourceType.LOCKED);
        setActionState(actionButtons.get("unLock"), src == StorageScanner.SourceType.LOCKED);

        setActionState(actionButtons.get("toArchive"), !archivedOnly);
        setActionState(actionButtons.get("unArchive"), !archivedExcluded);

        setActionState(actionButtons.get("toFavorite"), !favoritesOnly && src != StorageScanner.SourceType.FAVORITES);
        setActionState(actionButtons.get("unFavorite"), !favoritesExcluded);

        setActionState(actionButtons.get("copyDescFromOther"), src != StorageScanner.SourceType.TRASH);
    }

    private static void setActionState(TextView btn, boolean enabled) {
        if (btn == null) return;
        btn.setAlpha(enabled ? 1.0f : 0.35f);
    }

    private static void showNewAlbumDialog(Activity activity, List<StorageScanner.MediaItem> items, Runnable[] refreshRunner, float density, Theme theme) {
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(createRoundedDrawable(theme.surface, 20 * density));
        int p = (int) (20 * density);
        box.setPadding(p, p, p, (int) (14 * density));

        TextView tv = new TextView(activity);
        tv.setText("Add " + items.size() + " items to New Album");
        tv.setTextSize(15f);
        tv.setTextColor(theme.textPrimary);
        tv.setTypeface(null, Typeface.BOLD);
        box.addView(tv);

        EditText et = createFilterInput(activity, density, theme, "Album title...");
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = (int) (12 * density);
        etLp.bottomMargin = (int) (12 * density);
        et.setLayoutParams(etLp);
        box.addView(et);

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);

        TextView btnCancel = new TextView(activity);
        btnCancel.setText("Cancel");
        btnCancel.setTextSize(13f);
        btnCancel.setTextColor(theme.primary);
        btnCancel.setPadding((int) (14 * density), (int) (8 * density), (int) (14 * density), (int) (8 * density));
        btnCancel.setBackground(createActionPillDrawable(theme.surfaceContainer, 14 * density));
        btnCancel.setOnClickListener(v -> d.dismiss());
        btnRow.addView(btnCancel);

        TextView btnCreate = new TextView(activity);
        btnCreate.setText("Create & Add");
        btnCreate.setTextSize(13f);
        btnCreate.setTypeface(null, Typeface.BOLD);
        btnCreate.setTextColor(theme.onPrimary);
        btnCreate.setPadding((int) (16 * density), (int) (8 * density), (int) (16 * density), (int) (8 * density));
        btnCreate.setBackground(createActionPillDrawable(theme.primary, 14 * density));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okLp.leftMargin = (int) (8 * density);
        btnCreate.setLayoutParams(okLp);
        btnCreate.setOnClickListener(v -> {
            String title = et.getText() != null ? et.getText().toString().trim() : "";
            if (title.isEmpty()) {
                Toast.makeText(activity, "Please enter an album title", Toast.LENGTH_SHORT).show();
                return;
            }
            d.dismiss();
            executeDbActionAsync(activity, "toNewAlbum", items, title, refreshRunner);
        });
        btnRow.addView(btnCreate);

        box.addView(btnRow);
        d.setContentView(box);
        d.show();

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (screenWidth * 0.90f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void showAlbumPickerDialog(Activity activity, List<StorageScanner.MediaItem> items, Runnable[] refreshRunner, float density, Theme theme) {
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(createRoundedDrawable(theme.surface, 20 * density));
        int p = (int) (16 * density);
        box.setPadding(p, p, p, (int) (12 * density));

        TextView tv = new TextView(activity);
        tv.setText("Select Album (" + items.size() + " items)");
        tv.setTextSize(15f);
        tv.setTextColor(theme.textPrimary);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 0, 0, (int) (8 * density));
        box.addView(tv);

        EditText etSearch = createFilterInput(activity, density, theme, "Search album...");
        box.addView(etSearch);

        ScrollView sv = new ScrollView(activity);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (260 * density));
        svLp.topMargin = (int) (6 * density);
        svLp.bottomMargin = (int) (10 * density);
        sv.setLayoutParams(svLp);

        LinearLayout albumListLayout = new LinearLayout(activity);
        albumListLayout.setOrientation(LinearLayout.VERTICAL);
        sv.addView(albumListLayout);
        box.addView(sv);

        // Load albums in background
        THREAD_POOL.execute(() -> {
            List<StorageScanner.AlbumInfo> allAlbums = StorageScanner.getAvailableAlbums(currentAccount);
            MAIN_HANDLER.post(() -> {
                Runnable populate = () -> {
                    albumListLayout.removeAllViews();
                    String query = etSearch.getText() != null ? etSearch.getText().toString().trim().toLowerCase() : "";
                    int shown = 0;
                    for (StorageScanner.AlbumInfo alb : allAlbums) {
                        if (!query.isEmpty() && !alb.title.toLowerCase().contains(query)) {
                            continue;
                        }
                        shown++;
                        TextView albRow = new TextView(activity);
                        albRow.setText(alb.title + " (" + alb.itemCount + ")");
                        albRow.setTextSize(13f);
                        albRow.setTextColor(theme.textPrimary);
                        albRow.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
                        albRow.setBackground(createActionPillDrawable(theme.surfaceContainer, 10 * density));
                        albRow.setClickable(true);
                        albRow.setFocusable(true);
                        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        rLp.bottomMargin = (int) (4 * density);
                        albRow.setLayoutParams(rLp);

                        albRow.setOnClickListener(v -> {
                            d.dismiss();
                            executeDbActionAsync(activity, "toExistingAlbum", items, alb.mediaKey, refreshRunner);
                        });
                        albumListLayout.addView(albRow);
                    }
                    if (shown == 0) {
                        TextView tvEmpty = new TextView(activity);
                        tvEmpty.setText("No albums found.");
                        tvEmpty.setTextSize(12.5f);
                        tvEmpty.setTextColor(theme.textSecondary);
                        tvEmpty.setGravity(Gravity.CENTER);
                        tvEmpty.setPadding(0, (int) (16 * density), 0, (int) (16 * density));
                        albumListLayout.addView(tvEmpty);
                    }
                };

                populate.run();
                etSearch.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) { populate.run(); }
                    @Override public void afterTextChanged(Editable s) {}
                });
            });
        });

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);

        TextView btnCancel = new TextView(activity);
        btnCancel.setText("Cancel");
        btnCancel.setTextSize(13f);
        btnCancel.setTextColor(theme.primary);
        btnCancel.setPadding((int) (14 * density), (int) (8 * density), (int) (14 * density), (int) (8 * density));
        btnCancel.setBackground(createActionPillDrawable(theme.surfaceContainer, 14 * density));
        btnCancel.setOnClickListener(v -> d.dismiss());
        btnRow.addView(btnCancel);

        box.addView(btnRow);
        d.setContentView(box);
        d.show();

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (screenWidth * 0.90f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void executeDbActionAsync(Activity activity, String actionId, List<StorageScanner.MediaItem> items, String param, Runnable[] refreshRunner) {
        THREAD_POOL.execute(() -> {
            StorageScanner.ActionResult res = StorageScanner.executeDatabaseAction(activity, currentAccount, actionId, items, param);
            MAIN_HANDLER.post(() -> {
                Toast.makeText(activity, res.message, Toast.LENGTH_SHORT).show();
                if (res.success) {
                    if ("toTrash".equals(actionId) && currentCriteria.source == StorageScanner.SourceType.ALBUMS) {
                        currentCriteria.albumsInclude.clear();
                    }
                    if (currentAccount != null) {
                        availableAlbums = StorageScanner.getAvailableAlbums(currentAccount);
                    }
                    if (refreshRunner != null && refreshRunner[0] != null) {
                        refreshRunner[0].run();
                    }
                }
            });
        });
    }

    private static void exportCsvFile(Activity activity, List<StorageScanner.MediaItem> items) {
        THREAD_POOL.execute(() -> {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                String name = "Google_Photos_Toolkit_" + FILE_DATE_FORMAT.format(new Date()) + ".csv";
                File file = new File(dir, name);
                String csv = StorageScanner.exportToCsv(items);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(csv.getBytes(StandardCharsets.UTF_8));
                }
                MAIN_HANDLER.post(() -> {
                    Toast.makeText(activity, "Saved CSV with " + items.size() + " items to Download/" + name, Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t) {
                MAIN_HANDLER.post(() -> {
                    Toast.makeText(activity, "Export failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Results List Renderer
    // ─────────────────────────────────────────────────────────────────────────
    private static void renderResultsList(Activity activity, LinearLayout container, List<StorageScanner.MediaItem> items, float density, Theme theme) {
        container.removeAllViews();
        if (items.isEmpty()) {
            TextView tvEmpty = new TextView(activity);
            tvEmpty.setText("No media items matched the selected filters.");
            tvEmpty.setTextSize(13f);
            tvEmpty.setTextColor(theme.textSecondary);
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, (int) (20 * density), 0, (int) (20 * density));
            container.addView(tvEmpty);
            return;
        }

        int maxDisplay = Math.min(items.size(), 50);
        for (int i = 0; i < maxDisplay; i++) {
            StorageScanner.MediaItem item = items.get(i);
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            int padH = (int) (12 * density);
            int padV = (int) (9 * density);
            card.setPadding(padH, padV, padH, padV);
            card.setBackground(createCardDrawable(false, density, theme));
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cLp.bottomMargin = (int) (6 * density);
            card.setLayoutParams(cLp);

            ImageView thumb = new ImageView(activity);
            int thSize = (int) (44 * density);
            LinearLayout.LayoutParams thLp = new LinearLayout.LayoutParams(thSize, thSize);
            thumb.setLayoutParams(thLp);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(createRoundedDrawable(theme.surfaceContainer, 8 * density));
            thumb.setClipToOutline(true);
            card.addView(thumb);

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tLp.setMargins((int) (10 * density), 0, (int) (6 * density), 0);
            textCol.setLayoutParams(tLp);

            TextView tvName = new TextView(activity);
            tvName.setText(item.filename);
            tvName.setTextSize(13f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(theme.textPrimary);
            tvName.setSingleLine(true);
            tvName.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(tvName);

            String sub = item.getFormattedSize();
            if (item.captureTimestamp > 0) sub += " • " + DATE_FORMAT.format(new Date(item.captureTimestamp));
            if (item.clusterId > 0) sub += " • Similar #" + item.clusterId;

            TextView tvSub = new TextView(activity);
            tvSub.setText(sub);
            tvSub.setTextSize(11f);
            tvSub.setTextColor(theme.textSecondary);
            textCol.addView(tvSub);
            card.addView(textCol);

            TextView badge = new TextView(activity);
            badge.setTextSize(11f);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setPadding((int) (8 * density), (int) (3 * density), (int) (8 * density), (int) (3 * density));
            if (item.quotaBytes > 0) {
                badge.setText("-" + item.getFormattedQuota());
                badge.setTextColor(theme.isDark ? 0xFFFFB4AB : 0xFFBA1A1A);
                badge.setBackground(createRoundedDrawable(theme.isDark ? 0xFF3E1E1E : 0xFFFFDAD6, 8 * density));
            } else {
                badge.setText("Free 0B");
                badge.setTextColor(theme.isDark ? 0xFF70F7E3 : 0xFF006A60);
                badge.setBackground(createRoundedDrawable(theme.isDark ? 0xFF1D3E38 : 0xFFCCE8E3, 8 * density));
            }
            card.addView(badge);

            loadThumbAsync(thumb, item.remoteUrl);
            container.addView(card);
        }

        if (items.size() > 50) {
            TextView tvMore = new TextView(activity);
            tvMore.setText("+ " + (items.size() - 50) + " more items (export CSV to view full dataset)");
            tvMore.setTextSize(11.5f);
            tvMore.setTextColor(theme.textSecondary);
            tvMore.setGravity(Gravity.CENTER);
            tvMore.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
            container.addView(tvMore);
        }
    }

    private static Bitmap fetchThumbSync(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return null;
        try {
            String thumbUrl = urlStr.replace("=s0-d", "=s140-c");
            URL u = new URL(thumbUrl);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            try (InputStream is = conn.getInputStream()) {
                return BitmapFactory.decodeStream(is);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void loadThumbAsync(ImageView iv, String urlStr) {
        iv.setImageDrawable(null);
        if (urlStr == null || urlStr.isEmpty()) return;

        Bitmap cached = THUMB_CACHE.get(urlStr);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }

        THREAD_POOL.execute(() -> {
            Bitmap bm = fetchThumbSync(urlStr);
            if (bm != null) {
                THUMB_CACHE.put(urlStr, bm);
                MAIN_HANDLER.post(() -> iv.setImageBitmap(bm));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Helpers (Theme Tokens & Reusable Cards)
    // ─────────────────────────────────────────────────────────────────────────
    public interface FilterStateProvider {
        String getActiveLabel();
    }

    public interface CardContentBuilder {
        void build(LinearLayout content, Runnable updateBadge);
    }

    private static View addFilterCard(Activity activity, LinearLayout container, float density, Theme theme,
                                      Set<String> expandedSet, String title, String subtitle,
                                      FilterStateProvider stateProvider, CardContentBuilder builder) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) (14 * density);
        int padV = (int) (10 * density);
        card.setPadding(padH, padV, padH, padV);
        card.setBackground(createCardDrawable(false, density, theme));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = (int) (6 * density);
        card.setLayoutParams(cardLp);

        LinearLayout topRow = new LinearLayout(activity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvT = new TextView(activity);
        tvT.setText(title);
        tvT.setTextSize(13.5f);
        tvT.setTypeface(null, Typeface.BOLD);
        tvT.setTextColor(theme.textPrimary);
        topRow.addView(tvT, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvBadge = new TextView(activity);
        tvBadge.setTextSize(10.5f);
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setTextColor(theme.onPrimary);
        tvBadge.setBackground(createActionPillDrawable(theme.primary, 10 * density));
        int bPadH = (int) (8 * density);
        int bPadV = (int) (2.5f * density);
        tvBadge.setPadding(bPadH, bPadV, bPadH, bPadV);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.rightMargin = (int) (8 * density);
        tvBadge.setLayoutParams(bLp);
        tvBadge.setVisibility(View.GONE);
        topRow.addView(tvBadge);

        ImageView ivChev = new ImageView(activity);
        int chevType = expandedSet.contains(title) ? MaterialVectorDrawable.TYPE_CHEVRON_UP : MaterialVectorDrawable.TYPE_CHEVRON_DOWN;
        ivChev.setImageDrawable(new MaterialVectorDrawable(chevType, theme.textSecondary));
        ivChev.setLayoutParams(new LinearLayout.LayoutParams((int) (20 * density), (int) (20 * density)));
        topRow.addView(ivChev);
        card.addView(topRow);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvS = new TextView(activity);
            tvS.setText(subtitle);
            tvS.setTextSize(11.5f);
            tvS.setTextColor(theme.textSecondary);
            tvS.setPadding(0, (int) (2 * density), 0, 0);
            card.addView(tvS);
        }

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, (int) (10 * density), 0, (int) (4 * density));
        content.setVisibility(expandedSet.contains(title) ? View.VISIBLE : View.GONE);

        Runnable updateBadge = () -> {
            String label = stateProvider != null ? stateProvider.getActiveLabel() : null;
            if (label != null && !label.isEmpty()) {
                tvBadge.setText(label);
                tvBadge.setVisibility(View.VISIBLE);
                card.setBackground(createCardDrawable(true, density, theme));
            } else {
                tvBadge.setVisibility(View.GONE);
                card.setBackground(createCardDrawable(false, density, theme));
            }
        };

        builder.build(content, updateBadge);
        updateBadge.run();
        card.addView(content);

        View.OnClickListener toggle = v -> {
            if (expandedSet.contains(title)) {
                expandedSet.remove(title);
                content.setVisibility(View.GONE);
                ivChev.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_CHEVRON_DOWN, theme.textSecondary));
            } else {
                expandedSet.add(title);
                content.setVisibility(View.VISIBLE);
                ivChev.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_CHEVRON_UP, theme.textSecondary));
            }
        };

        card.setClickable(true);
        card.setOnClickListener(toggle);
        container.addView(card);
        return card;
    }

    private static void updateFilterVisibility(StorageScanner.SourceType src, Map<String, View> filterCards) {
        if (filterCards == null || filterCards.isEmpty()) return;

        // Hide all initially
        for (View v : filterCards.values()) {
            if (v != null) v.setVisibility(View.GONE);
        }

        View v;

        // 1. Permanent Filters (Always visible on ALL sources including TRASH, matching user.js)
        if ((v = filterCards.get("dateInterval")) != null) v.setVisibility(View.VISIBLE);
        if ((v = filterCards.get("similarity")) != null) v.setVisibility(View.VISIBLE);
        if ((v = filterCards.get("duration")) != null) v.setVisibility(View.VISIBLE);
        if ((v = filterCards.get("type")) != null) v.setVisibility(View.VISIBLE);
        if ((v = filterCards.get("sortBySize")) != null) v.setVisibility(View.VISIBLE);

        // 2. Albums-only filter
        if (src == StorageScanner.SourceType.ALBUMS) {
            if ((v = filterCards.get("includeAlbums")) != null) v.setVisibility(View.VISIBLE);
        }

        // 3. Search-only filter
        if (src == StorageScanner.SourceType.SEARCH) {
            if ((v = filterCards.get("search")) != null) v.setVisibility(View.VISIBLE);
            if ((v = filterCards.get("favorite")) != null) v.setVisibility(View.VISIBLE);
        }

        // 4. Filters visible in Library, Search, and Favorites
        if (src == StorageScanner.SourceType.LIBRARY || src == StorageScanner.SourceType.SEARCH || src == StorageScanner.SourceType.FAVORITES) {
            if ((v = filterCards.get("owned")) != null) v.setVisibility(View.VISIBLE);
            if ((v = filterCards.get("uploadStatus")) != null) v.setVisibility(View.VISIBLE);
            if ((v = filterCards.get("archive")) != null) v.setVisibility(View.VISIBLE);
        }

        // 5. Library-only filter
        if (src == StorageScanner.SourceType.LIBRARY) {
            if ((v = filterCards.get("excludeFavorites")) != null) v.setVisibility(View.VISIBLE);
        }

        // 6. Metadata filters visible everywhere EXCEPT Trash
        if (src != StorageScanner.SourceType.TRASH) {
            if ((v = filterCards.get("quality")) != null) v.setVisibility(View.VISIBLE);
            if ((v = filterCards.get("size")) != null) v.setVisibility(View.VISIBLE);
            if ((v = filterCards.get("resolution")) != null) v.setVisibility(View.VISIBLE);
            if ((v = filterCards.get("location")) != null) v.setVisibility(View.VISIBLE);
            if ((v = filterCards.get("filename")) != null) v.setVisibility(View.VISIBLE);
            if ((v = filterCards.get("description")) != null) v.setVisibility(View.VISIBLE);
            if ((v = filterCards.get("space")) != null) v.setVisibility(View.VISIBLE);

            if (src != StorageScanner.SourceType.LOCKED) {
                if ((v = filterCards.get("excludeAlbums")) != null) v.setVisibility(View.VISIBLE);
            }
            if (src != StorageScanner.SourceType.SHARED) {
                if ((v = filterCards.get("excludeShared")) != null) v.setVisibility(View.VISIBLE);
            }
        }
    }

    public interface OnDateTimeSelectedListener {
        void onSelected(long epochMs);
    }

    private static LinearLayout createDateTimePickerRow(Activity activity, float density, Theme theme, String label, long initialEpochMs, OnDateTimeSelectedListener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, (int) (4 * density), 0, (int) (8 * density));

        TextView tvLabel = new TextView(activity);
        tvLabel.setText(label);
        tvLabel.setTextSize(12f);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setTextColor(theme.textPrimary);
        tvLabel.setPadding(0, 0, 0, (int) (4 * density));
        row.addView(tvLabel);

        LinearLayout pickerControls = new LinearLayout(activity);
        pickerControls.setOrientation(LinearLayout.HORIZONTAL);
        pickerControls.setGravity(Gravity.CENTER_VERTICAL);

        Calendar cal = Calendar.getInstance();
        if (initialEpochMs > 0) {
            cal.setTimeInMillis(initialEpochMs);
        }

        final long[] selectedTime = new long[]{initialEpochMs};

        SimpleDateFormat dFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat tFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

        TextView btnDate = new TextView(activity);
        btnDate.setText(initialEpochMs > 0 ? dFmt.format(new Date(initialEpochMs)) : "Select Date");
        btnDate.setTextSize(12.5f);
        btnDate.setTextColor(initialEpochMs > 0 ? theme.primary : theme.textSecondary);
        btnDate.setGravity(Gravity.CENTER);
        btnDate.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
        btnDate.setBackground(createRoundedDrawable(theme.searchInputBg, 8 * density));
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
        dateLp.rightMargin = (int) (6 * density);
        btnDate.setLayoutParams(dateLp);

        TextView btnTime = new TextView(activity);
        btnTime.setText(initialEpochMs > 0 ? tFmt.format(new Date(initialEpochMs)) : "Select Time");
        btnTime.setTextSize(12.5f);
        btnTime.setTextColor(initialEpochMs > 0 ? theme.primary : theme.textSecondary);
        btnTime.setGravity(Gravity.CENTER);
        btnTime.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
        btnTime.setBackground(createRoundedDrawable(theme.searchInputBg, 8 * density));
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        timeLp.rightMargin = (int) (6 * density);
        btnTime.setLayoutParams(timeLp);

        TextView btnClear = new TextView(activity);
        btnClear.setText("Clear");
        btnClear.setTextSize(11.5f);
        btnClear.setTextColor(theme.isDark ? 0xFFFFB4AB : 0xFFBA1A1A);
        btnClear.setGravity(Gravity.CENTER);
        btnClear.setPadding((int) (10 * density), (int) (8 * density), (int) (10 * density), (int) (8 * density));
        btnClear.setBackground(createRoundedDrawable(theme.surfaceContainer, 8 * density));

        btnDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(activity, (view, year, month, dayOfMonth) -> {
                cal.set(Calendar.YEAR, year);
                cal.set(Calendar.MONTH, month);
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                selectedTime[0] = cal.getTimeInMillis();
                btnDate.setText(dFmt.format(cal.getTime()));
                btnDate.setTextColor(theme.primary);
                listener.onSelected(selectedTime[0]);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });

        btnTime.setOnClickListener(v -> {
            TimePickerDialog tpd = new TimePickerDialog(activity, (view, hourOfDay, minute) -> {
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);
                selectedTime[0] = cal.getTimeInMillis();
                btnTime.setText(tFmt.format(cal.getTime()));
                btnTime.setTextColor(theme.primary);
                listener.onSelected(selectedTime[0]);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true);
            tpd.show();
        });

        btnClear.setOnClickListener(v -> {
            selectedTime[0] = 0;
            btnDate.setText("Select Date");
            btnDate.setTextColor(theme.textSecondary);
            btnTime.setText("Select Time");
            btnTime.setTextColor(theme.textSecondary);
            listener.onSelected(0);
        });

        pickerControls.addView(btnDate);
        pickerControls.addView(btnTime);
        pickerControls.addView(btnClear);
        row.addView(pickerControls);
        return row;
    }

    public static long parseBytesInput(String s) {
        if (s == null) return 0;
        String str = s.trim().toUpperCase(Locale.ROOT).replace(",", "");
        if (str.isEmpty()) return 0;
        try {
            if (str.endsWith("GB") || str.endsWith("G")) {
                String num = str.replaceAll("[A-Z]", "").trim();
                return (long) (Double.parseDouble(num) * 1024L * 1024L * 1024L);
            } else if (str.endsWith("MB") || str.endsWith("M")) {
                String num = str.replaceAll("[A-Z]", "").trim();
                return (long) (Double.parseDouble(num) * 1024L * 1024L);
            } else if (str.endsWith("KB") || str.endsWith("K")) {
                String num = str.replaceAll("[A-Z]", "").trim();
                return (long) (Double.parseDouble(num) * 1024L);
            } else {
                return Long.parseLong(str);
            }
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static EditText createFilterInput(Activity activity, float density, Theme theme, String hint) {
        EditText et = new EditText(activity);
        et.setHint(hint);
        et.setTextSize(13f);
        et.setTextColor(theme.textPrimary);
        et.setHintTextColor(theme.textSecondary);
        et.setBackground(createRoundedDrawable(theme.searchInputBg, 10 * density));
        int p = (int) (12 * density);
        et.setPadding(p, (int) (8 * density), p, (int) (8 * density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (6 * density);
        et.setLayoutParams(lp);
        return et;
    }

    public interface OnRadioSelected {
        void onSelected(int index);
    }

    private static RadioGroup createRadioGroup(Activity activity, String[] labels, int defaultIdx, Theme theme, OnRadioSelected listener) {
        RadioGroup rg = new RadioGroup(activity);
        rg.setOrientation(RadioGroup.VERTICAL);
        int selectedId = -1;
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(activity);
            int id = View.generateViewId();
            rb.setId(id);
            rb.setText(labels[i]);
            rb.setTextSize(13f);
            rb.setTextColor(theme.textPrimary);
            rg.addView(rb);
            if (i == defaultIdx) {
                selectedId = id;
            }
        }
        if (selectedId != -1) {
            rg.check(selectedId);
        }
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (group.getChildAt(i).getId() == checkedId) {
                    listener.onSelected(i);
                    break;
                }
            }
        });
        return rg;
    }

    private static void showActionConfirmDialog(Activity activity, String msg, Runnable onConfirm, float density, Theme theme) {
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(createRoundedDrawable(theme.surface, 20 * density));
        int p = (int) (20 * density);
        box.setPadding(p, p, p, (int) (14 * density));

        TextView tv = new TextView(activity);
        tv.setText(msg);
        tv.setTextSize(14.5f);
        tv.setTextColor(theme.textPrimary);
        tv.setTypeface(null, Typeface.BOLD);
        box.addView(tv);

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        btnRow.setPadding(0, (int) (16 * density), 0, 0);

        TextView btnCancel = new TextView(activity);
        btnCancel.setText("Cancel");
        btnCancel.setTextSize(13f);
        btnCancel.setTextColor(theme.primary);
        btnCancel.setPadding((int) (14 * density), (int) (8 * density), (int) (14 * density), (int) (8 * density));
        btnCancel.setBackground(createActionPillDrawable(theme.surfaceContainer, 14 * density));
        btnCancel.setOnClickListener(v -> d.dismiss());
        btnRow.addView(btnCancel);

        TextView btnOk = new TextView(activity);
        btnOk.setText("Confirm");
        btnOk.setTextSize(13f);
        btnOk.setTypeface(null, Typeface.BOLD);
        btnOk.setTextColor(theme.onPrimary);
        btnOk.setPadding((int) (16 * density), (int) (8 * density), (int) (16 * density), (int) (8 * density));
        btnOk.setBackground(createActionPillDrawable(theme.isDark ? 0xFFBA1A1A : 0xFFB3261E, 14 * density));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okLp.leftMargin = (int) (8 * density);
        btnOk.setLayoutParams(okLp);
        btnOk.setOnClickListener(v -> {
            d.dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        btnRow.addView(btnOk);

        box.addView(btnRow);
        d.setContentView(box);
        d.show();

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min((int) (screenWidth * 0.88f), (int) (400 * density)), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
    }

    private static class SimpleWatcher implements TextWatcher {
        private final SimpleConsumer consumer;
        SimpleWatcher(SimpleConsumer consumer) { this.consumer = consumer; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { consumer.accept(s.toString()); }
        @Override public void afterTextChanged(Editable s) {}
    }

    private interface SimpleConsumer {
        void accept(String s);
    }

    private static GradientDrawable createRoundedDrawable(int color, float radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(radiusPx);
        gd.setColor(color);
        return gd;
    }

    private static GradientDrawable createCardDrawable(boolean active, float density, Theme theme) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(14 * density);
        gd.setColor(active ? theme.cardActive : theme.card);
        gd.setStroke(active ? (int) (1.5f * density) : (int) (1 * density),
                active ? theme.cardBorderActive : theme.cardBorder);
        return gd;
    }

    private static Drawable createActionPillDrawable(int bgColor, float radiusPx) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(radiusPx);
        shape.setColor(bgColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(radiusPx);
            mask.setColor(Color.WHITE);
            ColorStateList rippleColor = ColorStateList.valueOf(0x22888888);
            return new RippleDrawable(rippleColor, shape, mask);
        }
        return shape;
    }

    private static View createHeaderIconButton(Activity activity, Drawable iconDrawable, int touchSizePx, int iconSizePx) {
        FrameLayout box = new FrameLayout(activity);
        box.setClickable(true);
        box.setFocusable(true);
        box.setLayoutParams(new LinearLayout.LayoutParams(touchSizePx, touchSizePx));

        ImageView iv = new ImageView(activity);
        iv.setImageDrawable(iconDrawable);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER);
        iv.setLayoutParams(ivLp);
        box.addView(iv);
        return box;
    }
}
