package app.morphe.extension.shared.patches.toolkit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe reflection cache to eliminate repetitive metadata lookups
 * during bulk database scans and remote task executions.
 */
public final class ReflectionCache {

    private static final ConcurrentHashMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    private ReflectionCache() {}

    public static Class<?> findClass(String className) throws ClassNotFoundException {
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) {
            return cached;
        }
        Class<?> clazz = Class.forName(className);
        CLASS_CACHE.put(className, clazz);
        return clazz;
    }

    public static Class<?> findClassOrNull(String className) {
        try {
            return findClass(className);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Method getMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        String key = clazz.getName() + "#" + name + Arrays.toString(parameterTypes);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Method method = clazz.getMethod(name, parameterTypes);
        method.setAccessible(true);
        METHOD_CACHE.put(key, method);
        return method;
    }

    public static Method getMethodOrNull(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return getMethod(clazz, name, parameterTypes);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Method getDeclaredMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        String key = clazz.getName() + "#decl#" + name + Arrays.toString(parameterTypes);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Method method = clazz.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        METHOD_CACHE.put(key, method);
        return method;
    }

    public static Method getDeclaredMethodOrNull(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return getDeclaredMethod(clazz, name, parameterTypes);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Field getDeclaredField(Class<?> clazz, String name) throws NoSuchFieldException {
        String key = clazz.getName() + "#" + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        FIELD_CACHE.put(key, field);
        return field;
    }

    public static Field getDeclaredFieldOrNull(Class<?> clazz, String name) {
        try {
            return getDeclaredField(clazz, name);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Constructor<?> getConstructor(Class<?> clazz, Class<?>... parameterTypes) throws NoSuchMethodException {
        String key = clazz.getName() + "#init" + Arrays.toString(parameterTypes);
        Constructor<?> cached = CONSTRUCTOR_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Constructor<?> constructor = clazz.getConstructor(parameterTypes);
        constructor.setAccessible(true);
        CONSTRUCTOR_CACHE.put(key, constructor);
        return constructor;
    }

    public static Constructor<?> getConstructorOrNull(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            return getConstructor(clazz, parameterTypes);
        } catch (Throwable t) {
            return null;
        }
    }
}
