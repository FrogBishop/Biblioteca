package babel.biblioteca.client.sodiumcompat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Cached access to optional Sodium internals; no Sodium classes are linked without the mod. */
public final class SodiumAccess {
    private record Members(Map<String, Field> fields, Map<String, Method> methods) {}
    private static final ClassValue<Members> MEMBERS = new ClassValue<>() {
        @Override protected Members computeValue(Class<?> type) {
            Map<String, Field> fields = new HashMap<>();
            Map<String, Method> methods = new HashMap<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    field.setAccessible(true);
                    fields.putIfAbsent(field.getName(), field);
                }
                for (Method method : c.getDeclaredMethods()) {
                    method.setAccessible(true);
                    methods.putIfAbsent(method.getName() + "/" + method.getParameterCount(), method);
                }
            }
            for (Method method : type.getMethods()) methods.putIfAbsent(method.getName() + "/" + method.getParameterCount(), method);
            return new Members(fields, methods);
        }
    };
    private SodiumAccess() {}
    public static boolean has(Object value, String name) { return MEMBERS.get(value.getClass()).fields().containsKey(name); }
    public static boolean hasMethod(Object value, String name, int count) {
        return MEMBERS.get(value.getClass()).methods().containsKey(name + "/" + count);
    }
    public static Object get(Object value, String... names) {
        for (String name : names) {
            Field field = MEMBERS.get(value.getClass()).fields().get(name);
            if (field != null) try { return field.get(value); } catch (IllegalAccessException failure) { throw error(failure); }
        }
        throw new IllegalStateException("Unsupported Sodium field in " + value.getClass().getName() + ": " + String.join(",", names));
    }
    public static void set(Object value, String name, Object data) {
        Field field = MEMBERS.get(value.getClass()).fields().get(name);
        if (field == null) throw new IllegalStateException("Unsupported Sodium field: " + name);
        try { field.set(value, data); } catch (IllegalAccessException failure) { throw error(failure); }
    }
    public static Object call(Object value, String name, Object... args) {
        Method method = MEMBERS.get(value.getClass()).methods().get(name + "/" + args.length);
        if (method == null) throw new IllegalStateException("Unsupported Sodium method in " + value.getClass().getName() + ": " + name);
        try { return method.invoke(value, args); } catch (ReflectiveOperationException failure) { throw error(failure); }
    }
    public static Object construct(Class<?> type, Object... args) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != args.length) continue;
            constructor.setAccessible(true);
            try { return constructor.newInstance(args); } catch (ReflectiveOperationException failure) { throw error(failure); }
        }
        throw new IllegalStateException("Unsupported Sodium constructor: " + type.getName());
    }
    public static Class<?> sibling(Object value, String path) {
        String prefix = value.getClass().getName().startsWith("me.jellysquid.")
                ? "me.jellysquid.mods.sodium." : "net.caffeinemc.mods.sodium.";
        try { return Class.forName(prefix + path, true, value.getClass().getClassLoader()); }
        catch (ClassNotFoundException failure) { throw error(failure); }
    }
    private static RuntimeException error(ReflectiveOperationException failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
        if (cause instanceof RuntimeException runtime) return runtime;
        if (cause instanceof Error error) throw error;
        return new IllegalStateException("Sodium periodic renderer access failed", cause);
    }
}
