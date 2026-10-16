package app.morphe.extension.shared.diagnostics;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.patches.PhenotypeFlagManager;
import app.morphe.extension.shared.patches.PhenotypeFlagManager.MaterialVectorDrawable;
import app.morphe.extension.shared.patches.PhenotypeFlagManager.Theme;

/**
 * Material 3 Diagnostics & Log Viewer Dialog.
 *
 * Single unified log view with live search, inspection, and 1-tap share or copy diagnostics.
 * Styled to seamlessly match Google Photos Material 3 design language.
 */
public final class DiagnosticsDialog {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private DiagnosticsDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

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

        // ─────────────────────────────────────────────────────────────────────
        // 1. Top Bar (Tailor-made M3 title with vector icon and action buttons)
        // ─────────────────────────────────────────────────────────────────────
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (18 * density);
        int padV = (int) (12 * density);
        topBar.setPadding(padH, padV, (int) (10 * density), (int) (8 * density));

        // Diagnostics leading vector icon in subtle tonal capsule
        FrameLayout iconContainer = new FrameLayout(activity);
        int icBox = (int) (34 * density);
        LinearLayout.LayoutParams icBoxLp = new LinearLayout.LayoutParams(icBox, icBox);
        icBoxLp.setMargins(0, 0, (int) (10 * density), 0);
        iconContainer.setLayoutParams(icBoxLp);
        GradientDrawable icBg = new GradientDrawable();
        icBg.setCornerRadius(10 * density);
        icBg.setColor(theme.surfaceContainer);
        iconContainer.setBackground(icBg);

        ImageView ivLead = new ImageView(activity);
        ivLead.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_LOGS, theme.primary));
        int ivLeadSize = (int) (20 * density);
        FrameLayout.LayoutParams ivLeadLp = new FrameLayout.LayoutParams(ivLeadSize, ivLeadSize, Gravity.CENTER);
        ivLead.setLayoutParams(ivLeadLp);
        iconContainer.addView(ivLead);
        topBar.addView(iconContainer);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleCol.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Diagnostics & Logs");
        tvTitle.setTextSize(17.5f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(theme.textPrimary);
        titleCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText("Inspection & Session Analyzer");
        tvSub.setTextSize(11.5f);
        tvSub.setTextColor(theme.textSecondary);
        titleCol.addView(tvSub);

        topBar.addView(titleCol);

        // Header Action: Close (40dp touch target)
        View btnClose = createHeaderIconButton(activity, new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_CLOSE, theme.textSecondary), (int) (40 * density), (int) (20 * density));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        topBar.addView(btnClose);
        root.addView(topBar);

        // ─────────────────────────────────────────────────────────────────────
        // 2. Active Session Selector Card
        // ─────────────────────────────────────────────────────────────────────
        LinearLayout sessionCard = new LinearLayout(activity);
        sessionCard.setOrientation(LinearLayout.VERTICAL);
        sessionCard.setBackground(createRoundedDrawable(theme.surfaceContainer, 14 * density));
        int sPad = (int) (12 * density);
        sessionCard.setPadding(sPad, (int) (8 * density), sPad, (int) (8 * density));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scLp.setMargins(padH, (int) (4 * density), padH, (int) (8 * density));
        sessionCard.setLayoutParams(scLp);

        TextView tvSessionLabel = new TextView(activity);
        tvSessionLabel.setText("Active Log Session");
        tvSessionLabel.setTextSize(11.5f);
        tvSessionLabel.setTypeface(null, Typeface.BOLD);
        tvSessionLabel.setTextColor(theme.primary);
        sessionCard.addView(tvSessionLabel);

        LinearLayout sessionSelectorBox = new LinearLayout(activity);
        sessionSelectorBox.setOrientation(LinearLayout.HORIZONTAL);
        sessionSelectorBox.setGravity(Gravity.CENTER_VERTICAL);
        sessionSelectorBox.setClickable(true);
        sessionSelectorBox.setFocusable(true);
        int boxBgColor = theme.isDark ? 0xFF242728 : 0xFFFFFFFF;
        sessionSelectorBox.setBackground(createRoundedCardDrawable(boxBgColor, theme.outline, 10 * density));
        sessionSelectorBox.setPadding((int) (12 * density), (int) (9 * density), (int) (12 * density), (int) (9 * density));
        LinearLayout.LayoutParams ssbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ssbLp.topMargin = (int) (6 * density);
        sessionSelectorBox.setLayoutParams(ssbLp);

        TextView tvSelectedSession = new TextView(activity);
        tvSelectedSession.setText("Select session...");
        tvSelectedSession.setTextSize(13f);
        tvSelectedSession.setTypeface(null, Typeface.BOLD);
        tvSelectedSession.setTextColor(theme.textPrimary);
        tvSelectedSession.setSingleLine(true);
        tvSelectedSession.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tssLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvSelectedSession.setLayoutParams(tssLp);
        sessionSelectorBox.addView(tvSelectedSession);

        ImageView ivChevron = new ImageView(activity);
        ivChevron.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_CHEVRON_DOWN, theme.textSecondary));
        LinearLayout.LayoutParams cvLp = new LinearLayout.LayoutParams((int) (16 * density), (int) (16 * density));
        cvLp.leftMargin = (int) (8 * density);
        ivChevron.setLayoutParams(cvLp);
        sessionSelectorBox.addView(ivChevron);

        sessionCard.addView(sessionSelectorBox);

        // Crash Warning Banner
        LinearLayout crashBanner = new LinearLayout(activity);
        crashBanner.setOrientation(LinearLayout.VERTICAL);
        crashBanner.setBackground(createRoundedDrawable(theme.isDark ? 0x33BA1A1A : 0x22BA1A1A, 8 * density));
        crashBanner.setPadding((int) (10 * density), (int) (6 * density), (int) (10 * density), (int) (6 * density));
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.topMargin = (int) (6 * density);
        crashBanner.setLayoutParams(cbLp);
        crashBanner.setVisibility(View.GONE);

        TextView tvCrashBannerText = new TextView(activity);
        tvCrashBannerText.setTextSize(11.5f);
        tvCrashBannerText.setTextColor(theme.isDark ? 0xFFFFB4AB : 0xFFBA1A1A);
        tvCrashBannerText.setTypeface(null, Typeface.BOLD);
        crashBanner.addView(tvCrashBannerText);
        sessionCard.addView(crashBanner);

        root.addView(sessionCard);

        // ─────────────────────────────────────────────────────────────────────
        // 3. Search Box (Live log filter covering all logs)
        // ─────────────────────────────────────────────────────────────────────
        LinearLayout filterBar = new LinearLayout(activity);
        filterBar.setOrientation(LinearLayout.VERTICAL);
        filterBar.setPadding(padH, 0, padH, (int) (6 * density));

        LinearLayout inputWrapper = new LinearLayout(activity);
        inputWrapper.setOrientation(LinearLayout.HORIZONTAL);
        inputWrapper.setGravity(Gravity.CENTER_VERTICAL);
        inputWrapper.setBackground(createRoundedDrawable(theme.searchInputBg, 14 * density));
        LinearLayout.LayoutParams iwLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (44 * density));
        inputWrapper.setLayoutParams(iwLp);

        ImageView ivSearchIcon = new ImageView(activity);
        ivSearchIcon.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_SEARCH, theme.textSecondary));
        int siSize = (int) (18 * density);
        LinearLayout.LayoutParams siLp = new LinearLayout.LayoutParams(siSize, siSize);
        siLp.setMargins((int) (12 * density), 0, (int) (4 * density), 0);
        ivSearchIcon.setLayoutParams(siLp);
        inputWrapper.addView(ivSearchIcon);

        EditText etSearch = new EditText(activity);
        etSearch.setHint("Search logs (e.g. backup, album, toolkit, exception)...");
        etSearch.setTextSize(13.5f);
        etSearch.setTextColor(theme.textPrimary);
        etSearch.setHintTextColor(theme.textSecondary);
        etSearch.setBackground(null);
        etSearch.setSingleLine(true);
        etSearch.setMaxLines(1);
        etSearch.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER);
        etSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        int etPad = (int) (8 * density);
        etSearch.setPadding(etPad, 0, etPad, 0);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        etSearch.setLayoutParams(etLp);
        inputWrapper.addView(etSearch);

        ImageView btnClearSearch = new ImageView(activity);
        btnClearSearch.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_CLOSE, theme.textSecondary));
        btnClearSearch.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int cPad = (int) (10 * density);
        btnClearSearch.setPadding(cPad, 0, cPad, 0);
        btnClearSearch.setVisibility(View.GONE);
        btnClearSearch.setClickable(true);
        btnClearSearch.setFocusable(true);
        inputWrapper.addView(btnClearSearch);

        filterBar.addView(inputWrapper);

        // Category Chips Row (Horizontal Scroll)
        HorizontalScrollView chipScrollView = new HorizontalScrollView(activity);
        chipScrollView.setHorizontalScrollBarEnabled(false);
        chipScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams csvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        csvLp.topMargin = (int) (8 * density);
        chipScrollView.setLayoutParams(csvLp);

        LinearLayout chipsContainer = new LinearLayout(activity);
        chipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipScrollView.addView(chipsContainer);

        final String[] activeCategory = new String[]{"ALL"};
        final Runnable[] loadLogsRef = new Runnable[1];

        final String[][] chipDefs = new String[][] {
            {"ALL", "All"},
            {"ERROR", "🚨 Errors & Crashes"},
            {"WARN", "⚠️ Warnings"},
            {"INFO", "ℹ️ Info"},
            {"MORPHE", "🧩 Morphe"}
        };
        final TextView[] chipViews = new TextView[chipDefs.length];

        Runnable updateChipStyles = () -> {
            for (int i = 0; i < chipDefs.length; i++) {
                TextView cv = chipViews[i];
                if (cv == null) continue;
                boolean isSelected = chipDefs[i][0].equals(activeCategory[0]);
                if (isSelected) {
                    cv.setBackground(createRoundedDrawable(theme.primary, 14 * density));
                    cv.setTextColor(theme.onPrimary);
                    cv.setTypeface(null, Typeface.BOLD);
                } else {
                    cv.setBackground(createRoundedCardDrawable(theme.surfaceContainer, theme.outline, 14 * density));
                    cv.setTextColor(theme.textSecondary);
                    cv.setTypeface(null, Typeface.NORMAL);
                }
            }
        };

        for (int i = 0; i < chipDefs.length; i++) {
            final String key = chipDefs[i][0];
            String label = chipDefs[i][1];
            TextView chipView = new TextView(activity);
            chipView.setText(label);
            chipView.setTextSize(12f);
            chipView.setGravity(Gravity.CENTER);
            int cvPadH = (int) (12 * density);
            int cvPadV = (int) (6 * density);
            chipView.setPadding(cvPadH, cvPadV, cvPadH, cvPadV);
            chipView.setClickable(true);
            chipView.setFocusable(true);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cLp.rightMargin = (int) (6 * density);
            chipView.setLayoutParams(cLp);

            chipViews[i] = chipView;
            chipView.setOnClickListener(v -> {
                if (!activeCategory[0].equals(key)) {
                    activeCategory[0] = key;
                    updateChipStyles.run();
                    if (loadLogsRef[0] != null) {
                        loadLogsRef[0].run();
                    }
                }
            });
            chipsContainer.addView(chipView);
        }
        updateChipStyles.run();
        filterBar.addView(chipScrollView);
        root.addView(filterBar);

        // ─────────────────────────────────────────────────────────────────────
        // 4. Log Content View (Modern Dark Terminal covering all logs)
        // ─────────────────────────────────────────────────────────────────────
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svLp.setMargins(padH, (int) (4 * density), padH, (int) (8 * density));
        scrollView.setLayoutParams(svLp);
        int logBg = theme.isDark ? 0xFF141616 : 0xFF1E2022;
        scrollView.setBackground(createRoundedDrawable(logBg, 14 * density));

        TextView tvLogs = new TextView(activity);
        tvLogs.setTextSize(11);
        tvLogs.setTypeface(Typeface.MONOSPACE);
        tvLogs.setTextColor(0xFFE1E3E5);
        tvLogs.setTextIsSelectable(false);
        int logPad = (int) (12 * density);
        tvLogs.setPadding(logPad, logPad, logPad, logPad);
        scrollView.addView(tvLogs);
        root.addView(scrollView);

        // ─────────────────────────────────────────────────────────────────────
        // 5. Bottom Action Dock (Tailored M3 Share, Copy, and Clear buttons)
        // ─────────────────────────────────────────────────────────────────────
        LinearLayout bottomDock = new LinearLayout(activity);
        bottomDock.setOrientation(LinearLayout.HORIZONTAL);
        bottomDock.setGravity(Gravity.CENTER_VERTICAL);
        bottomDock.setPadding(padH, (int) (6 * density), padH, (int) (16 * density));

        // Export File Button (Primary Pill)
        LinearLayout btnExport = new LinearLayout(activity);
        btnExport.setOrientation(LinearLayout.HORIZONTAL);
        btnExport.setGravity(Gravity.CENTER);
        btnExport.setBackground(createRoundedDrawable(theme.primary, 22 * density));
        btnExport.setClickable(true);
        btnExport.setFocusable(true);
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(0, (int) (44 * density), 1.0f);
        exportLp.setMargins(0, 0, (int) (6 * density), 0);
        btnExport.setLayoutParams(exportLp);

        ImageView ivExport = new ImageView(activity);
        ivExport.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_EXPORT, theme.onPrimary));
        LinearLayout.LayoutParams ivExportLp = new LinearLayout.LayoutParams((int) (18 * density), (int) (18 * density));
        ivExportLp.setMargins(0, 0, (int) (6 * density), 0);
        ivExport.setLayoutParams(ivExportLp);
        btnExport.addView(ivExport);

        TextView tvExport = new TextView(activity);
        tvExport.setText("Export File");
        tvExport.setTextSize(13f);
        tvExport.setTypeface(null, Typeface.BOLD);
        tvExport.setTextColor(theme.onPrimary);
        btnExport.addView(tvExport);
        bottomDock.addView(btnExport);

        // Copy (GitHub) Button (Tonal Outlined Pill)
        LinearLayout btnCopy = new LinearLayout(activity);
        btnCopy.setOrientation(LinearLayout.HORIZONTAL);
        btnCopy.setGravity(Gravity.CENTER);
        btnCopy.setBackground(createRoundedCardDrawable(theme.surfaceContainer, theme.outline, 22 * density));
        btnCopy.setClickable(true);
        btnCopy.setFocusable(true);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, (int) (44 * density), 1.15f);
        copyLp.setMargins(0, 0, (int) (6 * density), 0);
        btnCopy.setLayoutParams(copyLp);

        ImageView ivCopy = new ImageView(activity);
        ivCopy.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_CLIPBOARD, theme.primary));
        LinearLayout.LayoutParams ivCopyLp = new LinearLayout.LayoutParams((int) (18 * density), (int) (18 * density));
        ivCopyLp.setMargins(0, 0, (int) (6 * density), 0);
        ivCopy.setLayoutParams(ivCopyLp);
        btnCopy.addView(ivCopy);

        TextView tvCopy = new TextView(activity);
        tvCopy.setText("Copy (GitHub)");
        tvCopy.setTextSize(12.5f);
        tvCopy.setTypeface(null, Typeface.BOLD);
        tvCopy.setTextColor(theme.primary);
        btnCopy.addView(tvCopy);
        bottomDock.addView(btnCopy);

        // Share Button (Tonal Outlined Icon)
        FrameLayout btnShare = new FrameLayout(activity);
        btnShare.setBackground(createRoundedCardDrawable(theme.surfaceContainer, theme.outline, 22 * density));
        btnShare.setClickable(true);
        btnShare.setFocusable(true);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams((int) (44 * density), (int) (44 * density));
        shareLp.setMargins(0, 0, (int) (6 * density), 0);
        btnShare.setLayoutParams(shareLp);

        ImageView ivShare = new ImageView(activity);
        ivShare.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_EXPORT, theme.textPrimary));
        FrameLayout.LayoutParams ivShareLp = new FrameLayout.LayoutParams((int) (18 * density), (int) (18 * density), Gravity.CENTER);
        ivShare.setLayoutParams(ivShareLp);
        btnShare.addView(ivShare);
        bottomDock.addView(btnShare);

        // Clear Button (Destructive Tonal Pill)
        FrameLayout btnClear = new FrameLayout(activity);
        int clearColor = theme.isDark ? 0xFFFFB4AB : 0xFFBA1A1A;
        int clearStroke = theme.isDark ? 0xFF5C2020 : 0xFFFFDAD6;
        btnClear.setBackground(createRoundedCardDrawable(theme.surfaceContainer, clearStroke, 22 * density));
        btnClear.setClickable(true);
        btnClear.setFocusable(true);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams((int) (44 * density), (int) (44 * density));
        btnClear.setLayoutParams(clearLp);

        ImageView ivClear = new ImageView(activity);
        ivClear.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_DELETE, clearColor));
        FrameLayout.LayoutParams ivClearLp = new FrameLayout.LayoutParams((int) (20 * density), (int) (20 * density), Gravity.CENTER);
        ivClear.setLayoutParams(ivClearLp);
        btnClear.addView(ivClear);
        bottomDock.addView(btnClear);

        root.addView(bottomDock);

        // ─────────────────────────────────────────────────────────────────────
        // Logic & Data Binding
        // ─────────────────────────────────────────────────────────────────────
        List<SessionLogManager.SessionInfo> sessions = new ArrayList<>();
        final SessionLogManager.SessionInfo[] activeSession = new SessionLogManager.SessionInfo[1];

        Runnable loadLogs = () -> {
            if (activeSession[0] == null) {
                tvLogs.setText("No log session selected.");
                return;
            }
            final String q = etSearch.getText().toString();
            final String cat = activeCategory[0];
            new Thread(() -> {
                String content = SessionLogManager.readSessionContent(activeSession[0], q, cat, 1000);
                MAIN_HANDLER.post(() -> {
                    tvLogs.setText(content);
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                });
            }).start();
        };
        loadLogsRef[0] = loadLogs;

        // Sessions loader
        Runnable reloadSessions = () -> {
            sessions.clear();
            sessions.addAll(SessionLogManager.getAllSessions());
            if (sessions.isEmpty()) {
                tvSelectedSession.setText("No sessions found");
                tvLogs.setText("No sessions found yet.");
                return;
            }

            int defaultIndex = 0;
            for (int i = 0; i < sessions.size(); i++) {
                SessionLogManager.SessionInfo s = sessions.get(i);
                if (s.isCurrent) {
                    defaultIndex = i;
                    break;
                }
            }

            activeSession[0] = sessions.get(defaultIndex);
            tvSelectedSession.setText(activeSession[0].getDisplayName());
            updateCrashBanner(crashBanner, tvCrashBannerText, activeSession[0]);
            loadLogs.run();
        };

        sessionSelectorBox.setOnClickListener(v -> {
            if (sessions.isEmpty()) return;
            showSessionPicker(activity, theme, sessions, activeSession[0], selectedSession -> {
                activeSession[0] = selectedSession;
                tvSelectedSession.setText(selectedSession.getDisplayName());
                updateCrashBanner(crashBanner, tvCrashBannerText, activeSession[0]);
                loadLogs.run();
            });
        });

        // Search text watcher
        Runnable[] searchDelay = new Runnable[1];
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (searchDelay[0] != null) MAIN_HANDLER.removeCallbacks(searchDelay[0]);
                searchDelay[0] = loadLogs;
                MAIN_HANDLER.postDelayed(searchDelay[0], 250);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            loadLogs.run();
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                }
                etSearch.clearFocus();
                return true;
            }
            return false;
        });

        // Action Buttons Click Handlers
        btnExport.setOnClickListener(v -> {
            if (activeSession[0] != null) {
                String q = etSearch.getText().toString();
                String cat = activeCategory[0];
                showExportOptionsDialog(activity, theme, activeSession[0], q, cat);
            }
        });

        btnCopy.setOnClickListener(v -> {
            if (activeSession[0] != null) {
                String q = etSearch.getText().toString();
                String cat = activeCategory[0];
                SessionLogManager.copyGitHubMarkdown(activity, activeSession[0], q, cat);
            }
        });

        btnCopy.setOnLongClickListener(v -> {
            if (activeSession[0] != null) {
                String q = etSearch.getText().toString();
                String cat = activeCategory[0];
                SessionLogManager.copyToClipboard(activity, activeSession[0], q, cat);
            }
            return true;
        });

        btnShare.setOnClickListener(v -> {
            if (activeSession[0] != null) {
                String q = etSearch.getText().toString();
                String cat = activeCategory[0];
                showShareOptionsDialog(activity, theme, activeSession[0], q, cat);
            }
        });

        btnClear.setOnClickListener(v -> {
            showClearConfirmationDialog(activity, theme, reloadSessions);
        });

        dialog.setOnDismissListener(d -> {
            if (PhenotypeFlagManager.getCurrentOpenDialog() == dialog) {
                PhenotypeFlagManager.setCurrentOpenDialog(null);
            }
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && activity.getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
            }
        });

        reloadSessions.run();
        dialog.setContentView(root);
        dialog.show();
        PhenotypeFlagManager.setCurrentOpenDialog(dialog);
        PhenotypeFlagManager.updateDockTabState(activity, PhenotypeFlagManager.DOCK_TAB_LOGS);

        // Responsive popup window sizing with clean rounded geometry (adaptive up to 920dp on tablet)
        PhenotypeFlagManager.applyAdaptiveDialogBounds(dialog, activity);
    }

    private static View createHeaderIconButton(Activity activity, MaterialVectorDrawable icon, int sizePx, int iconPx) {
        FrameLayout frame = new FrameLayout(activity);
        frame.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        frame.setClickable(true);
        frame.setFocusable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TypedValue outValue = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
            frame.setBackgroundResource(outValue.resourceId);
        }

        ImageView iv = new ImageView(activity);
        iv.setImageDrawable(icon);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER);
        iv.setLayoutParams(flp);
        frame.addView(iv);
        return frame;
    }

    private static GradientDrawable createRoundedDrawable(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(color);
        return d;
    }

    private static GradientDrawable createRoundedCardDrawable(int bgColor, int strokeColor, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(bgColor);
        d.setStroke(1, strokeColor);
        return d;
    }

    private interface OnSessionSelectedListener {
        void onSelected(SessionLogManager.SessionInfo session);
    }

    private static void showSessionPicker(Activity activity, Theme theme,
                                          List<SessionLogManager.SessionInfo> sessions,
                                          SessionLogManager.SessionInfo currentActive,
                                          OnSessionSelectedListener listener) {
        float density = activity.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createRoundedDrawable(theme.surface, 24 * density));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (20 * density);
        int padV = (int) (16 * density);
        header.setPadding(padH, padV, padH, (int) (8 * density));

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Log Sessions");
        tvTitle.setTextSize(16.5f);
        tvTitle.setTextColor(theme.textPrimary);
        tvTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText("Select a session to inspect");
        tvSub.setTextSize(11.5f);
        tvSub.setTextColor(theme.textSecondary);
        titleCol.addView(tvSub);

        header.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View btnClose = createHeaderIconButton(activity, new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_CLOSE, theme.textSecondary), (int) (36 * density), (int) (18 * density));
        btnClose.setOnClickListener(v -> d.dismiss());
        header.addView(btnClose);
        root.addView(header);

        ScrollView sv = new ScrollView(activity);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svLp.setMargins(0, 0, 0, (int) (8 * density));
        sv.setLayoutParams(svLp);

        LinearLayout listLayout = new LinearLayout(activity);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(padH, (int) (4 * density), padH, (int) (12 * density));

        for (SessionLogManager.SessionInfo s : sessions) {
            boolean isSelected = currentActive != null && s.id.equals(currentActive.id);

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setClickable(true);
            card.setFocusable(true);
            int cardPadH = (int) (14 * density);
            int cardPadV = (int) (12 * density);
            card.setPadding(cardPadH, cardPadV, cardPadH, cardPadV);

            int bgColor = isSelected ? theme.cardActive : (theme.isDark ? 0xFF1E2120 : 0xFFFFFFFF);
            int strokeColor = isSelected ? theme.cardBorderActive : theme.cardBorder;
            card.setBackground(createRoundedCardDrawable(bgColor, strokeColor, 12 * density));

            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cLp.bottomMargin = (int) (8 * density);
            card.setLayoutParams(cLp);

            View dot = new View(activity);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            if (s.isCrashed) {
                dotBg.setColor(0xFFFF5252);
            } else if (s.isCurrent) {
                dotBg.setColor(theme.primary);
            } else {
                dotBg.setColor(theme.textTertiary);
            }
            dot.setBackground(dotBg);
            int dotSize = (int) (8 * density);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.rightMargin = (int) (12 * density);
            dot.setLayoutParams(dotLp);
            card.addView(dot);

            LinearLayout infoCol = new LinearLayout(activity);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            infoCol.setLayoutParams(icLp);

            TextView tvName = new TextView(activity);
            tvName.setText(s.getDisplayName());
            tvName.setTextSize(13.5f);
            tvName.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            tvName.setTextColor(isSelected ? theme.primary : theme.textPrimary);
            infoCol.addView(tvName);

            String sizeStr = (s.sizeBytes > 1024) ? ((s.sizeBytes / 1024) + " KB") : (s.sizeBytes + " B");
            String meta = sizeStr + (s.isCrashed ? " • Crashed" : "");
            TextView tvMeta = new TextView(activity);
            tvMeta.setText(meta);
            tvMeta.setTextSize(11f);
            tvMeta.setTextColor(s.isCrashed ? 0xFFFF5252 : theme.textSecondary);
            infoCol.addView(tvMeta);

            card.addView(infoCol);

            card.setOnClickListener(v -> {
                d.dismiss();
                listener.onSelected(s);
            });

            listLayout.addView(card);
        }

        sv.addView(listLayout);
        root.addView(sv);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            int dialogWidth = Math.min((int) (screenWidth * 0.88f), (int) (400 * density));
            int dialogHeight = Math.min((int) (screenHeight * 0.60f), (int) (450 * density));
            w.setLayout(dialogWidth, dialogHeight);
            w.setGravity(Gravity.CENTER);
        }
        d.show();
    }

    private static void showClearConfirmationDialog(Activity activity, Theme theme, Runnable onConfirm) {
        float density = activity.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createRoundedDrawable(theme.surface, 28 * density));
        int pad = (int) (24 * density);
        root.setPadding(pad, pad, pad, (int) (18 * density));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 28 * density);
                }
            });
            root.setClipToOutline(true);
        }

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Clear Old Sessions?");
        tvTitle.setTextSize(18.5f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(theme.textPrimary);
        root.addView(tvTitle);

        TextView tvMsg = new TextView(activity);
        tvMsg.setText("This will delete all previous session logs. The active session log will be kept.");
        tvMsg.setTextSize(14f);
        tvMsg.setTextColor(theme.textSecondary);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.setMargins(0, (int) (12 * density), 0, (int) (24 * density));
        tvMsg.setLayoutParams(msgLp);
        root.addView(tvMsg);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        int padH = (int) (16 * density);
        int padV = (int) (10 * density);

        TextView btnCancel = new TextView(activity);
        btnCancel.setText("Cancel");
        btnCancel.setTextSize(14f);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setTextColor(theme.primary);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setClickable(true);
        btnCancel.setFocusable(true);
        btnCancel.setPadding(padH, padV, padH, padV);
        btnCancel.setBackground(createRoundedDrawable(theme.surfaceContainer, 18 * density));
        btnCancel.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.setMargins(0, 0, (int) (10 * density), 0);
        actions.addView(btnCancel, cancelLp);

        TextView btnClearConfirm = new TextView(activity);
        btnClearConfirm.setText("Clear");
        btnClearConfirm.setTextSize(14f);
        btnClearConfirm.setTypeface(null, Typeface.BOLD);
        btnClearConfirm.setTextColor(theme.onPrimary);
        btnClearConfirm.setGravity(Gravity.CENTER);
        btnClearConfirm.setClickable(true);
        btnClearConfirm.setFocusable(true);
        btnClearConfirm.setPadding((int) (20 * density), padV, (int) (20 * density), padV);
        btnClearConfirm.setBackground(createRoundedDrawable(theme.primary, 18 * density));
        btnClearConfirm.setOnClickListener(v -> {
            d.dismiss();
            if (onConfirm != null) onConfirm.run();
            Toast.makeText(activity, "Cleared previous sessions", Toast.LENGTH_SHORT).show();
        });
        actions.addView(btnClearConfirm, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(actions);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min((int) (screenWidth * 0.88f), (int) (380 * density)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
        d.show();
    }

    private static void updateCrashBanner(View banner, TextView bannerText, SessionLogManager.SessionInfo session) {
        if (session != null && session.isCrashed) {
            banner.setVisibility(View.VISIBLE);
            String summary = session.crashSummary != null && !session.crashSummary.isEmpty()
                    ? session.crashSummary
                    : "Crash detected in this session";
            bannerText.setText("🚨 " + summary);
        } else {
            banner.setVisibility(View.GONE);
        }
    }

    private static void showExportOptionsDialog(Activity activity, Theme theme, SessionLogManager.SessionInfo session, String filterQuery, String levelFilter) {
        float density = activity.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createRoundedDrawable(theme.surface, 28 * density));
        int pad = (int) (24 * density);
        root.setPadding(pad, pad, pad, (int) (18 * density));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 28 * density);
                }
            });
            root.setClipToOutline(true);
        }

        boolean isFiltered = (levelFilter != null && !levelFilter.equalsIgnoreCase("ALL")) || (filterQuery != null && !filterQuery.trim().isEmpty());
        String filterDesc = SessionLogManager.getFilterDescription(levelFilter, filterQuery);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(isFiltered ? "Export Filtered Log" : "Export Log File");
        tvTitle.setTextSize(18.5f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(theme.textPrimary);
        root.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText(isFiltered ? "Active Filter: [" + filterDesc + "]\nSelect format to save into Downloads/Morphe/:" : "Select file format to save into Downloads/Morphe/:");
        tvSub.setTextSize(13f);
        tvSub.setTextColor(theme.textSecondary);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, (int) (6 * density), 0, (int) (16 * density));
        tvSub.setLayoutParams(subLp);
        root.addView(tvSub);

        // Option 1: Log file (.log)
        String logTitle = isFiltered ? "Filtered Log (.log)" : "Raw Log File (.log)";
        String logSubtitle = isFiltered ? "Saves matching lines for [" + filterDesc + "]" : "Standard log file containing complete terminal output & stack traces";
        View optLog = createOptionCard(activity, theme, "📄", logTitle, logSubtitle, v -> {
            d.dismiss();
            SessionLogManager.exportSessionToFile(activity, session, false, filterQuery, levelFilter);
        });
        root.addView(optLog);

        // Option 2: GitHub .md
        String mdTitle = isFiltered ? "Filtered GitHub Report (.md)" : "GitHub Markdown (.md)";
        String mdSubtitle = isFiltered ? "Formatted report containing filtered logs for [" + filterDesc + "]" : "Formatted report with device specs, crash summary & <details> tags";
        View optMd = createOptionCard(activity, theme, "🐙", mdTitle, mdSubtitle, v -> {
            d.dismiss();
            SessionLogManager.exportSessionToFile(activity, session, true, filterQuery, levelFilter);
        });
        LinearLayout.LayoutParams optMdLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        optMdLp.topMargin = (int) (8 * density);
        optMd.setLayoutParams(optMdLp);
        root.addView(optMd);

        // Option 3: Full Unfiltered Log (if filtered)
        if (isFiltered) {
            View optFull = createOptionCard(activity, theme, "📦", "Full Unfiltered Log (.log)",
                    "Exports complete unmodified session log without filters", v -> {
                        d.dismiss();
                        SessionLogManager.exportSessionToFile(activity, session, false, null, "ALL");
                    });
            LinearLayout.LayoutParams optFullLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            optFullLp.topMargin = (int) (8 * density);
            optFull.setLayoutParams(optFullLp);
            root.addView(optFull);
        }

        // Cancel
        TextView btnCancel = new TextView(activity);
        btnCancel.setText("Cancel");
        btnCancel.setTextSize(14f);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setTextColor(theme.primary);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setClickable(true);
        btnCancel.setFocusable(true);
        btnCancel.setPadding((int) (16 * density), (int) (10 * density), (int) (16 * density), (int) (10 * density));
        btnCancel.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.gravity = Gravity.END;
        cancelLp.topMargin = (int) (16 * density);
        root.addView(btnCancel, cancelLp);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min((int) (screenWidth * 0.90f), (int) (380 * density)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
        d.show();
    }

    private static void showShareOptionsDialog(Activity activity, Theme theme, SessionLogManager.SessionInfo session, String filterQuery, String levelFilter) {
        float density = activity.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createRoundedDrawable(theme.surface, 28 * density));
        int pad = (int) (24 * density);
        root.setPadding(pad, pad, pad, (int) (18 * density));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 28 * density);
                }
            });
            root.setClipToOutline(true);
        }

        boolean isFiltered = (levelFilter != null && !levelFilter.equalsIgnoreCase("ALL")) || (filterQuery != null && !filterQuery.trim().isEmpty());
        String filterDesc = SessionLogManager.getFilterDescription(levelFilter, filterQuery);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(isFiltered ? "Share Filtered Log" : "Share Diagnostics");
        tvTitle.setTextSize(18.5f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(theme.textPrimary);
        root.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText(isFiltered ? "Active Filter: [" + filterDesc + "]\nSelect how to share:" : "Select how to share this session:");
        tvSub.setTextSize(13f);
        tvSub.setTextColor(theme.textSecondary);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, (int) (6 * density), 0, (int) (16 * density));
        tvSub.setLayoutParams(subLp);
        root.addView(tvSub);

        // Option 1: Share .log file
        String logTitle = isFiltered ? "Share Filtered Log (.log)" : "Share Log File (.log)";
        String logSub = isFiltered ? "Sends filtered log (" + filterDesc + ") via Android share sheet" : "Sends actual .log file attachment via Android share sheet";
        View optLog = createOptionCard(activity, theme, "📄", logTitle, logSub, v -> {
            d.dismiss();
            SessionLogManager.shareSessionFile(activity, session, false, filterQuery, levelFilter);
        });
        root.addView(optLog);

        // Option 2: Share .md file
        String mdTitle = isFiltered ? "Share Filtered GitHub Report (.md)" : "Share GitHub Report (.md)";
        String mdSub = isFiltered ? "Sends filtered Markdown report (" + filterDesc + ") via share sheet" : "Sends formatted Markdown report attachment via share sheet";
        View optMd = createOptionCard(activity, theme, "🐙", mdTitle, mdSub, v -> {
            d.dismiss();
            SessionLogManager.shareSessionFile(activity, session, true, filterQuery, levelFilter);
        });
        LinearLayout.LayoutParams optMdLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        optMdLp.topMargin = (int) (8 * density);
        optMd.setLayoutParams(optMdLp);
        root.addView(optMd);

        // Option 3: Full Log (if filtered)
        if (isFiltered) {
            View optFull = createOptionCard(activity, theme, "📦", "Share Full Log (.log)",
                    "Sends complete unmodified session log file", v -> {
                        d.dismiss();
                        SessionLogManager.shareSessionFile(activity, session, false, null, "ALL");
                    });
            LinearLayout.LayoutParams optFullLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            optFullLp.topMargin = (int) (8 * density);
            optFull.setLayoutParams(optFullLp);
            root.addView(optFull);
        }

        // Cancel
        TextView btnCancel = new TextView(activity);
        btnCancel.setText("Cancel");
        btnCancel.setTextSize(14f);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setTextColor(theme.primary);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setClickable(true);
        btnCancel.setFocusable(true);
        btnCancel.setPadding((int) (16 * density), (int) (10 * density), (int) (16 * density), (int) (10 * density));
        btnCancel.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.gravity = Gravity.END;
        cancelLp.topMargin = (int) (16 * density);
        root.addView(btnCancel, cancelLp);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min((int) (screenWidth * 0.90f), (int) (380 * density)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
        d.show();
    }

    private static View createOptionCard(Activity activity, Theme theme, String emoji, String title, String subtitle, View.OnClickListener onClick) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(createRoundedCardDrawable(theme.surfaceContainer, theme.outline, 14 * density));
        int p = (int) (12 * density);
        card.setPadding(p, p, p, p);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(onClick);

        TextView tvEmoji = new TextView(activity);
        tvEmoji.setText(emoji);
        tvEmoji.setTextSize(20f);
        LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        emojiLp.rightMargin = (int) (12 * density);
        tvEmoji.setLayoutParams(emojiLp);
        card.addView(tvEmoji);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(tcLp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(theme.textPrimary);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText(subtitle);
        tvSub.setTextSize(11.5f);
        tvSub.setTextColor(theme.textSecondary);
        textCol.addView(tvSub);

        card.addView(textCol);
        return card;
    }
}
