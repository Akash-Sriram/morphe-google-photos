package app.morphe.extension.shared.patches.toolkit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.patches.PhenotypeFlagManager.MaterialVectorDrawable;

public class ToolkitWebViewDialog {
    // Accounts login URL with continue redirect directly to photos.google.com
    private static final String LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?service=lh2&continue=https://photos.google.com/";
    private static final String PHOTOS_WEB_URL = "https://photos.google.com/";

    // Standard Desktop Chrome User-Agent forces full Web UI (navigation bar with GPTK button)
    private static final String DESKTOP_CHROME_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    // Real device mobile User-Agent (exact Android release & model, zero "; wv", zero "Version/4.0")
    private static String getRealMobileUserAgent() {
        String release = Build.VERSION.RELEASE;
        String model = Build.MODEL;
        return "Mozilla/5.0 (Linux; Android " + release + "; " + model + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
    }

    private static String cachedScript = null;

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        activity.runOnUiThread(() -> {
            try {
                Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF131314));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                        window.setStatusBarColor(0xFF1E1F20);
                        window.setNavigationBarColor(0xFF1E1F20);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        WindowManager.LayoutParams lp = window.getAttributes();
                        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                        window.setAttributes(lp);
                    }
                }

                float density = activity.getResources().getDisplayMetrics().density;

                // Dynamically resolve status bar / cutout height to protect against notch overlay
                int statusBarHeight = 0;
                int resId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
                if (resId > 0) {
                    statusBarHeight = activity.getResources().getDimensionPixelSize(resId);
                }
                if (statusBarHeight <= 0) {
                    statusBarHeight = (int) (24 * density);
                }

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                root.setBackgroundColor(0xFF131314);
                // Apply top padding strictly matching status bar cutout height
                root.setPadding(0, statusBarHeight, 0, 0);

                // --- Top Bar ---
                LinearLayout topBar = new LinearLayout(activity);
                topBar.setOrientation(LinearLayout.HORIZONTAL);
                topBar.setGravity(Gravity.CENTER_VERTICAL);
                int topBarHeight = (int) (56 * density);
                topBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topBarHeight));
                topBar.setBackgroundColor(0xFF1E1F20);
                topBar.setPadding((int) (8 * density), 0, (int) (12 * density), 0);

                // Close Button
                ImageView btnClose = new ImageView(activity);
                btnClose.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_CLOSE, 0xFFE3E3E3));
                int btnSize = (int) (44 * density);
                LinearLayout.LayoutParams btnCloseLp = new LinearLayout.LayoutParams(btnSize, btnSize);
                btnClose.setLayoutParams(btnCloseLp);
                btnClose.setPadding((int) (10 * density), (int) (10 * density), (int) (10 * density), (int) (10 * density));
                btnClose.setClickable(true);
                btnClose.setFocusable(true);
                GradientDrawable btnRipple = new GradientDrawable();
                btnRipple.setCornerRadius(22 * density);
                btnRipple.setColor(Color.TRANSPARENT);
                btnClose.setBackground(btnRipple);
                btnClose.setOnClickListener(v -> dialog.dismiss());
                topBar.addView(btnClose);

                // Title + Subtitle
                LinearLayout titleBox = new LinearLayout(activity);
                titleBox.setOrientation(LinearLayout.VERTICAL);
                titleBox.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tbLp.setMargins((int) (8 * density), 0, 0, 0);
                titleBox.setLayoutParams(tbLp);

                TextView tvTitle = new TextView(activity);
                tvTitle.setText("Google Photos Toolkit");
                tvTitle.setTextSize(16f);
                tvTitle.setTextColor(0xFFE3E3E3);
                tvTitle.setTypeface(null, Typeface.BOLD);
                titleBox.addView(tvTitle);

                TextView tvSub = new TextView(activity);
                tvSub.setText("xob0t GPTK Web Client");
                tvSub.setTextSize(11.5f);
                tvSub.setTextColor(0xFF8E918F);
                titleBox.addView(tvSub);

                topBar.addView(titleBox);

                // Sign In / Switch Account Button
                TextView btnLogin = new TextView(activity);
                btnLogin.setText("Sign In");
                btnLogin.setTextSize(13f);
                btnLogin.setTextColor(0xFFFFFFFF);
                btnLogin.setTypeface(null, Typeface.BOLD);
                btnLogin.setGravity(Gravity.CENTER);
                btnLogin.setClickable(true);
                btnLogin.setFocusable(true);
                int padH = (int) (14 * density);
                int padV = (int) (6 * density);
                btnLogin.setPadding(padH, padV, padH, padV);
                GradientDrawable btnLoginBg = new GradientDrawable();
                btnLoginBg.setCornerRadius(16 * density);
                btnLoginBg.setColor(0xFF0B57D0); // Google M3 Blue
                btnLogin.setBackground(btnLoginBg);
                LinearLayout.LayoutParams btnLoginLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btnLoginLp.setMargins(0, 0, (int) (8 * density), 0);
                btnLogin.setLayoutParams(btnLoginLp);
                topBar.addView(btnLogin);

                // Open in External Browser Button
                ImageView btnBrowser = new ImageView(activity);
                btnBrowser.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_EXPORT, 0xFFE3E3E3));
                LinearLayout.LayoutParams btnBrowserLp = new LinearLayout.LayoutParams(btnSize, btnSize);
                btnBrowser.setLayoutParams(btnBrowserLp);
                btnBrowser.setPadding((int) (11 * density), (int) (11 * density), (int) (11 * density), (int) (11 * density));
                btnBrowser.setClickable(true);
                btnBrowser.setFocusable(true);
                btnBrowser.setOnClickListener(v -> {
                    try {
                        android.content.Intent intent = new android.content.Intent(
                                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PHOTOS_WEB_URL));
                        activity.startActivity(intent);
                    } catch (Throwable t) {
                        Toast.makeText(activity, "Cannot open browser: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
                topBar.addView(btnBrowser);

                // Refresh Button
                ImageView btnRefresh = new ImageView(activity);
                btnRefresh.setImageDrawable(new MaterialVectorDrawable(MaterialVectorDrawable.TYPE_SYNC, 0xFFE3E3E3));
                LinearLayout.LayoutParams btnRefLp = new LinearLayout.LayoutParams(btnSize, btnSize);
                btnRefresh.setLayoutParams(btnRefLp);
                btnRefresh.setPadding((int) (11 * density), (int) (11 * density), (int) (11 * density), (int) (11 * density));
                btnRefresh.setClickable(true);
                btnRefresh.setFocusable(true);
                topBar.addView(btnRefresh);

                root.addView(topBar);

                // Progress Bar
                ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (3 * density)));
                progressBar.setMax(100);
                progressBar.setProgress(0);
                root.addView(progressBar);

                // Content Frame
                FrameLayout contentFrame = new FrameLayout(activity);
                contentFrame.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                WebView webView = setupWebView(activity, progressBar);
                contentFrame.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                btnLogin.setOnClickListener(v -> {
                    // Switch to real device Mobile UA before navigating to Google login
                    webView.getSettings().setUserAgentString(getRealMobileUserAgent());
                    webView.loadUrl(LOGIN_URL);
                });
                btnRefresh.setOnClickListener(v -> webView.reload());

                root.addView(contentFrame);

                dialog.setContentView(root);
                dialog.setOnDismissListener(d -> {
                    try {
                        webView.stopLoading();
                        webView.loadUrl("about:blank");
                        webView.destroy();
                    } catch (Throwable ignored) {}
                });

                dialog.show();
                webView.loadUrl(PHOTOS_WEB_URL);

            } catch (Throwable t) {
                Logger.printException(() -> "Error showing Toolkit WebView Dialog", t);
                Toast.makeText(activity, "Could not open Toolkit: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static WebView setupWebView(Activity activity, ProgressBar progressBar) {
        WebView webView = new WebView(activity);
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Start with Desktop Chrome User Agent for photos.google.com
        settings.setUserAgentString(DESKTOP_CHROME_USER_AGENT);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String url = request.getUrl().toString();
                    adjustUserAgentForUrl(view, url);
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                adjustUserAgentForUrl(view, url);
            }

            private void adjustUserAgentForUrl(WebView view, String url) {
                if (url == null) return;
                WebSettings s = view.getSettings();
                if (url.contains("accounts.google.com")) {
                    // Real device mobile UA for Google Accounts authentication
                    String mobileUa = getRealMobileUserAgent();
                    if (!mobileUa.equals(s.getUserAgentString())) {
                        s.setUserAgentString(mobileUa);
                    }
                } else if (url.contains("photos.google.com")) {
                    // Desktop Chrome UA for Google Photos Toolkit UI
                    if (!DESKTOP_CHROME_USER_AGENT.equals(s.getUserAgentString())) {
                        s.setUserAgentString(DESKTOP_CHROME_USER_AGENT);
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }
                if (url != null && url.startsWith("https://photos.google.com")) {
                    injectToolkitScript(activity, view);
                }
            }
        });

        return webView;
    }

    private static void injectToolkitScript(Context context, WebView webView) {
        try {
            String script = loadToolkitScript(context);
            if (script == null || script.trim().isEmpty()) {
                Logger.printException(() -> "Toolkit userscript could not be loaded from assets");
                return;
            }

            // Tampermonkey shims + Userscript
            String injectionPayload =
                    "(function() {" +
                    "  if (window.__gptk_injected) return;" +
                    "  window.unsafeWindow = window;" +
                    "  window.GM_registerMenuCommand = function(name, cb) {};" +
                    "  try {" +
                    script + "\n" +
                    "    window.__gptk_injected = true;" +
                    "    console.log('[Morphe] Google Photos Toolkit successfully injected');" +
                    "  } catch(e) {" +
                    "    console.error('[Morphe] Error running GPTK script:', e);" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(injectionPayload, value -> {
                Logger.printInfo(() -> "Google Photos Toolkit script injection evaluated: " + value);
            });
        } catch (Throwable t) {
            Logger.printException(() -> "Failed to inject Google Photos Toolkit script", t);
        }
    }

    private static synchronized String loadToolkitScript(Context context) {
        if (cachedScript != null) return cachedScript;
        try (InputStream is = context.getAssets().open("toolkit/google_photos_toolkit.user.js");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            cachedScript = sb.toString();
            return cachedScript;
        } catch (Throwable t) {
            Logger.printException(() -> "Error reading google_photos_toolkit.user.js from assets", t);
            return null;
        }
    }
}
