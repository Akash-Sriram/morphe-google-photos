package app.morphe.extension.shared.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

import app.morphe.extension.shared.Utils;

/**
 * Material 3 styled on-demand download dialog that matches Google Photos' design system.
 */
public class MorpheModelDownloadDialog {
    public interface DialogListener {
        void onCancel();
    }

    private final Activity activity;
    private final Dialog dialog;
    private final ProgressBar progressBar;
    private final TextView statusView;
    private final TextView percentView;
    private final TextView sizeView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isCancelled = false;

    public MorpheModelDownloadDialog(Activity activity, String featureName, DialogListener listener) {
        this.activity = activity;
        this.dialog = new Dialog(activity);
        this.dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.dialog.setCancelable(false);

        boolean isDark = Utils.isDarkModeEnabled();
        int surfaceColor = isDark ? Color.parseColor("#202124") : Color.parseColor("#FFFFFF");
        int primaryText = isDark ? Color.parseColor("#E8EAED") : Color.parseColor("#1F1F1F");
        int secondaryText = isDark ? Color.parseColor("#9AA0A6") : Color.parseColor("#5F6368");
        int accentColor = isDark ? Color.parseColor("#8AB4F8") : Color.parseColor("#1A73E8");
        int trackColor = isDark ? Color.parseColor("#3C4043") : Color.parseColor("#E0E2EC");

        // Root container with Material 3 28dp rounded corners
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Dim.dp24, Dim.dp24, Dim.dp24, Dim.dp20);

        ShapeDrawable background = new ShapeDrawable(new RoundRectShape(
                Dim.roundedCorners(28), null, null));
        background.getPaint().setColor(surfaceColor);
        root.setBackground(background);

        // Title
        TextView titleView = new TextView(activity);
        titleView.setText("Downloading " + featureName);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleView.setTextColor(primaryText);
        root.addView(titleView);

        // Subtitle / Status
        statusView = new TextView(activity);
        statusView.setText("Preparing AI model for on-device processing...");
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusView.setTextColor(secondaryText);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Dim.dp8;
        statusParams.bottomMargin = Dim.dp16;
        statusView.setLayoutParams(statusParams);
        root.addView(statusView);

        // Horizontal Progress Bar
        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(ColorStateList.valueOf(accentColor));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(trackColor));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Dim.dp8);
        progressParams.bottomMargin = Dim.dp8;
        progressBar.setLayoutParams(progressParams);
        root.addView(progressBar);

        // Details row: Percentage & File Size
        LinearLayout detailsRow = new LinearLayout(activity);
        detailsRow.setOrientation(LinearLayout.HORIZONTAL);

        percentView = new TextView(activity);
        percentView.setText("0%");
        percentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        percentView.setTextColor(secondaryText);
        detailsRow.addView(percentView);

        sizeView = new TextView(activity);
        sizeView.setText("");
        sizeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sizeView.setTextColor(secondaryText);
        sizeView.setGravity(Gravity.END);
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        sizeView.setLayoutParams(sizeParams);
        detailsRow.addView(sizeView);

        root.addView(detailsRow);

        // Actions: Cancel Button
        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams buttonRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonRowParams.topMargin = Dim.dp16;
        buttonRow.setLayoutParams(buttonRowParams);

        Button cancelButton = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        cancelButton.setText(android.R.string.cancel);
        cancelButton.setTextColor(accentColor);
        cancelButton.setAllCaps(false);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cancelButton.setOnClickListener(v -> {
            isCancelled = true;
            if (listener != null) {
                listener.onCancel();
            }
            dismiss();
        });
        buttonRow.addView(cancelButton);
        root.addView(buttonRow);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min((int) (Dim.SCREEN_WIDTH * 0.88), Dim.dp(360));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void show() {
        mainHandler.post(() -> {
            try {
                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                    dialog.show();
                }
            } catch (Throwable ignored) {}
        });
    }

    public void updateProgress(int percent, long currentBytes, long totalBytes, String currentFileStatus) {
        mainHandler.post(() -> {
            try {
                if (progressBar != null) {
                    progressBar.setProgress(percent);
                }
                if (percentView != null) {
                    percentView.setText(percent + "%");
                }
                if (sizeView != null && totalBytes > 0) {
                    double currentMb = currentBytes / (1024.0 * 1024.0);
                    double totalMb = totalBytes / (1024.0 * 1024.0);
                    sizeView.setText(String.format(Locale.US, "%.1f MB / %.1f MB", currentMb, totalMb));
                }
                if (statusView != null && currentFileStatus != null) {
                    statusView.setText(currentFileStatus);
                }
            } catch (Throwable ignored) {}
        });
    }

    public void setComplete(String message) {
        mainHandler.post(() -> {
            try {
                if (statusView != null) statusView.setText(message);
                if (progressBar != null) progressBar.setProgress(100);
                if (percentView != null) percentView.setText("100%");
            } catch (Throwable ignored) {}
        });
    }

    public void dismiss() {
        mainHandler.post(() -> {
            try {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            } catch (Throwable ignored) {}
        });
    }
}
