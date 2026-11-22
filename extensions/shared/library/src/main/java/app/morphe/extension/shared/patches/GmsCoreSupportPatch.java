package app.morphe.extension.shared.patches;

import static app.morphe.extension.shared.StringRef.str;
import static app.morphe.extension.shared.requests.Route.Method.GET;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Pair;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.patches.backup.PhotosBackupHelper;
import app.morphe.extension.shared.patches.map.PhotosMapHelper;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;
import app.morphe.extension.shared.ui.CustomDialog;

@SuppressWarnings("unused")
public class GmsCoreSupportPatch {
    private static final String GOOGLE_PHOTOS_PACKAGE_NAME = "com.google.android.apps.photos";
    private static final String OFFICIAL_PHOTOS_CERT_SHA1 = "24bb24c05e47e0aefa68a58a766179d9b613a600";

    public static String getEffectivePackageSignature() {
        return OFFICIAL_PHOTOS_CERT_SHA1;
    }

    private static final String GMS_CORE_PACKAGE_NAME
            = getGmsCoreVendorGroupId() + ".android.gms";
    private static final Uri GMS_CORE_PROVIDER
            = Uri.parse("content://" + getGmsCoreVendorGroupId() + ".android.gsf.gservices/prefix");
    private static final String DONT_KILL_MY_APP_URL
            = "https://dontkillmyapp.com/";
    private static final Route DONT_KILL_MY_APP_MANUFACTURER_API
            = new Route(GET, "/api/v2/{manufacturer}.json");
    private static final String DONT_KILL_MY_APP_NAME_PARAMETER
            = "?app=MicroG";
    private static final String BUILD_MANUFACTURER
            = Build.MANUFACTURER.toLowerCase(Locale.ROOT).replace(" ", "-");

    /**
     * If a manufacturer specific page exists on DontKillMyApp.
     */
    @Nullable
    private static volatile Boolean DONT_KILL_MY_APP_MANUFACTURER_SUPPORTED;

    private static String getOriginalPackageName() {
       return null; // Modified during patching.
    }

    public static String getEffectiveOriginalPackageName() {
        String original = getOriginalPackageName();
        return original != null ? original : GOOGLE_PHOTOS_PACKAGE_NAME;
    }

    /**
     * @return If the current package name is the same as the original unpatched app.
     *         If `GmsCore support` was not included during patching, this returns true;
     */
    public static boolean isPackageNameOriginal(Context context) {
        String originalPackageName = getOriginalPackageName();
        return originalPackageName == null
                || originalPackageName.equals(context.getPackageName());
    }

    private static void open(String queryOrLink) {
        Logger.printInfo(() -> "Opening link: " + queryOrLink);

        Intent intent;
        try {
            new URL(queryOrLink);
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(queryOrLink));
        } catch (MalformedURLException e) {
            intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(SearchManager.QUERY, queryOrLink);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Utils.getContext().startActivity(intent);

        // Gracefully exit, otherwise the broken app will continue to run.
        System.exit(0);
    }

    private static void showBatteryOptimizationDialog(Activity context,
                                                      String dialogMessageRef,
                                                      String positiveButtonTextRef,
                                                      DialogInterface.OnClickListener onPositiveClickListener) {
        Utils.runOnMainThreadDelayed(() -> {
            Pair<Dialog, LinearLayout> dialogPair = CustomDialog.create(
                    context,
                    str("gms_core_dialog_title"),
                    str(dialogMessageRef),
                    null,
                    str(positiveButtonTextRef),
                    () -> onPositiveClickListener.onClick(null, 0),
                    null,
                    null,
                    null,
                    true
            );

            Dialog dialog = dialogPair.first;
            dialog.setCancelable(true);
            Utils.showDialog(context, dialog);
        }, 100);
    }

    /**
     * Injection point.
     */
    public static void checkGmsCore(Activity context) {
        try {
            try {
                PackageManager manager = context.getPackageManager();
                manager.getPackageInfo(GMS_CORE_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);
            } catch (PackageManager.NameNotFoundException exception) {
                Logger.printInfo(() -> "GmsCore was not found");
                Utils.showToastLong(str("gms_core_toast_not_installed_message"));
                open(getGmsCoreDownload());
                return;
            }

            if (isAndroidAutomotive(context) || isGooglePhotos(context)) {
                Logger.printDebug(() -> "Skipping battery optimization check (Automotive or Google Photos)");
                if (isGooglePhotos(context)) {
                    PhotosMapHelper.checkMicroGLocationPermission(context);
                }
            } else if (batteryOptimizationsEnabled(context)) {
                Logger.printInfo(() -> "GmsCore is not whitelisted from battery optimizations");

                showBatteryOptimizationDialog(context,
                        "gms_core_dialog_not_whitelisted_using_battery_optimizations_message",
                        "gms_core_dialog_continue_text",
                        (dialog, id) -> openGmsCoreDisableBatteryOptimizationsIntent(context));
                return;
            }

            if (!isGooglePhotos(context)) {
                var client = context.getContentResolver().acquireContentProviderClient(GMS_CORE_PROVIDER);
                try {
                    if (client == null) {
                        Logger.printInfo(() -> "GmsCore is not running in the background");
                        checkIfDontKillMyAppSupportsManufacturer();

                        showBatteryOptimizationDialog(context,
                                "gms_core_dialog_not_whitelisted_not_allowed_in_background_message",
                                "gms_core_dialog_open_website_text",
                                (dialog, id) -> openDontKillMyApp());
                    }
                } finally {
                    if (client != null) client.close();
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "checkGmsCore failure", ex);
        }
    }

    @SuppressLint("BatteryLife")
    private static void openGmsCoreDisableBatteryOptimizationsIntent(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.fromParts("package", GMS_CORE_PACKAGE_NAME, null));
        activity.startActivityForResult(intent, 0);
    }

    private static void checkIfDontKillMyAppSupportsManufacturer() {
        Utils.runOnBackgroundThread(() -> {
            try {
                final long start = System.currentTimeMillis();
                HttpURLConnection connection = Requester.getConnectionFromRoute(
                        DONT_KILL_MY_APP_URL, DONT_KILL_MY_APP_MANUFACTURER_API, BUILD_MANUFACTURER);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                final boolean supported = connection.getResponseCode() == 200;
                Logger.printInfo(() -> "Manufacturer is " + (supported ? "" : "NOT ")
                        + "listed on DontKillMyApp: " + BUILD_MANUFACTURER
                        + " fetch took: " + (System.currentTimeMillis() - start) + "ms");
                DONT_KILL_MY_APP_MANUFACTURER_SUPPORTED = supported;
            } catch (Exception ex) {
                Logger.printInfo(() -> "Could not check if manufacturer is listed on DontKillMyApp: "
                        + BUILD_MANUFACTURER, ex);
                DONT_KILL_MY_APP_MANUFACTURER_SUPPORTED = null;
            }
        });
    }

    private static void openDontKillMyApp() {
        final Boolean manufacturerSupported = DONT_KILL_MY_APP_MANUFACTURER_SUPPORTED;

        String manufacturerPageToOpen;
        if (manufacturerSupported == null) {
            manufacturerPageToOpen = "";
        } else if (manufacturerSupported) {
            manufacturerPageToOpen = BUILD_MANUFACTURER;
        } else {
            manufacturerPageToOpen = "general";
        }

        open(DONT_KILL_MY_APP_URL + manufacturerPageToOpen + DONT_KILL_MY_APP_NAME_PARAMETER);
    }

    private static boolean batteryOptimizationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        var powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return !powerManager.isIgnoringBatteryOptimizations(GMS_CORE_PACKAGE_NAME);
    }

    private static boolean isAndroidAutomotive(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private static boolean isGooglePhotos(Context context) {
        return GOOGLE_PHOTOS_PACKAGE_NAME.equals(getOriginalPackageName())
                || context.getPackageName().contains("photos");
    }

    private static String getGmsCoreDownload() {
        return switch (getGmsCoreVendorGroupId()) {
            case "app.revanced" -> "https://morphe.software/microg";
            default -> getGmsCoreVendorGroupId() + ".android.gms";
        };
    }

    private static String getGmsCoreVendorGroupId() {
        return "app.revanced"; // Modified during patching.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Map & Location Delegation Stubs (Delegating to PhotosMapHelper)
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean isMicroGLocationGranted(Context context) {
        return PhotosMapHelper.isMicroGLocationGranted(context);
    }

    public static boolean isMicroGLocationPromptIgnored(Context context) {
        return PhotosMapHelper.isMicroGLocationPromptIgnored(context);
    }

    public static void setMicroGLocationPromptIgnored(Context context, boolean ignored) {
        PhotosMapHelper.setMicroGLocationPromptIgnored(context, ignored);
    }

    public static void checkMicroGLocationPermission(Activity context) {
        PhotosMapHelper.checkMicroGLocationPermission(context);
    }

    public static boolean isMapStyleAvailable(Context context) {
        return PhotosMapHelper.isMapStyleAvailable(context);
    }

    public static boolean isInfoPanelMapKillswitched(boolean originalValue) {
        return PhotosMapHelper.isInfoPanelMapKillswitched(originalValue);
    }

    public static int adjustScrollPosition(int pos) {
        return PhotosMapHelper.adjustScrollPosition(pos);
    }

    public static int adjustScrollOffset(int pos, int offset) {
        return PhotosMapHelper.adjustScrollOffset(pos, offset);
    }

    public static int adjustScrollTargetPosition(int pos) {
        return PhotosMapHelper.adjustScrollTargetPosition(pos);
    }

    public static void clearMarkerSelection() {
        PhotosMapHelper.clearMarkerSelection();
    }

    public static android.location.Location getSafeLastKnownLocation(Context context) {
        return PhotosMapHelper.getSafeLastKnownLocation(context);
    }

    public static void onCurrentLocationMixinTintUpdated(Object mixinObj, boolean active) {
        PhotosMapHelper.onCurrentLocationMixinTintUpdated(mixinObj, active);
    }

    public static void initMapLocation(Object mixinObj) {
        PhotosMapHelper.initMapLocation(mixinObj);
    }

    public static void handleCurrentLocation(Object currentLocMixinObj) {
        PhotosMapHelper.handleCurrentLocation(currentLocMixinObj);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Backup Delegation Stubs (Delegating to PhotosBackupHelper)
    // ─────────────────────────────────────────────────────────────────────────

    public static final String BACKUP_TAG = PhotosBackupHelper.BACKUP_TAG;

    public static void logBackup(String message) {
        PhotosBackupHelper.logBackup(message);
    }

    public static void logBackupError(String message, Throwable t) {
        PhotosBackupHelper.logBackupError(message, t);
    }

    public static Object sanitizeLudh(Object ludhObj) {
        return PhotosBackupHelper.sanitizeLudh(ludhObj);
    }

    public static Object sanitizeLudh(Object ludhObj, Context context) {
        return PhotosBackupHelper.sanitizeLudh(ludhObj, context);
    }

    public static Object sanitizeGmsBackupSettings(Object gmsSettings, Context context) {
        return PhotosBackupHelper.sanitizeGmsBackupSettings(gmsSettings, context);
    }

    public static int resolveRegisteredAccountId(Context context) {
        return PhotosBackupHelper.resolveRegisteredAccountId(context);
    }

    public static int resolveRegisteredAccountId(Context context, int currentId) {
        return PhotosBackupHelper.resolveRegisteredAccountId(context, currentId);
    }

    public static int resolveRegisteredAccountId(Context context, Object accountStore) {
        return PhotosBackupHelper.resolveRegisteredAccountId(context, accountStore);
    }

    public static int resolveRegisteredAccountFromEmail(String gmsEmail, Object accountStore, Context context) {
        return PhotosBackupHelper.resolveRegisteredAccountFromEmail(gmsEmail, accountStore, context);
    }

    public static int resolveBackupAccount(int currentId, String gmsEmail, Object accountStore, Context context) {
        return PhotosBackupHelper.resolveBackupAccount(currentId, gmsEmail, accountStore, context);
    }

    public static void onAutoBackupSettingsActivityAttachBinder(Activity activity) {
        PhotosBackupHelper.onAutoBackupSettingsActivityAttachBinder(activity);
    }

    public static Object onLuvhAEntry(Object ludhObj, Boolean isConnected) {
        return PhotosBackupHelper.onLuvhAEntry(ludhObj, isConnected);
    }

    public static Object onLuvhAReturn(Object luvk) {
        return PhotosBackupHelper.onLuvhAReturn(luvk);
    }

    public static boolean onCheckBackupAccountLinked(boolean originalVal, Object luslObj) {
        return PhotosBackupHelper.onCheckBackupAccountLinked(originalVal, luslObj);
    }
}
