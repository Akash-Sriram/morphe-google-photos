package app.morphe.extension.shared.patches.backup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;

@SuppressWarnings("unused")
public class PhotosBackupHelper {
    public static final String BACKUP_TAG = "MORPHE_BACKUP";

    public static void logBackup(String message) {
        try {
            if (BaseSettings.DEBUG.get()) {
                android.util.Log.d(BACKUP_TAG, message);
            }
        } catch (Throwable ignored) {}
    }

    public static void logBackupError(String message, Throwable t) {
        try {
            if (t != null) {
                android.util.Log.e(BACKUP_TAG, message + ": " + t.getMessage(), t);
            } else {
                android.util.Log.e(BACKUP_TAG, message);
            }
        } catch (Throwable ignored) {}
    }

    public static Object sanitizeLudh(Object ludhObj) {
        return ludhObj;
    }

    public static Object sanitizeLudh(Object ludhObj, Context context) {
        return ludhObj;
    }

    /**
     * Sanitizes GMS Core backup settings (uyc) for MicroG and non-certified devices.
     * When GMS Core returns empty/disabled settings or null selective backup sets,
     * this resolves the configured backup account from Google Photos preferences
     * and guarantees a non-null folder set, preventing NullPointerException and fallback to disabled state.
     */
    public static Object sanitizeGmsBackupSettings(Object gmsSettings, Context context) {
        if (gmsSettings == null) {
            return null;
        }
        try {
            if (context == null) {
                context = Utils.getContext();
            }
            Class<?> uycClass = gmsSettings.getClass();
            Field aField = uycClass.getDeclaredField("a"); // String (email)
            Field bField = uycClass.getDeclaredField("b"); // boolean (isBackupEnabled)
            Field cField = uycClass.getDeclaredField("c"); // boolean (cellularPhotos)
            Field dField = uycClass.getDeclaredField("d"); // boolean (roaming)
            Field eField = uycClass.getDeclaredField("e"); // boolean (selectiveBackup)
            Field fField = uycClass.getDeclaredField("f"); // Set<String> (folders)

            aField.setAccessible(true);
            bField.setAccessible(true);
            cField.setAccessible(true);
            dField.setAccessible(true);
            eField.setAccessible(true);
            fField.setAccessible(true);

            String email = (String) aField.get(gmsSettings);
            boolean isBackupEnabled = bField.getBoolean(gmsSettings);
            boolean cellularPhotos = cField.getBoolean(gmsSettings);
            boolean roaming = dField.getBoolean(gmsSettings);
            boolean selectiveBackup = eField.getBoolean(gmsSettings);
            @SuppressWarnings("unchecked")
            Set<String> folders = (Set<String>) fField.get(gmsSettings);
            if (folders == null) {
                folders = Collections.emptySet();
            }

            logBackup("[sanitizeGmsBackupSettings] INPUT: email=" + email + ", isBackupEnabled=" + isBackupEnabled + ", cellular=" + cellularPhotos + ", roaming=" + roaming + ", selective=" + selectiveBackup);

            if (context != null) {
                SharedPreferences backupPrefs = context.getSharedPreferences("photos.backup.backup_prefs", Context.MODE_PRIVATE);
                int backupAccountId = backupPrefs.getInt("backup_prefs_account_id", -1);
                SharedPreferences accountsPrefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE);

                if (email == null || email.trim().isEmpty()) {
                    if (backupAccountId >= 0) {
                        String configuredEmail = accountsPrefs.getString(backupAccountId + ".account_name", null);
                        if (configuredEmail != null && !configuredEmail.trim().isEmpty()) {
                            email = configuredEmail;
                        }
                    } else {
                        int activeKey = accountsPrefs.getInt("key.active-account-key", -1);
                        if (activeKey >= 0) {
                            String activeEmail = accountsPrefs.getString(activeKey + ".account_name", null);
                            if (activeEmail != null && !activeEmail.trim().isEmpty()) {
                                email = activeEmail;
                            }
                        }
                    }
                }

                if (backupAccountId >= 0) {
                    isBackupEnabled = true;
                } else {
                    isBackupEnabled = false;
                }
            }

            Constructor<?> ctor = uycClass.getDeclaredConstructor(
                    String.class, boolean.class, boolean.class, boolean.class, boolean.class, Set.class
            );
            ctor.setAccessible(true);
            Object sanitized = ctor.newInstance(email, isBackupEnabled, cellularPhotos, roaming, selectiveBackup, folders);
            final String finalEmail = email;
            final boolean finalEnabled = isBackupEnabled;
            logBackup("[sanitizeGmsBackupSettings] SUCCESS: email=" + finalEmail + ", enabled=" + finalEnabled);
            return sanitized;
        } catch (Throwable t) {
            logBackupError("[sanitizeGmsBackupSettings] Error sanitizing settings", t);
            return gmsSettings;
        }
    }

    public static int resolveRegisteredAccountId(Context context) {
        return resolveRegisteredAccountId(context, null);
    }

    public static int resolveRegisteredAccountId(Context context, int currentId) {
        if (currentId >= 0) {
            return currentId;
        }
        return resolveRegisteredAccountId(context, null);
    }

    public static int resolveRegisteredAccountId(Context context, Object accountStore) {
        if (context == null) {
            context = Utils.getContext();
        }
        if (context == null) return -1;
        try {
            try {
                SharedPreferences backupPrefs = context.getSharedPreferences("photos.backup.backup_prefs", Context.MODE_PRIVATE);
                int backupAccountId = backupPrefs.getInt("backup_prefs_account_id", -1);
                if (backupAccountId >= 0) {
                    logBackup("Resolved active backup account ID from backup_prefs: " + backupAccountId);
                    return backupAccountId;
                }
            } catch (Throwable ignored) {}

            SharedPreferences accountsPrefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE);
            int activeKey = accountsPrefs.getInt("key.active-account-key", -1);
            if (activeKey >= 0) {
                if (accountStore != null) {
                    try {
                        Method pMethod = accountStore.getClass().getMethod("p", int.class);
                        Boolean isValid = (Boolean) pMethod.invoke(accountStore, activeKey);
                        if (isValid != null && isValid) {
                            logBackup("Resolved registered active account key: " + activeKey);
                            return activeKey;
                        }
                    } catch (Throwable ignored) {
                        logBackup("Resolved active account key from prefs: " + activeKey);
                        return activeKey;
                    }
                } else {
                    logBackup("Resolved active account key from prefs: " + activeKey);
                    return activeKey;
                }
            }

            for (Map.Entry<String, ?> entry : accountsPrefs.getAll().entrySet()) {
                if (entry.getKey().endsWith(".account_name") && entry.getValue() instanceof String) {
                    try {
                        int id = Integer.parseInt(entry.getKey().substring(0, entry.getKey().indexOf('.')));
                        if (id >= 0) {
                            logBackup("Resolved account ID from prefs map: " + id + " (" + entry.getValue() + ")");
                            return id;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            if (accountStore != null) {
                try {
                    Method hMethod = accountStore.getClass().getMethod("h");
                    List<?> list = (List<?>) hMethod.invoke(accountStore);
                    if (list != null && !list.isEmpty()) {
                        Object first = list.get(0);
                        if (first instanceof Integer) {
                            logBackup("Resolved first registered account key: " + first);
                            return (Integer) first;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            ClassLoader classLoader = context.getClassLoader();
            String[] locators = {"cdhf", "ccym", "cbyf", "bzoq", "bxoe"};
            String[] handlers = {"cbne", "cbdz", "cbqo", "cbnu", "cboe", "cbmw"};
            String[] stores = {"cbnl", "cbeg", "cboi", "cbfc", "cbdr", "cbde"};

            for (String locName : locators) {
                try {
                    Class<?> locClass = classLoader.loadClass(locName);
                    Method eMethod = locClass.getMethod("e", Context.class, Class.class);

                    for (String handlerName : handlers) {
                        try {
                            Class<?> hClass = classLoader.loadClass(handlerName);
                            Object handler = eMethod.invoke(null, context, hClass);
                            if (handler != null) {
                                Method dMethod = handler.getClass().getMethod("d");
                                Integer id = (Integer) dMethod.invoke(handler);
                                if (id != null && id >= 0) {
                                    logBackup("Resolved active account ID via ServiceLocator (" + locName + " -> " + handlerName + ".d()): " + id);
                                    return id;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }

                    for (String storeName : stores) {
                        try {
                            Class<?> sClass = classLoader.loadClass(storeName);
                            Object store = eMethod.invoke(null, context, sClass);
                            if (store != null) {
                                Method hMethod = store.getClass().getMethod("h");
                                List<?> list = (List<?>) hMethod.invoke(store);
                                if (list != null && !list.isEmpty()) {
                                    Object first = list.get(0);
                                    if (first instanceof Integer && ((Integer) first) >= 0) {
                                        logBackup("Resolved registered account ID via ServiceLocator (" + locName + " -> " + storeName + ".h()): " + first);
                                        return (Integer) first;
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            logBackupError("Error resolving registered account ID", t);
        }
        return -1;
    }

    public static int resolveRegisteredAccountFromEmail(String gmsEmail, Object accountStore, Context context) {
        if (accountStore == null && context != null) {
            try {
                ClassLoader cl = context.getClassLoader();
                String[] locators = {"cdhf", "ccym", "cbyf", "bzoq", "bxoe"};
                String[] stores = {"cbnl", "cbeg", "cboi", "cbfc", "cbdr", "cbde"};
                for (String loc : locators) {
                    try {
                        Class<?> locClass = cl.loadClass(loc);
                        Method eMethod = locClass.getMethod("e", Context.class, Class.class);
                        for (String storeName : stores) {
                            try {
                                Class<?> sClass = cl.loadClass(storeName);
                                Object store = eMethod.invoke(null, context, sClass);
                                if (store != null) {
                                    accountStore = store;
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (accountStore != null) break;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        if (accountStore != null) {
            try {
                Method hMethod = accountStore.getClass().getMethod("h");
                List<?> accountIds = (List<?>) hMethod.invoke(accountStore);
                if (accountIds != null && !accountIds.isEmpty()) {
                    Method eMethod = accountStore.getClass().getMethod("e", int.class);
                    if (gmsEmail != null && !gmsEmail.trim().isEmpty()) {
                        String cleanGms = gmsEmail.trim().toLowerCase(Locale.US);
                        for (Object idObj : accountIds) {
                            if (idObj instanceof Integer) {
                                int id = (Integer) idObj;
                                Object cbec = eMethod.invoke(accountStore, id);
                                if (cbec != null) {
                                    Method getStrMethod = cbec.getClass().getMethod("e", String.class);
                                    String email = (String) getStrMethod.invoke(cbec, "account_name");
                                    if (email != null && email.trim().toLowerCase(Locale.US).equals(cleanGms)) {
                                        Logger.printInfo(() -> "Resolved GMS Core email " + gmsEmail + " to Photos account " + id);
                                        return id;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Logger.printException(() -> "Error resolving account from email", t);
            }
        }
        return resolveRegisteredAccountId(context, accountStore);
    }

    public static int resolveBackupAccount(int currentId, String gmsEmail, Object accountStore, Context context) {
        if (currentId >= 0) {
            return currentId;
        }
        return resolveRegisteredAccountFromEmail(gmsEmail, accountStore, context);
    }

    public static void onAutoBackupSettingsActivityAttachBinder(Activity activity) {
        try {
            Intent intent = activity != null ? activity.getIntent() : null;
            Bundle extras = intent != null ? intent.getExtras() : null;
            logBackup("[AutoBackupSettingsActivity.onAttachBinder] activity=" + activity + " intent=" + intent + " extras=" + extras);
        } catch (Throwable t) {
            logBackupError("[AutoBackupSettingsActivity.onAttachBinder] Error", t);
        }
    }

    public static Object onLuvhAEntry(Object ludhObj, Boolean isConnected) {
        return ludhObj;
    }

    public static Object onLuvhAReturn(Object luvk) {
        return luvk;
    }

    public static boolean onCheckBackupAccountLinked(boolean originalVal, Object luslObj) {
        try {
            int activeId = resolveRegisteredAccountId(Utils.getContext());
            if (activeId >= 0) {
                logBackup("[Lusl.b CHECK] Suppressing auto-account-switcher popup (active registered account id=" + activeId + ")");
                return true;
            }
        } catch (Throwable ignored) {}
        return originalVal;
    }
}
