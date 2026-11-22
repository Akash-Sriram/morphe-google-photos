package app.morphe.extension.shared.patches.toolkit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import app.morphe.extension.shared.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StructuralSymbolResolver provides dynamic runtime self-healing symbol resolution
 * for Google Photos Toolkit obfuscated internals.
 *
 * When static mappings in ToolkitMappings fail on a new or unaligned build,
 * this resolver discovers target classes and methods structurally (by interface,
 * parameter signature, and string constants), caching verified symbols in
 * SharedPreferences for O(1) subsequent startup speed.
 */
public final class StructuralSymbolResolver {

    private static final String PREF_NAME = "morphe_toolkit_symbols";
    private static final ConcurrentHashMap<String, Object> MEM_CACHE = new ConcurrentHashMap<>();

    private StructuralSymbolResolver() {}

    private static String getVersionedKey(Context context, String key) {
        String version = "unknown";
        if (context != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                if (pi.versionName != null) {
                    version = pi.versionName;
                }
            } catch (Throwable ignored) {}
        }
        return "sym_" + version + "_" + key;
    }

    private static String getCachedString(Context context, String key) {
        if (context == null) return null;
        Object mem = MEM_CACHE.get(key);
        if (mem instanceof String) return (String) mem;
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String val = sp.getString(getVersionedKey(context, key), null);
            if (val != null) {
                MEM_CACHE.put(key, val);
            }
            return val;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setCachedString(Context context, String key, String value) {
        if (context == null || value == null) return;
        MEM_CACHE.put(key, value);
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(getVersionedKey(context, key), value).apply();
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // 1. Service Locator Self-Healing: (Context, Class) -> Object
    // =========================================================================

    public static Object resolveServiceLocator(Context context, Class<?> serviceClass) {
        if (context == null || serviceClass == null) return null;

        // Check cache
        String cached = getCachedString(context, "service_locator");
        if (cached != null) {
            String[] parts = cached.split("#");
            if (parts.length == 2) {
                try {
                    Class<?> slClass = ReflectionCache.findClassOrNull(parts[0]);
                    if (slClass != null) {
                        Method m = ReflectionCache.getMethodOrNull(slClass, parts[1], Context.class, Class.class);
                        if (m != null) {
                            Object res = m.invoke(null, context, serviceClass);
                            if (res != null) return res;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        // Search static candidates first
        for (String slName : ToolkitMappings.SERVICE_LOCATOR_CLASSES) {
            try {
                Class<?> slClass = ReflectionCache.findClassOrNull(slName);
                if (slClass != null) {
                    for (Method m : slClass.getMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) &&
                            m.getParameterTypes().length == 2 &&
                            m.getParameterTypes()[0] == Context.class &&
                            m.getParameterTypes()[1] == Class.class) {
                            try {
                                Object res = m.invoke(null, context, serviceClass);
                                if (res != null) {
                                    setCachedString(context, "service_locator", slName + "#" + m.getName());
                                    Logger.printInfo(() -> "StructuralSymbolResolver: Resolved ServiceLocator via candidate " + slName + "#" + m.getName());
                                    return res;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Structural heuristic: inspect known Photos classes that invoke ServiceLocator
        try {
            Class<?> actionQueue = ReflectionCache.findClassOrNull("com.google.android.apps.photos.actionqueue.ActionWrapper");
            if (actionQueue != null) {
                for (Method m : actionQueue.getDeclaredMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length >= 2 && m.getParameterTypes()[0] == Context.class) {
                        for (Class<?> p : m.getParameterTypes()) {
                            if (p == Class.class) {
                                Object res = m.invoke(null, context, serviceClass);
                                if (res != null) {
                                    setCachedString(context, "service_locator", actionQueue.getName() + "#" + m.getName());
                                    return res;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    // =========================================================================
    // 2. Trash Media Collection Self-Healing
    // =========================================================================

    public static Object resolveTrashMediaCollection(Context context, int accountId) {
        // Check cache
        String cached = getCachedString(context, "trash_collection");
        if (cached != null) {
            try {
                if (cached.contains("#")) {
                    String[] parts = cached.split("#");
                    Class<?> fClass = ReflectionCache.findClassOrNull(parts[0]);
                    if (fClass != null) {
                        Method m = fClass.getMethod(parts[1], int.class);
                        Object coll = m.invoke(null, accountId);
                        if (coll != null && verifyTrashCollection(coll)) return coll;
                    }
                } else {
                    Class<?> tClass = ReflectionCache.findClassOrNull(cached);
                    if (tClass != null) {
                        try {
                            Constructor<?> ctor = tClass.getConstructor(int.class);
                            Object coll = ctor.newInstance(accountId);
                            if (coll != null && verifyTrashCollection(coll)) return coll;
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Heuristic 1: Scan TRASH_FACTORIES pairs
        for (String[] pair : ToolkitMappings.TRASH_FACTORIES) {
            try {
                Class<?> fClass = ReflectionCache.findClassOrNull(pair[0]);
                if (fClass != null) {
                    for (Method m : fClass.getMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) &&
                            m.getParameterTypes().length == 1 &&
                            m.getParameterTypes()[0] == int.class) {
                            try {
                                Object coll = m.invoke(null, accountId);
                                if (coll != null && verifyTrashCollection(coll)) {
                                    setCachedString(context, "trash_collection", pair[0] + "#" + m.getName());
                                    Logger.printInfo(() -> "StructuralSymbolResolver: Resolved TrashFactory via " + pair[0] + "#" + m.getName());
                                    return coll;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Heuristic 2: Scan TRASH_COLLECTION_CLASSES
        for (String tName : ToolkitMappings.TRASH_COLLECTION_CLASSES) {
            try {
                Class<?> tClass = ReflectionCache.findClassOrNull(tName);
                if (tClass != null) {
                    try {
                        Constructor<?> ctor = tClass.getConstructor(int.class);
                        Object coll = ctor.newInstance(accountId);
                        if (coll != null && verifyTrashCollection(coll)) {
                            setCachedString(context, "trash_collection", tName);
                            Logger.printInfo(() -> "StructuralSymbolResolver: Resolved TrashCollection ctor via " + tName);
                            return coll;
                        }
                    } catch (Throwable ignored) {}

                    for (Method m : tClass.getMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) &&
                            m.getParameterTypes().length == 1 &&
                            m.getParameterTypes()[0] == int.class) {
                            try {
                                Object coll = m.invoke(null, accountId);
                                if (coll != null && verifyTrashCollection(coll)) {
                                    setCachedString(context, "trash_collection", tName + "#" + m.getName());
                                    Logger.printInfo(() -> "StructuralSymbolResolver: Resolved TrashCollection static method via " + tName + "#" + m.getName());
                                    return coll;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static boolean verifyTrashCollection(Object coll) {
        if (coll == null) return false;
        try {
            Method eMethod = coll.getClass().getMethod("e");
            Object id = eMethod.invoke(coll);
            if (id != null && id.toString().contains("TrashCore")) {
                return true;
            }
        } catch (Throwable ignored) {}
        String s = coll.toString();
        return s != null && s.contains("TrashCore");
    }

    // =========================================================================
    // 3. Mars Remove Task Configuration Self-Healing
    // =========================================================================

    public static String[] resolveMarsRemoveConfig(Context context) {
        String cached = getCachedString(context, "mars_remove_config");
        if (cached != null) {
            String[] parts = cached.split(",");
            if (parts.length == 5) {
                if (verifyMarsConfig(parts)) return parts;
            }
        }

        for (String[] cfg : ToolkitMappings.MARS_REMOVE_CONFIGS) {
            if (verifyMarsConfig(cfg)) {
                setCachedString(context, "mars_remove_config", String.join(",", cfg));
                Logger.printInfo(() -> "StructuralSymbolResolver: Resolved MarsRemoveConfig via " + cfg[0] + " / " + cfg[3]);
                return cfg;
            }
        }

        return null;
    }

    private static boolean verifyMarsConfig(String[] cfg) {
        if (cfg == null || cfg.length != 5) return false;
        try {
            Class<?> argClass = ReflectionCache.findClassOrNull(cfg[0]);
            Class<?> enumClass = ReflectionCache.findClassOrNull(cfg[1]);
            Class<?> builderClass = ReflectionCache.findClassOrNull(cfg[3]);
            if (argClass == null || enumClass == null || builderClass == null) return false;
            Object enumVal = enumClass.getField(cfg[2]).get(null);
            if (enumVal == null) return false;
            boolean hasMethod = false;
            for (Method m : builderClass.getMethods()) {
                if (m.getName().equals(cfg[4]) && m.getParameterTypes().length == 4) {
                    hasMethod = true;
                    break;
                }
            }
            return hasMethod;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // =========================================================================
    // 4. Keys Media Collection Self-Healing
    // =========================================================================

    public static Object resolveKeysMediaCollection(Context context, int accountId, List<String> mediaKeys) {
        if (mediaKeys == null || mediaKeys.isEmpty()) return null;

        // Check cache
        String cached = getCachedString(context, "keys_collection");
        if (cached != null) {
            try {
                if (cached.contains("#")) {
                    String[] parts = cached.split("#");
                    Class<?> clazz = ReflectionCache.findClassOrNull(parts[0]);
                    if (clazz != null) {
                        Method m = ReflectionCache.getMethodOrNull(clazz, parts[1], int.class, List.class);
                        if (m != null) {
                            Object coll = m.invoke(null, accountId, mediaKeys);
                            if (coll != null) return coll;
                        }
                    }
                } else {
                    Class<?> bClass = ReflectionCache.findClassOrNull(cached);
                    if (bClass != null) {
                        Object coll = tryInvokeKeysBuilder(bClass, accountId, mediaKeys);
                        if (coll != null) return coll;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Heuristic 1: Scan KEYS_COLLECTION_FACTORIES
        for (String[] pair : ToolkitMappings.KEYS_COLLECTION_FACTORIES) {
            try {
                Class<?> clazz = ReflectionCache.findClassOrNull(pair[0]);
                if (clazz != null) {
                    for (Method m : clazz.getMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) &&
                            m.getParameterTypes().length == 2 &&
                            m.getParameterTypes()[0] == int.class &&
                            (m.getParameterTypes()[1] == List.class || m.getParameterTypes()[1] == Collection.class)) {
                            try {
                                Object coll = m.invoke(null, accountId, mediaKeys);
                                if (coll != null) {
                                    setCachedString(context, "keys_collection", pair[0] + "#" + m.getName());
                                    Logger.printInfo(() -> "StructuralSymbolResolver: Resolved KeysCollectionFactory via " + pair[0] + "#" + m.getName());
                                    return coll;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Heuristic 2: Scan KEYS_COLLECTION_BUILDERS
        for (String bName : ToolkitMappings.KEYS_COLLECTION_BUILDERS) {
            try {
                Class<?> bClass = ReflectionCache.findClassOrNull(bName);
                if (bClass != null) {
                    Object coll = tryInvokeKeysBuilder(bClass, accountId, mediaKeys);
                    if (coll != null) {
                        setCachedString(context, "keys_collection", bName);
                        Logger.printInfo(() -> "StructuralSymbolResolver: Resolved KeysCollectionBuilder via " + bName);
                        return coll;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static Object tryInvokeKeysBuilder(Class<?> bClass, int accountId, List<String> mediaKeys) {
        try {
            Object builder = bClass.getConstructor().newInstance();
            Field fa = bClass.getField("a");
            Field fb = bClass.getField("b");
            fa.setInt(builder, accountId);
            fb.set(builder, mediaKeys);
            try {
                bClass.getField("d").setBoolean(builder, true);
                bClass.getField("e").setBoolean(builder, true);
            } catch (Throwable ignored) {}
            Method buildMethod = bClass.getMethod("a");
            return buildMethod.invoke(builder);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
