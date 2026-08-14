package cozycraft;

import java.lang.reflect.*;

/**
 * Loader-light bootstrap for Cozycraft Records 26.2.
 * No nested record/classes are used so the packaged jar cannot lose an
 * inner class during assembly.
 */
public final class Bootstrap {
    private static final String MOD_ID = "cozycraft_records";
    private static final String[] IDS = {"warm_overworld", "echoes_below", "around_the_campfire"};

    private Bootstrap() {}

    public static void init() {
        try {
            Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
            Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
            Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
            Class<?> builtInRegistries = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Class<?> soundEventClass = Class.forName("net.minecraft.sounds.SoundEvent");
            Class<?> itemClass = Class.forName("net.minecraft.world.item.Item");

            Object itemRegistryKey = getStatic(registriesClass, "ITEM");
            Object soundRegistry = getStatic(builtInRegistries, "SOUND_EVENT");
            Object itemRegistry = getStatic(builtInRegistries, "ITEM");
            Object jukeboxSongRegistryKey = getStatic(registriesClass, "JUKEBOX_SONG");

            Class<?> propsClass = null;
            for (Class<?> c : itemClass.getDeclaredClasses()) {
                if (c.getSimpleName().equals("Properties")) {
                    propsClass = c;
                    break;
                }
            }
            if (propsClass == null) throw new IllegalStateException("Minecraft Item.Properties was not found");

            for (String id : IDS) {
                Object soundId = identifier(identifierClass, id);
                Object soundEvent = invokeStatic(soundEventClass, "createVariableRangeEvent", soundId);
                invokeStatic(registryClass, "register", soundRegistry, soundId, soundEvent);

                Object songId = identifier(identifierClass, id);
                Object songKey = invokeStatic(resourceKeyClass, "create", jukeboxSongRegistryKey, songId);
                Object itemKey = invokeStatic(resourceKeyClass, "create", itemRegistryKey, identifier(identifierClass, id));

                Object props = propsClass.getDeclaredConstructor().newInstance();
                invoke(props, "setId", itemKey);
                invoke(props, "stacksTo", Integer.valueOf(1));
                invoke(props, "jukeboxPlayable", songKey);

                Constructor<?> itemCtor = itemClass.getDeclaredConstructor(propsClass);
                Object item = itemCtor.newInstance(props);
                invokeStatic(registryClass, "register", itemRegistry, itemKey, item);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Cozycraft Records failed to initialize", t);
        }
    }

    private static Object identifier(Class<?> c, String path) throws Exception {
        try {
            return c.getMethod("fromNamespaceAndPath", String.class, String.class).invoke(null, MOD_ID, path);
        } catch (NoSuchMethodException e) {
            return c.getMethod("of", String.class, String.class).invoke(null, MOD_ID, path);
        }
    }

    private static Object getStatic(Class<?> c, String field) throws Exception {
        return c.getField(field).get(null);
    }

    private static Object invokeStatic(Class<?> c, String name, Object... args) throws Exception {
        return findMethod(c, name, args).invoke(null, args);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        return findMethod(target.getClass(), name, args).invoke(target, args);
    }

    private static Method findMethod(Class<?> c, String name, Object[] args) throws NoSuchMethodException {
        for (Class<?> current = c; current != null; current = current.getSuperclass()) {
            for (Method m : current.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                Class<?>[] p = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < p.length; i++) {
                    if (args[i] == null) continue;
                    if (!wrap(p[i]).isAssignableFrom(args[i].getClass())) { ok = false; break; }
                }
                if (ok) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new NoSuchMethodException(c.getName() + "." + name + "(" + args.length + ")");
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == char.class) return Character.class;
        return c;
    }
}
