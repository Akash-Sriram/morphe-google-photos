package app.morphe.extension.shared.patches.map;

import static app.morphe.extension.shared.StringRef.str;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Pair;
import android.widget.LinearLayout;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.SafeLog;
import app.morphe.extension.shared.ui.CustomDialog;

@SuppressWarnings("unused")
public class PhotosMapHelper {
    private static final String GMS_CORE_PACKAGE_NAME = "app.revanced.android.gms";
    private static final String PREF_IGNORE_MICROG_LOCATION_PROMPT = "morphe_ignore_microg_location_prompt";
    private static final String MORPHE_PREFERENCES_NAME = "morphe_preferences";

    private static volatile Boolean sMapStyleAvailable = null;

    private static volatile Object sAttachedMapObj;
    private static volatile LocationSourceBinder sLocationSource;
    private static volatile android.location.Location sLastLocation;
    private static android.location.LocationListener sContinuousListener;
    private static volatile Object sMapExploreController;
    private static volatile java.lang.ref.WeakReference<Object> sCurrentMixinRef;
    private static final AtomicBoolean sIsLocatingAnimation = new AtomicBoolean(false);
    private static volatile Object sAppCameraIdleListener;
    private static volatile Object sAppMapClickListener;
    private static volatile Object sGoogleMapDelegate;
    private static final Set<Object> sWrappedMapObjects = Collections.newSetFromMap(new WeakHashMap<>());
    private static final AtomicBoolean sInitialSelectionCleared = new AtomicBoolean(false);

    private static volatile Class<?> sCachedCameraUpdateFactoryClass = null;
    private static final AtomicBoolean sDumpedGoogleMapImplFields = new AtomicBoolean(false);

    private static final android.os.Handler sCameraIdleHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final Runnable sCameraIdleRunnable = () -> doTriggerGmsCameraIdle();

    private interface LocationApplier {
        void onLocation(android.location.Location loc);
    }

    public static class LocationSourceBinder extends android.os.Binder implements android.os.IInterface {
        private volatile android.os.IBinder listenerBinder;

        public LocationSourceBinder() {
            attachInterface(this, "com.google.android.gms.maps.internal.ILocationSourceDelegate");
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString("com.google.android.gms.maps.internal.ILocationSourceDelegate");
                return true;
            }
            if (code == 1 || code == 0) { // activate(IOnLocationChangeListener listener)
                data.enforceInterface("com.google.android.gms.maps.internal.ILocationSourceDelegate");
                android.os.IBinder b = data.readStrongBinder();
                this.listenerBinder = b;
                SafeLog.d("MorpheLocation", "LocationSourceBinder: activated with listener " + b + " (code " + code + ")");
                android.location.Location loc = sLastLocation;
                if (loc == null) {
                    loc = getSafeLastKnownLocation(Utils.getContext());
                    if (loc != null) sLastLocation = loc;
                }
                if (loc != null) {
                    pushLocation(loc);
                }
                if (reply != null) reply.writeNoException();
                return true;
            } else if (code == 2) { // deactivate()
                data.enforceInterface("com.google.android.gms.maps.internal.ILocationSourceDelegate");
                SafeLog.d("MorpheLocation", "LocationSourceBinder: deactivate() received, cleared listener");
                this.listenerBinder = null;
                if (reply != null) reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        public void pushLocation(android.location.Location loc) {
            android.os.IBinder b = this.listenerBinder;
            if (b == null || loc == null) return;
            if (!b.isBinderAlive() || !b.pingBinder()) {
                this.listenerBinder = null;
                return;
            }
            android.location.Location pushLoc = new android.location.Location(loc);
            if (pushLoc.getProvider() == null || pushLoc.getProvider().isEmpty()) {
                pushLoc.setProvider("fused");
            }
            if (pushLoc.getTime() == 0) {
                pushLoc.setTime(System.currentTimeMillis());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                if (pushLoc.getElapsedRealtimeNanos() == 0) {
                    pushLoc.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
                }
            }
            if (!pushLoc.hasAccuracy() || pushLoc.getAccuracy() <= 0.0f) {
                pushLoc.setAccuracy(15.0f);
            }

            for (int code : new int[]{1, 2}) {
                if (!b.isBinderAlive()) {
                    this.listenerBinder = null;
                    break;
                }
                android.os.Parcel p = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                try {
                    p.writeInterfaceToken("com.google.android.gms.maps.internal.IOnLocationChangeListener");
                    p.writeInt(1); // indicates Parcelable exists
                    pushLoc.writeToParcel(p, 0);
                    boolean sent = b.transact(code, p, reply, 0);
                    if (sent) {
                        reply.readException();
                        SafeLog.d("MorpheLocation", "Native Location pushed via transact " + code + ": "
                                + pushLoc.getLatitude() + ", " + pushLoc.getLongitude()
                                + " (acc=" + pushLoc.getAccuracy() + "m)");
                        break;
                    }
                } catch (Throwable t) {
                    if (t instanceof android.os.DeadObjectException || !b.isBinderAlive()) {
                        this.listenerBinder = null;
                        break;
                    }
                } finally {
                    p.recycle();
                    reply.recycle();
                }
            }
        }
    }

    public static boolean isMicroGLocationGranted(Context context) {
        if (context == null) context = Utils.getContext();
        if (context == null) return true;
        try {
            PackageManager pm = context.getPackageManager();
            boolean fine = pm.checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, GMS_CORE_PACKAGE_NAME) == PackageManager.PERMISSION_GRANTED;
            boolean coarse = pm.checkPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, GMS_CORE_PACKAGE_NAME) == PackageManager.PERMISSION_GRANTED;
            return fine || coarse;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isMicroGLocationPromptIgnored(Context context) {
        if (context == null) context = Utils.getContext();
        if (context == null) return false;
        try {
            return context.getSharedPreferences(MORPHE_PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .getBoolean(PREF_IGNORE_MICROG_LOCATION_PROMPT, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setMicroGLocationPromptIgnored(Context context, boolean ignored) {
        if (context == null) context = Utils.getContext();
        if (context == null) return;
        try {
            context.getSharedPreferences(MORPHE_PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_IGNORE_MICROG_LOCATION_PROMPT, ignored)
                    .apply();
        } catch (Throwable ignoredException) {}
    }

    public static void checkMicroGLocationPermission(Activity context) {
        try {
            if (isMicroGLocationGranted(context)) {
                return;
            }
            if (isMicroGLocationPromptIgnored(context)) {
                Logger.printInfo(() -> "MicroG location permission not granted, but user previously selected Ignore");
                return;
            }

            Utils.runOnMainThreadDelayed(() -> {
                try {
                    if (context.isFinishing()) return;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && context.isDestroyed()) return;

                    Pair<Dialog, LinearLayout> dialogPair = CustomDialog.create(
                            context,
                            "MicroG Location Permission",
                            "MicroG requires Location permission to display photo location maps and extra info.\n\nWithout this permission, location maps in Google Photos are temporarily disabled to prevent crashes.",
                            null,
                            "Open Settings",
                            () -> {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(Uri.fromParts("package", GMS_CORE_PACKAGE_NAME, null));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                } catch (Throwable t) {
                                    Logger.printException(() -> "Failed to open MicroG settings", t);
                                }
                            },
                            null,
                            "Ignore",
                            () -> {
                                Logger.printInfo(() -> "User selected Ignore on MicroG location prompt");
                                setMicroGLocationPromptIgnored(context, true);
                            },
                            true
                    );

                    Dialog dialog = dialogPair.first;
                    dialog.setCancelable(true);
                    Utils.showDialog(context, dialog);
                } catch (Throwable t) {
                    Logger.printException(() -> "Failed to display MicroG location permission dialog", t);
                }
            }, 1000);
        } catch (Throwable t) {
            Logger.printException(() -> "checkMicroGLocationPermission error", t);
        }
    }

    public static boolean isMapStyleAvailable(Context context) {
        if (sMapStyleAvailable != null) {
            return sMapStyleAvailable;
        }
        if (context == null) context = Utils.getContext();
        if (context == null) return false;
        try {
            Context gmsContext = context.createPackageContext(GMS_CORE_PACKAGE_NAME, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            if (gmsContext != null) {
                try (java.io.InputStream is = gmsContext.getAssets().open("style-microg-normal-mapbox.json")) {
                    sMapStyleAvailable = true;
                    return true;
                } catch (Throwable ignored) {}

                try (java.io.InputStream is = gmsContext.getAssets().open("style-microg-normal-openfreemap.json")) {
                    sMapStyleAvailable = true;
                    return true;
                } catch (Throwable ignored) {}

                try (java.io.InputStream is = gmsContext.getAssets().open("style-microg-normal-stadia.json")) {
                    sMapStyleAvailable = true;
                    return true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Logger.printException(() -> "isMapStyleAvailable check failed", t);
        }
        sMapStyleAvailable = false;
        return false;
    }

    public static boolean isInfoPanelMapKillswitched(boolean originalValue) {
        if (originalValue) {
            return true;
        }
        Context ctx = Utils.getContext();
        if (!isMicroGLocationGranted(ctx)) {
            Logger.printInfo(() -> "MicroG lacks location permission: killswitching Info Panel map to prevent crash");
            return true;
        }
        if (!isMapStyleAvailable(ctx)) {
            Logger.printInfo(() -> "MicroG GmsCore lacks Mapbox style assets: killswitching Info Panel map to prevent crash (#67)");
            return true;
        }
        return false;
    }

    public static int adjustScrollPosition(int pos) {
        if (pos <= 1) {
            SafeLog.d("MorpheLocation", "adjustScrollPosition: adjusting pos " + pos + " to 0 (top header)");
            return 0;
        }
        return pos;
    }

    public static int adjustScrollOffset(int pos, int offset) {
        if (pos <= 1) {
            SafeLog.d("MorpheLocation", "adjustScrollOffset: resetting offset " + offset + " to 0 for pos " + pos);
            return 0;
        }
        return offset;
    }

    public static int adjustScrollTargetPosition(int pos) {
        return adjustScrollPosition(pos);
    }

    public static void clearMarkerSelection() {
        // No-op: Marker selection is natively managed by Google Photos.
    }

    public static android.location.Location getSafeLastKnownLocation(Context context) {
        if (context == null) context = Utils.getContext();
        if (context == null) return sLastLocation;
        try {
            android.location.LocationManager lm = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        if (lm.isProviderEnabled(android.location.LocationManager.FUSED_PROVIDER)) {
                            android.location.Location l = lm.getLastKnownLocation(android.location.LocationManager.FUSED_PROVIDER);
                            if (l != null) return l;
                        }
                    } catch (Throwable ignored) {}
                }
                try {
                    if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                        android.location.Location l = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                        if (l != null) return l;
                    }
                } catch (Throwable ignored) {}
                try {
                    if (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                        android.location.Location l = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                        if (l != null) return l;
                    }
                } catch (Throwable ignored) {}
                try {
                    android.location.Location l = lm.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER);
                    if (l != null) return l;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return sLastLocation;
    }

    public static void onCurrentLocationMixinTintUpdated(Object mixinObj, boolean active) {
        if (!active) {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement elem : stack) {
                if ("onClick".equals(elem.getMethodName())) {
                    SafeLog.d("MorpheLocation", "CurrentLocationMixin.b(false) invoked from onClick! Redirecting to handleCurrentLocation()");
                    handleCurrentLocation(mixinObj);
                    return;
                }
            }
        }
    }

    public static void initMapLocation(Object mixinObj) {
        if (mixinObj == null) return;
        try {
            if (!isMicroGLocationGranted(Utils.getContext())) {
                SafeLog.d("MorpheLocation", "initMapLocation skipped: MicroG location permission not granted");
                return;
            }
            SafeLog.d("MorpheLocation", "initMapLocation called for: " + mixinObj.getClass().getName());

            if (sContinuousListener != null) {
                try {
                    Context ctx = Utils.getContext();
                    if (ctx != null) {
                        android.location.LocationManager lm = (android.location.LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
                        if (lm != null) {
                            lm.removeUpdates(sContinuousListener);
                            SafeLog.d("MorpheLocation", "initMapLocation: unregistered previous sContinuousListener");
                        }
                    }
                } catch (Throwable ignored) {}
                sContinuousListener = null;
            }

            sAttachedMapObj = null;
            sLocationSource = null;
            sGoogleMapDelegate = null;
            sMapExploreController = null;
            sCameraIdleHandler.removeCallbacksAndMessages(null);
            sIsLocatingAnimation.set(false);

            sCurrentMixinRef = new java.lang.ref.WeakReference<>(mixinObj);
            sInitialSelectionCleared.set(false);
            if (sMapExploreController == null) {
                sMapExploreController = findMapExploreController(mixinObj);
            }

            android.location.Location initLoc = getSafeLastKnownLocation(Utils.getContext());
            if (initLoc != null) {
                sLastLocation = initLoc;
                SafeLog.d("MorpheLocation", "initMapLocation: seeded sLastLocation=" + initLoc.getLatitude() + "," + initLoc.getLongitude());
            }

            final Object finalMixin = mixinObj;
            Runnable pollRunnable = new Runnable() {
                int attempts = 0;
                @Override
                public void run() {
                    try {
                        Object mapObj = findMapObject(finalMixin);
                        if (mapObj != null) {
                            SafeLog.d("MorpheLocation", "initMapLocation: mapObj found on attempt " + attempts);
                            wrapGoogleMapIfNeeded(mapObj);
                            android.os.IBinder mapBinder = extractMapBinder(mapObj);
                            if (mapBinder != null) {
                                if (sLocationSource == null) {
                                    sLocationSource = new LocationSourceBinder();
                                }
                                if (sAttachedMapObj != mapObj || sLocationSource.listenerBinder == null) {
                                    sAttachedMapObj = mapObj;
                                    setLocationSource(mapBinder, sLocationSource);
                                }
                            }
                        } else if (attempts < 8) {
                            attempts++;
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 300);
                        }
                    } catch (Throwable t) {
                        SafeLog.e("MorpheLocation", "initMapLocation error in poll", t);
                    }
                }
            };
            pollRunnable.run();
        } catch (Throwable t) {
            SafeLog.e("MorpheLocation", "initMapLocation outer exception", t);
        }
    }

    @SuppressLint({"MissingPermission", "NewApi"})
    public static void handleCurrentLocation(Object currentLocMixinObj) {
        if (currentLocMixinObj == null) return;
        try {
            SafeLog.d("MorpheLocation", "handleCurrentLocation triggered with: " + currentLocMixinObj.getClass().getName());
            Class<?> clazz = currentLocMixinObj.getClass();
            Context context = null;
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                if (Context.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    context = (Context) f.get(currentLocMixinObj);
                    break;
                }
            }
            if (context == null) {
                SafeLog.e("MorpheLocation", "Context not found on mixin");
                return;
            }

            final Context finalContext = context;
            final Object finalMixin = currentLocMixinObj;

            boolean hasFine = context.checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean hasCoarse = context.checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!hasFine && !hasCoarse) {
                SafeLog.w("MorpheLocation", "Location permission not granted to Google Photos");
                Activity activity = null;
                Context cur = context;
                while (cur instanceof android.content.ContextWrapper) {
                    if (cur instanceof Activity) {
                        activity = (Activity) cur;
                        break;
                    }
                    cur = ((android.content.ContextWrapper) cur).getBaseContext();
                }
                if (activity != null) {
                    final Activity finalAct = activity;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        try {
                            android.widget.Toast.makeText(finalAct, "Location permission required for map", android.widget.Toast.LENGTH_SHORT).show();
                            if (Build.VERSION.SDK_INT >= 23) {
                                finalAct.requestPermissions(new String[]{
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                }, 1001);
                            }
                        } catch (Throwable t) {
                            SafeLog.e("MorpheLocation", "Failed to request location permissions", t);
                        }
                    });
                }
                return;
            }

            if (!isMicroGLocationGranted(context)) {
                SafeLog.w("MorpheLocation", "Location permission not granted to MicroG");
                Activity activity = null;
                Context cur = context;
                while (cur instanceof android.content.ContextWrapper) {
                    if (cur instanceof Activity) {
                        activity = (Activity) cur;
                        break;
                    }
                    cur = ((android.content.ContextWrapper) cur).getBaseContext();
                }
                if (activity != null) {
                    checkMicroGLocationPermission(activity);
                }
                return;
            }

            android.location.LocationManager lm = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                SafeLog.e("MorpheLocation", "LocationManager is null");
                return;
            }

            boolean isGpsEnabled = false;
            boolean isNetworkEnabled = false;
            boolean isFusedEnabled = false;
            try {
                isGpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
            } catch (Exception ignored) {}
            try {
                isNetworkEnabled = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
            } catch (Exception ignored) {}
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    isFusedEnabled = lm.isProviderEnabled(android.location.LocationManager.FUSED_PROVIDER);
                }
            } catch (Exception ignored) {}

            if (!isGpsEnabled && !isNetworkEnabled && !isFusedEnabled) {
                SafeLog.w("MorpheLocation", "Location providers are disabled on device");
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        android.widget.Toast.makeText(finalContext, "Please enable Location in device settings", android.widget.Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {}
                });
                return;
            }

            if (sContinuousListener != null) {
                try {
                    lm.removeUpdates(sContinuousListener);
                } catch (Throwable ignored) {}
                sContinuousListener = null;
            }

            sCurrentMixinRef = new java.lang.ref.WeakReference<>(currentLocMixinObj);
            sIsLocatingAnimation.set(true);
            if (sMapExploreController == null) {
                sMapExploreController = findMapExploreController(currentLocMixinObj);
            }

            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                if (f.getType() == boolean.class && f.getName().equals("j")) {
                    try {
                        f.setAccessible(true);
                        f.setBoolean(finalMixin, false);
                    } catch (Exception ignored) {}
                }
            }

            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                if (f.getType() == boolean.class && f.getName().equals("h")) {
                    try {
                        f.setAccessible(true);
                        f.setBoolean(finalMixin, false);
                    } catch (Exception ignored) {}
                }
            }

            final AtomicBoolean cameraAnimated = new AtomicBoolean(false);

            LocationApplier applyLocation = (loc) -> {
                if (loc == null) return;
                sLastLocation = loc;
                SafeLog.d("MorpheLocation", "Applying location: " + loc.getLatitude() + ", " + loc.getLongitude());

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        Object mapObj = findMapObject(finalMixin);
                        if (mapObj == null) {
                            SafeLog.e("MorpheLocation", "Map object not found on mixin");
                            return;
                        }
                        wrapGoogleMapIfNeeded(mapObj);

                        android.os.IBinder mapBinder = extractMapBinder(mapObj);
                        if (mapBinder != null) {
                            if (sLocationSource == null) {
                                sLocationSource = new LocationSourceBinder();
                            }
                            if (sAttachedMapObj != mapObj || sLocationSource.listenerBinder == null) {
                                sAttachedMapObj = mapObj;
                                setLocationSource(mapBinder, sLocationSource);
                            }
                            setMyLocationEnabled(mapBinder, true);
                        }
                        try {
                            for (Method m : mapObj.getClass().getMethods()) {
                                if (m.getName().equals("f") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                                    m.invoke(mapObj, true);
                                    SafeLog.d("MorpheLocation", "Invoked map.f(true)");
                                    break;
                                }
                            }
                        } catch (Throwable ignored) {}

                        if (sLocationSource != null) {
                            sLocationSource.pushLocation(loc);
                        }

                        if (cameraAnimated.compareAndSet(false, true)) {
                            if (sContinuousListener != null) {
                                try {
                                    lm.removeUpdates(sContinuousListener);
                                    SafeLog.d("MorpheLocation", "handleCurrentLocation: unregistered sContinuousListener after camera animation");
                                } catch (Throwable ignored) {}
                                sContinuousListener = null;
                            }
                            try {
                                Class<?> latLngClass = Class.forName("com.google.android.gms.maps.model.LatLng", true, clazz.getClassLoader());
                                Object latLng = latLngClass.getConstructor(double.class, double.class)
                                        .newInstance(loc.getLatitude(), loc.getLongitude());

                                Object camUpdate = createCameraUpdate(clazz.getClassLoader(), latLng, 15.0f);
                                if (camUpdate != null) {
                                    boolean animated = false;
                                    Class<?> cuClass = camUpdate.getClass();
                                    for (Method m : mapObj.getClass().getMethods()) {
                                        Class<?>[] pts = m.getParameterTypes();
                                        if (pts.length == 2 && (pts[0].isAssignableFrom(cuClass) || cuClass.isAssignableFrom(pts[0]))
                                                && (pts[1] == int.class || pts[1] == Integer.class)) {
                                            try {
                                                m.setAccessible(true);
                                                m.invoke(mapObj, camUpdate, 500);
                                                SafeLog.d("MorpheLocation", "Invoked map." + m.getName() + "(camUpdate, 500)");
                                                animated = true;
                                                break;
                                            } catch (Throwable t) {
                                                SafeLog.w("MorpheLocation", "Failed invoking " + m.getName() + "(camUpdate, 500)", t);
                                            }
                                        }
                                    }
                                    if (!animated) {
                                        for (Method m : mapObj.getClass().getMethods()) {
                                            Class<?>[] pts = m.getParameterTypes();
                                            if (pts.length == 1 && (pts[0].isAssignableFrom(cuClass) || cuClass.isAssignableFrom(pts[0]))) {
                                                try {
                                                    m.setAccessible(true);
                                                    m.invoke(mapObj, camUpdate);
                                                    SafeLog.d("MorpheLocation", "Invoked map." + m.getName() + "(camUpdate)");
                                                    animated = true;
                                                    break;
                                                } catch (Throwable t) {
                                                    SafeLog.w("MorpheLocation", "Failed invoking " + m.getName() + "(camUpdate)", t);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                SafeLog.w("MorpheLocation", "Camera animation failed", t);
                            }

                            sCameraIdleHandler.removeCallbacks(sCameraIdleRunnable);
                            sCameraIdleHandler.postDelayed(sCameraIdleRunnable, 600);

                            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                                if (f.getType() == boolean.class && f.getName().equals("h")) {
                                    try {
                                        f.setAccessible(true);
                                        f.setBoolean(finalMixin, true);
                                    } catch (Throwable ignored) {}
                                }
                            }

                            for (Method m : clazz.getDeclaredMethods()) {
                                if (m.getName().equals("b") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                                    try {
                                        m.setAccessible(true);
                                        m.invoke(finalMixin, true);
                                        SafeLog.d("MorpheLocation", "Invoked mixin.b(true)");
                                    } catch (Throwable ignored) {}
                                    break;
                                }
                            }

                            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                                if (f.getName().equals("m")) {
                                    try {
                                        f.setAccessible(true);
                                        Object fab = f.get(finalMixin);
                                        if (fab instanceof android.widget.ImageView) {
                                            android.widget.ImageView fabView = (android.widget.ImageView) fab;
                                            if (fabView.getDrawable() != null) {
                                                fabView.getDrawable().mutate().setTint(0xFF1A73E8);
                                                SafeLog.d("MorpheLocation", "Directly tinted FAB drawable to active blue");
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }

                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                sIsLocatingAnimation.set(false);
                            }, 600);
                        }
                    } catch (Throwable t) {
                        SafeLog.e("MorpheLocation", "Error applying location to map", t);
                    }
                });
            };

            android.location.Location lastLoc = null;
            if (Build.VERSION.SDK_INT >= 31 && isFusedEnabled) {
                try {
                    lastLoc = lm.getLastKnownLocation(android.location.LocationManager.FUSED_PROVIDER);
                } catch (Exception ignored) {}
            }
            if (lastLoc == null && isGpsEnabled) {
                try {
                    lastLoc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                } catch (Exception ignored) {}
            }
            if (lastLoc == null && isNetworkEnabled) {
                try {
                    lastLoc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                } catch (Exception ignored) {}
            }
            if (lastLoc == null) {
                try {
                    lastLoc = lm.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER);
                } catch (Exception ignored) {}
            }
            if (lastLoc == null) {
                lastLoc = sLastLocation;
            }

            if (lastLoc != null) {
                SafeLog.d("MorpheLocation", "Found lastKnownLocation: " + lastLoc);
                applyLocation.onLocation(lastLoc);
            }

            final boolean finalGps = isGpsEnabled;
            final boolean finalNetwork = isNetworkEnabled;
            final boolean finalFused = isFusedEnabled;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    if (sContinuousListener == null && !cameraAnimated.get()) {
                        sContinuousListener = new android.location.LocationListener() {
                            @Override
                            public void onLocationChanged(android.location.Location location) {
                                if (location == null) return;
                                sLastLocation = location;
                                LocationSourceBinder src = sLocationSource;
                                if (src != null) {
                                    src.pushLocation(location);
                                }
                                applyLocation.onLocation(location);
                                try {
                                    lm.removeUpdates(this);
                                    SafeLog.d("MorpheLocation", "sContinuousListener: received fix, removed updates");
                                } catch (Throwable ignored) {}
                                if (sContinuousListener == this) {
                                    sContinuousListener = null;
                                }
                            }
                            @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
                            @Override public void onProviderEnabled(String provider) {}
                            @Override public void onProviderDisabled(String provider) {}
                        };
                        if (Build.VERSION.SDK_INT >= 31 && finalFused) {
                            try {
                                lm.requestLocationUpdates(android.location.LocationManager.FUSED_PROVIDER, 1000L, 1.0f, sContinuousListener, android.os.Looper.getMainLooper());
                            } catch (Throwable ignored) {}
                        }
                        if (finalGps) {
                            try {
                                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000L, 1.0f, sContinuousListener, android.os.Looper.getMainLooper());
                            } catch (Throwable ignored) {}
                        }
                        if (finalNetwork) {
                            try {
                                lm.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 1000L, 1.0f, sContinuousListener, android.os.Looper.getMainLooper());
                            } catch (Throwable ignored) {}
                        }
                        SafeLog.d("MorpheLocation", "Registered single-burst LocationListener for map centering");

                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (sContinuousListener != null) {
                                try {
                                    lm.removeUpdates(sContinuousListener);
                                    SafeLog.d("MorpheLocation", "LocationListener timed out after 10s, removed updates");
                                } catch (Throwable ignored) {}
                                sContinuousListener = null;
                            }
                            sIsLocatingAnimation.set(false);
                        }, 10000);
                    }
                } catch (Throwable t) {
                    SafeLog.w("MorpheLocation", "Failed to register location updates", t);
                }
            });

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Build.VERSION.SDK_INT >= 31 && finalFused) {
                    try {
                        lm.getCurrentLocation(android.location.LocationManager.FUSED_PROVIDER, null, context.getMainExecutor(), loc -> applyLocation.onLocation(loc));
                    } catch (Exception ignored) {}
                }
                if (isGpsEnabled) {
                    try {
                        lm.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, null, context.getMainExecutor(), loc -> applyLocation.onLocation(loc));
                    } catch (Exception ignored) {}
                }
                if (isNetworkEnabled) {
                    try {
                        lm.getCurrentLocation(android.location.LocationManager.NETWORK_PROVIDER, null, context.getMainExecutor(), loc -> applyLocation.onLocation(loc));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable t) {
            SafeLog.e("MorpheLocation", "handleCurrentLocation exception", t);
        }
    }

    private static boolean isGoogleMapDelegateBinder(android.os.IBinder b) {
        if (b == null) return false;
        try {
            String desc = b.getInterfaceDescriptor();
            if (desc != null && (desc.equals("com.google.android.gms.maps.internal.IGoogleMapDelegate")
                    || desc.endsWith("IGoogleMapDelegate"))) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isValidBinderCandidate(android.os.IBinder b) {
        if (b == null) return false;
        try {
            String desc = b.getInterfaceDescriptor();
            if (desc != null && (desc.contains("UiSettings") || desc.contains("Projection")
                    || desc.contains("Panorama") || desc.contains("LocationSource"))) {
                return false;
            }
        } catch (Throwable ignored) {}
        return true;
    }

    private static android.os.IBinder extractMapBinder(Object mapObj) {
        if (mapObj == null) return null;
        try {
            android.os.IBinder fallback = null;
            for (Class<?> clazz = mapObj.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(mapObj);
                    if (val == null) continue;

                    if (val instanceof android.os.IInterface) {
                        android.os.IBinder b = ((android.os.IInterface) val).asBinder();
                        if (isGoogleMapDelegateBinder(b)) {
                            SafeLog.d("MorpheLocation", "Found IGoogleMapDelegate directly: " + b);
                            return b;
                        }
                        if (fallback == null && isValidBinderCandidate(b)) fallback = b;
                    } else if (val instanceof android.os.IBinder) {
                        android.os.IBinder b = (android.os.IBinder) val;
                        if (isGoogleMapDelegateBinder(b)) {
                            SafeLog.d("MorpheLocation", "Found IGoogleMapDelegate IBinder: " + b);
                            return b;
                        }
                        if (fallback == null && isValidBinderCandidate(b)) fallback = b;
                    } else {
                        try {
                            java.lang.reflect.Field mRemote = val.getClass().getDeclaredField("mRemote");
                            mRemote.setAccessible(true);
                            Object remoteObj = mRemote.get(val);
                            if (remoteObj instanceof android.os.IBinder) {
                                android.os.IBinder b = (android.os.IBinder) remoteObj;
                                if (isGoogleMapDelegateBinder(b)) {
                                    SafeLog.d("MorpheLocation", "Found IGoogleMapDelegate in mRemote: " + b);
                                    return b;
                                }
                                if (fallback == null && isValidBinderCandidate(b)) fallback = b;
                            }
                        } catch (Throwable ignored) {}

                        for (java.lang.reflect.Field sf : val.getClass().getDeclaredFields()) {
                            sf.setAccessible(true);
                            Object sval = sf.get(val);
                            if (sval instanceof android.os.IBinder) {
                                android.os.IBinder b = (android.os.IBinder) sval;
                                if (isGoogleMapDelegateBinder(b)) {
                                    SafeLog.d("MorpheLocation", "Found IGoogleMapDelegate in subfield: " + b);
                                    return b;
                                }
                                if (fallback == null && isValidBinderCandidate(b)) fallback = b;
                            } else if (sval instanceof android.os.IInterface) {
                                android.os.IBinder b = ((android.os.IInterface) sval).asBinder();
                                if (isGoogleMapDelegateBinder(b)) {
                                    SafeLog.d("MorpheLocation", "Found IGoogleMapDelegate in subfield IInterface: " + b);
                                    return b;
                                }
                                if (fallback == null && isValidBinderCandidate(b)) fallback = b;
                            }
                        }
                    }
                }
            }
            if (fallback != null) {
                SafeLog.w("MorpheLocation", "Using fallback binder: " + fallback);
                return fallback;
            }
        } catch (Throwable t) {
            SafeLog.e("MorpheLocation", "Error extracting map binder", t);
        }
        return null;
    }

    private static Object extractGoogleMapDelegate(Object mapObj) {
        if (mapObj == null) return null;
        try {
            for (Class<?> clazz = mapObj.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(mapObj);
                    if (val == null) continue;
                    if (val instanceof android.os.IInterface) {
                        android.os.IBinder b = ((android.os.IInterface) val).asBinder();
                        if (isGoogleMapDelegateBinder(b)) {
                            return val;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void wrapGoogleMapIfNeeded(Object mapObj) {
        if (mapObj == null || sWrappedMapObjects.contains(mapObj)) return;
        sWrappedMapObjects.add(mapObj);
        try {
            Object delegate = extractGoogleMapDelegate(mapObj);
            if (delegate != null) {
                sGoogleMapDelegate = delegate;
                SafeLog.d("MorpheLocation", "Captured sGoogleMapDelegate: " + delegate.getClass().getName());
            }
        } catch (Throwable t) {
            SafeLog.e("MorpheLocation", "Failed to wrap GoogleMap delegate", t);
        }
    }

    private static void setLocationSource(android.os.IBinder mapBinder, android.os.IBinder locationSource) {
        if (mapBinder == null || locationSource == null) return;
        android.os.Parcel data = android.os.Parcel.obtain();
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            data.writeInterfaceToken("com.google.android.gms.maps.internal.IGoogleMapDelegate");
            data.writeStrongBinder(locationSource);
            boolean success = mapBinder.transact(24, data, reply, 0);
            if (success) {
                reply.readException();
                SafeLog.d("MorpheLocation", "Successfully called setLocationSource (transact 24)");
            } else {
                SafeLog.w("MorpheLocation", "transact 24 for setLocationSource returned false");
            }
        } catch (Throwable t) {
            SafeLog.w("MorpheLocation", "transact 24 for setLocationSource failed", t);
        } finally {
            data.recycle();
            reply.recycle();
        }

        try {
            Object target = mapBinder;
            if (target instanceof android.os.IInterface) {
                target = ((android.os.IInterface) target).asBinder();
            }
            Class<?> clazz = target.getClass();
            for (Method m : clazz.getMethods()) {
                if ("setLocationSource".equals(m.getName()) && m.getParameterTypes().length == 1) {
                    m.setAccessible(true);
                    m.invoke(target, locationSource);
                    SafeLog.d("MorpheLocation", "Invoked setLocationSource directly on " + clazz.getSimpleName());
                    break;
                }
            }
            java.lang.reflect.Field fDefault = null;
            java.lang.reflect.Field fCurrent = null;
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if ("defaultLocationEngine".equals(f.getName())) fDefault = f;
                    if ("locationEngine".equals(f.getName())) fCurrent = f;
                }
            }
            if (fDefault != null && fCurrent != null) {
                fDefault.setAccessible(true);
                fCurrent.setAccessible(true);
                Object currentEngine = fCurrent.get(target);
                if (currentEngine != null) {
                    fDefault.set(target, currentEngine);
                    SafeLog.d("MorpheLocation", "Successfully replaced defaultLocationEngine with SourceLocationEngine on " + clazz.getSimpleName());
                }
            }
        } catch (Throwable t) {
            SafeLog.w("MorpheLocation", "Direct setLocationSource reflection failed", t);
        }
    }

    private static void setMyLocationEnabled(android.os.IBinder mapBinder, boolean enabled) {
        if (mapBinder == null) return;
        android.os.Parcel data = android.os.Parcel.obtain();
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            data.writeInterfaceToken("com.google.android.gms.maps.internal.IGoogleMapDelegate");
            data.writeInt(enabled ? 1 : 0);
            boolean success = mapBinder.transact(22, data, reply, 0);
            if (success) {
                reply.readException();
                SafeLog.d("MorpheLocation", "Successfully called setMyLocationEnabled (transact 22)");
            }
        } catch (Throwable t) {
            SafeLog.w("MorpheLocation", "transact 22 for setMyLocationEnabled failed", t);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static boolean isMapObject(Object obj) {
        if (obj == null) return false;
        Class<?> cls = obj.getClass();
        for (Method m : cls.getMethods()) {
            if (m.getReturnType() != null && m.getReturnType().getName().contains("CameraPosition")) {
                return true;
            }
        }
        boolean hasLocationOrCamera = false;
        for (Method m : cls.getMethods()) {
            if (m.getName().equals("f") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                hasLocationOrCamera = true;
            }
            if ((m.getName().equals("t") || m.getName().equals("s") || m.getName().equals("r") || m.getName().equals("v") || m.getName().equals("u"))
                    && (m.getParameterTypes().length == 1 || m.getParameterTypes().length == 2)) {
                if (hasLocationOrCamera) return true;
            }
        }
        return hasLocationOrCamera;
    }

    private static Object findMapObject(Object mixin) {
        if (mixin == null) return null;
        Class<?> clazz = mixin.getClass();
        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object val = f.get(mixin);
                if (val == null) continue;

                if (isMapObject(val)) {
                    return val;
                }

                for (Method m : val.getClass().getDeclaredMethods()) {
                    if ((m.getName().equals("a") || m.getName().equals("get")) && m.getParameterTypes().length == 0) {
                        try {
                            m.setAccessible(true);
                            Object inner = m.invoke(val);
                            if (inner != null && isMapObject(inner)) {
                                return inner;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object createCameraUpdate(ClassLoader cl, Object latLng, float zoom) {
        if (latLng == null) return null;
        Class<?> latLngClass = latLng.getClass();

        if (sCachedCameraUpdateFactoryClass != null) {
            Object cu = invokeCameraUpdateFactory(sCachedCameraUpdateFactoryClass, latLng, zoom, latLngClass);
            if (cu != null) return cu;
        }

        String[] knownClasses = new String[]{
            "bqyk", "defpackage.bqyk",
            "bqll", "defpackage.bqll",
            "bqbb", "defpackage.bqbb",
            "bprq", "defpackage.bprq",
            "brwd", "defpackage.brwd",
            "com.google.android.gms.maps.CameraUpdateFactory"
        };
        for (String clsName : knownClasses) {
            try {
                Class<?> c = null;
                try { c = Class.forName(clsName); } catch (Throwable ignored) {}
                if (c == null && cl != null) {
                    try { c = cl.loadClass(clsName); } catch (Throwable ignored) {}
                }
                if (c != null) {
                    Object cu = invokeCameraUpdateFactory(c, latLng, zoom, latLngClass);
                    if (cu != null) {
                        sCachedCameraUpdateFactoryClass = c;
                        return cu;
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (cl != null) {
            try {
                Class<?> current = cl.getClass();
                java.lang.reflect.Field pathListField = null;
                while (current != null && current != Object.class) {
                    try {
                        pathListField = current.getDeclaredField("pathList");
                        break;
                    } catch (NoSuchFieldException e) {
                        current = current.getSuperclass();
                    }
                }
                if (pathListField != null) {
                    pathListField.setAccessible(true);
                    Object pathList = pathListField.get(cl);
                    java.lang.reflect.Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
                    dexElementsField.setAccessible(true);
                    Object[] dexElements = (Object[]) dexElementsField.get(pathList);
                    for (Object element : dexElements) {
                        java.lang.reflect.Field dexFileField = element.getClass().getDeclaredField("dexFile");
                        dexFileField.setAccessible(true);
                        dalvik.system.DexFile dexFile = (dalvik.system.DexFile) dexFileField.get(element);
                        if (dexFile != null) {
                            java.util.Enumeration<String> entries = dexFile.entries();
                            while (entries.hasMoreElements()) {
                                String className = entries.nextElement();
                                int dotIdx = className.lastIndexOf('.');
                                String simpleName = dotIdx >= 0 ? className.substring(dotIdx + 1) : className;
                                if (simpleName.length() <= 4 || simpleName.endsWith("CameraUpdateFactory")) {
                                    try {
                                        Class<?> candidate = cl.loadClass(className);
                                        Object cu = invokeCameraUpdateFactory(candidate, latLng, zoom, latLngClass);
                                        if (cu != null) {
                                            sCachedCameraUpdateFactoryClass = candidate;
                                            SafeLog.d("MorpheLocation", "Discovered CameraUpdateFactory dynamically: " + className);
                                            return cu;
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                SafeLog.w("MorpheLocation", "Dynamic CameraUpdateFactory scan failed", t);
            }
        }

        return null;
    }

    private static Object invokeCameraUpdateFactory(Class<?> c, Object latLng, float zoom, Class<?> latLngClass) {
        try {
            for (Method m : c.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 2 && pts[0] == latLngClass && (pts[1] == float.class || pts[1] == Float.class)) {
                        m.setAccessible(true);
                        Object cu = m.invoke(null, latLng, zoom);
                        if (cu != null) return cu;
                    }
                }
            }
            for (Method m : c.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 1 && pts[0] == latLngClass) {
                        m.setAccessible(true);
                        Object cu = m.invoke(null, latLng);
                        if (cu != null) return cu;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object findMapExploreController(Object mixinObj) {
        if (mixinObj == null) return null;
        try {
            Class<?> mixinClass = mixinObj.getClass();
            Context context = null;
            for (java.lang.reflect.Field f : mixinClass.getDeclaredFields()) {
                if (Context.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    context = (Context) f.get(mixinObj);
                    break;
                }
            }
            if (context == null) return null;
            Activity activity = null;
            Context cur = context;
            while (cur instanceof android.content.ContextWrapper) {
                if (cur instanceof Activity) {
                    activity = (Activity) cur;
                    break;
                }
                cur = ((android.content.ContextWrapper) cur).getBaseContext();
            }
            if (activity == null) return null;

            for (Class<?> c = activity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(activity);
                    if (val != null) {
                        String valCls = val.getClass().getName().toLowerCase(Locale.ROOT);
                        if (valCls.contains("mapexplore")) {
                            SafeLog.d("MorpheLocation", "Found MapExploreController by name: " + f.getName() + " (" + val.getClass().getName() + ")");
                            return val;
                        }
                        try {
                            val.getClass().getDeclaredMethod("ba");
                            SafeLog.d("MorpheLocation", "Found MapExploreController in field: " + f.getName() + " (" + val.getClass().getName() + ")");
                            return val;
                        } catch (NoSuchMethodException ignored) {}
                    }
                }
            }

            try {
                Class<?> faClass = Class.forName("androidx.fragment.app.FragmentActivity");
                if (faClass.isInstance(activity)) {
                    Method mGetFM = faClass.getMethod("getSupportFragmentManager");
                    Object fm = mGetFM.invoke(activity);
                    if (fm != null) {
                        Method mGetFrags = fm.getClass().getMethod("getFragments");
                        java.util.List<?> frags = (java.util.List<?>) mGetFrags.invoke(fm);
                        if (frags != null) {
                            for (Object frag : frags) {
                                if (frag != null && frag.getClass().getName().toLowerCase(Locale.ROOT).contains("mapexplore")) {
                                    SafeLog.d("MorpheLocation", "Found MapExplore fragment: " + frag.getClass().getName());
                                    return frag;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            SafeLog.e("MorpheLocation", "Error finding MapExploreController", t);
        }
        return null;
    }

    private static void triggerGmsCameraIdle() {
        sCameraIdleHandler.removeCallbacks(sCameraIdleRunnable);
        sCameraIdleHandler.postDelayed(sCameraIdleRunnable, 100);
    }

    private static void doTriggerGmsCameraIdle() {
        Object delegate = sGoogleMapDelegate;
        if (delegate == null) return;
        try {
            android.os.IBinder serverBinder = null;
            if (delegate instanceof android.os.IInterface) {
                serverBinder = ((android.os.IInterface) delegate).asBinder();
            } else if (delegate instanceof android.os.IBinder) {
                serverBinder = (android.os.IBinder) delegate;
            }

            SafeLog.d("MorpheLocation",
                    "triggerGmsCameraIdle: serverBinder=" +
                    (serverBinder != null ? serverBinder.getClass().getName() : "null") +
                    " isLocalBinder=" + (serverBinder instanceof android.os.Binder));

            if (serverBinder != null && sDumpedGoogleMapImplFields.compareAndSet(false, true)) {
                StringBuilder sb = new StringBuilder("GoogleMapImpl fields: ");
                for (Class<?> c = serverBinder.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        f.setAccessible(true);
                        Object val;
                        try { val = f.get(serverBinder); } catch (Throwable e) { val = "<err>"; }
                        sb.append(f.getName()).append("=").append(val == null ? "null" : val.getClass().getSimpleName()).append(", ");
                    }
                }
                SafeLog.d("MorpheLocation", sb.toString());
            }

            if (serverBinder != null && searchAndFireCameraIdleListener(serverBinder)) return;
            if (delegate != serverBinder && searchAndFireCameraIdleListener(delegate)) return;

            if (serverBinder != null) {
                for (Method m : serverBinder.getClass().getDeclaredMethods()) {
                    if ("onCameraIdle".equals(m.getName()) && m.getParameterTypes().length == 0) {
                        m.setAccessible(true);
                        m.invoke(serverBinder);
                        SafeLog.d("MorpheLocation",
                                "triggerGmsCameraIdle: called onCameraIdle() directly on GoogleMapImpl");
                        return;
                    }
                }
                for (Class<?> c = serverBinder.getClass().getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods()) {
                        if ("onCameraIdle".equals(m.getName()) && m.getParameterTypes().length == 0) {
                            m.setAccessible(true);
                            m.invoke(serverBinder);
                            SafeLog.d("MorpheLocation",
                                    "triggerGmsCameraIdle: called onCameraIdle() on superclass " + c.getSimpleName());
                            return;
                        }
                    }
                }
            }

            SafeLog.w("MorpheLocation",
                    "triggerGmsCameraIdle: could not fire camera-idle event");
        } catch (Throwable t) {
            SafeLog.e("MorpheLocation", "triggerGmsCameraIdle failed", t);
        }
    }

    private static boolean searchAndFireCameraIdleListener(Object obj) {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                Object val;
                try { val = f.get(obj); } catch (Throwable ignored) { continue; }
                if (val == null) continue;

                android.os.IBinder binder = null;
                if (val instanceof android.os.IBinder) {
                    binder = (android.os.IBinder) val;
                } else if (val instanceof android.os.IInterface) {
                    binder = ((android.os.IInterface) val).asBinder();
                }

                if (binder != null) {
                    String descriptor = null;
                    try { descriptor = binder.getInterfaceDescriptor(); } catch (Throwable ignored) {}
                    if (descriptor != null && descriptor.toLowerCase(Locale.ROOT).contains("cameraidle")) {
                        SafeLog.d("MorpheLocation",
                                "searchAndFire: found CameraIdle listener in field " + f.getName() +
                                " descriptor=" + descriptor + " on " + c.getSimpleName());
                        try {
                            Method m = val.getClass().getMethod("onCameraIdle");
                            m.invoke(val);
                            SafeLog.d("MorpheLocation", "searchAndFire: invoked via reflection");
                            return true;
                        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {}
                        try {
                            android.os.Parcel data = android.os.Parcel.obtain();
                            android.os.Parcel reply = android.os.Parcel.obtain();
                            try {
                                data.writeInterfaceToken(descriptor);
                                binder.transact(1, data, reply, 0);
                                reply.readException();
                                SafeLog.d("MorpheLocation", "searchAndFire: invoked via transact(1)");
                            } finally {
                                data.recycle();
                                reply.recycle();
                            }
                            return true;
                        } catch (Throwable t) {
                            SafeLog.w("MorpheLocation", "searchAndFire: transact failed", t);
                        }
                    }
                }
            }
        }
        return false;
    }
}
