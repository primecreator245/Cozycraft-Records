package cozycraft;

import java.lang.reflect.*;
import java.util.function.Consumer;

@net.neoforged.fml.common.Mod("cozycraft_records")
public final class NeoForgeBootstrap {
    private static final String MOD_ID = "cozycraft_records";
    private static final String[] IDS = {"warm_overworld", "echoes_below", "around_the_campfire"};

    public NeoForgeBootstrap() {
        try {
            Class<?> ctxClass = Class.forName("net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext");
            Object ctx = ctxClass.getMethod("get").invoke(null);
            Object modBus = ctxClass.getMethod("getModEventBus").invoke(ctx);

            Class<?> registerEvent = Class.forName("net.neoforged.neoforge.registries.RegisterEvent");
            Class<?> creativeEvent = Class.forName("net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent");

            addTypedListener(modBus, registerEvent, (Consumer<Object>) this::onRegister);
            addTypedListener(modBus, creativeEvent, (Consumer<Object>) this::onCreativeTab);
        } catch (Throwable t) {
            throw new RuntimeException("Cozycraft Records failed to initialize", t);
        }
    }

    private void onRegister(Object event) {
        try {
            Class<?> registries = Class.forName("net.minecraft.core.registries.Registries");
            Object soundKey = getField(registries, "SOUND_EVENT");
            Object itemKey = getField(registries, "ITEM");
            Object registryKey = invokeNoArg(event, "getRegistryKey", "getRegistry");

            if (sameRegistry(registryKey, soundKey)) {
                registerRegistryEntries(event, soundKey, (helper, id, name) -> {
                    Class<?> identifier = Class.forName("net.minecraft.resources.Identifier");
                    Object ident = identifier.getMethod("fromNamespaceAndPath", String.class, String.class)
                            .invoke(null, MOD_ID, name);
                    Class<?> soundEvent = Class.forName("net.minecraft.sounds.SoundEvent");
                    Object sound = soundEvent.getMethod("createVariableRangeEvent", identifier)
                            .invoke(null, ident);
                    helperRegister(helper, ident, sound);
                });
            } else if (sameRegistry(registryKey, itemKey)) {
                registerRegistryEntries(event, itemKey, (helper, id, name) -> {
                    Object item = createItem(name);
                    helperRegister(helper, id, item);
                });
            }
        } catch (Throwable t) {
            throw new RuntimeException("Cozycraft Records registry registration failed", t);
        }
    }

    private void onCreativeTab(Object event) {
        try {
            Class<?> tabs = Class.forName("net.minecraft.world.item.CreativeModeTabs");
            Object ingredients = getField(tabs, "INGREDIENTS");
            Object tabKey = invokeNoArg(event, "getTabKey");
            if (!ingredients.equals(tabKey)) return;

            Class<?> builtins = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object itemRegistry = getField(builtins, "ITEM");
            Class<?> identifier = Class.forName("net.minecraft.resources.Identifier");
            Method getMethod = findMethod(itemRegistry.getClass(), "get", 1);
            Method accept = findAccept(event.getClass());

            for (String id : IDS) {
                Object ident = identifier.getMethod("fromNamespaceAndPath", String.class, String.class)
                        .invoke(null, MOD_ID, id);
                Object item = getMethod.invoke(itemRegistry, ident);
                if (item != null) accept.invoke(event, item);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Cozycraft Records creative tab registration failed", t);
        }
    }

    private Object createItem(String id) throws Exception {
        Class<?> identifier = Class.forName("net.minecraft.resources.Identifier");
        Class<?> resourceKey = Class.forName("net.minecraft.resources.ResourceKey");
        Class<?> registries = Class.forName("net.minecraft.core.registries.Registries");
        Class<?> itemClass = Class.forName("net.minecraft.world.item.Item");
        Class<?> propsClass = Class.forName("net.minecraft.world.item.Item$Properties");

        Object itemRegistryKey = getField(registries, "ITEM");
        Object ident = identifier.getMethod("fromNamespaceAndPath", String.class, String.class)
                .invoke(null, MOD_ID, id);
        Object itemKey = resourceKey.getMethod("create", Class.class, Object.class)
                .invoke(null, itemRegistryKey, ident);

        Object props = propsClass.getDeclaredConstructor().newInstance();
        invokeCompatible(props, "setId", itemKey);
        invokeCompatible(props, "stacksTo", Integer.valueOf(1));

        Object songKey = resourceKey.getMethod("create", Class.class, Object.class)
                .invoke(null, getField(registries, "JUKEBOX_SONG"), ident);
        invokeCompatible(props, "jukeboxPlayable", songKey);

        return itemClass.getConstructor(propsClass).newInstance(props);
    }

    private interface EntryRegistrar {
        void register(Object helper, Object id, String name) throws Exception;
    }

    private void registerRegistryEntries(Object event, Object registryKey, EntryRegistrar registrar) throws Exception {
        Method eventRegister = null;
        for (Method m : event.getClass().getMethods()) {
            if (!m.getName().equals("register") || m.getParameterCount() != 2) continue;
            if (m.getParameterTypes()[0].isAssignableFrom(registryKey.getClass()) ||
                registryKey.getClass().isAssignableFrom(m.getParameterTypes()[0])) {
                eventRegister = m;
                break;
            }
        }
        if (eventRegister == null) throw new NoSuchMethodException("RegisterEvent.register(registryKey, consumer)");

        Consumer<Object> consumer = helper -> {
            try {
                for (String id : IDS) registrar.register(helper, makeIdentifier(id), id);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        };
        eventRegister.invoke(event, registryKey, consumer);
    }

    private static Object makeIdentifier(String id) throws Exception {
        Class<?> identifier = Class.forName("net.minecraft.resources.Identifier");
        return identifier.getMethod("fromNamespaceAndPath", String.class, String.class)
                .invoke(null, MOD_ID, id);
    }

    private static void helperRegister(Object helper, Object id, Object value) throws Exception {
        for (Method m : helper.getClass().getMethods()) {
            if (!m.getName().equals("register") || m.getParameterCount() != 2) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p[0].isAssignableFrom(id.getClass()) && p[1].isAssignableFrom(value.getClass())) {
                m.invoke(helper, id, value);
                return;
            }
        }
        throw new NoSuchMethodException("RegisterHelper.register(identifier, value)");
    }

    private static boolean sameRegistry(Object actual, Object expected) {
        if (actual == expected || (actual != null && actual.equals(expected))) return true;
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private static Object getField(Class<?> c, String name) throws Exception {
        Field f = c.getField(name);
        return f.get(null);
    }

    private static Object invokeNoArg(Object target, String... names) throws Exception {
        for (String name : names) {
            try { return target.getClass().getMethod(name).invoke(target); }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(String.join("/", names));
    }

    private static void addTypedListener(Object bus, Class<?> eventType, Consumer<Object> listener) throws Exception {
        for (Method m : bus.getClass().getMethods()) {
            if (!m.getName().equals("addListener") || m.getParameterCount() != 2) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p[0] == Class.class && Consumer.class.isAssignableFrom(p[1])) {
                m.invoke(bus, eventType, listener);
                return;
            }
        }
        throw new NoSuchMethodException("IEventBus.addListener(Class, Consumer)");
    }

    private static Method findMethod(Class<?> c, String name, int count) throws NoSuchMethodException {
        for (Class<?> x = c; x != null; x = x.getSuperclass()) {
            for (Method m : x.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == count) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Method findAccept(Class<?> c) throws NoSuchMethodException {
        for (Method m : c.getMethods()) {
            if (!m.getName().equals("accept") || m.getParameterCount() != 1) continue;
            if (m.getParameterTypes()[0].getName().equals("net.minecraft.world.item.ItemLike") ||
                m.getParameterTypes()[0].getName().equals("net.minecraft.world.item.Item")) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException("CreativeModeTab.Output.accept(ItemLike)");
    }

    private static Object invokeCompatible(Object target, String name, Object arg) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != 1) continue;
            if (wrap(m.getParameterTypes()[0]).isAssignableFrom(arg.getClass())) {
                m.setAccessible(true);
                return m.invoke(target, arg);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
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
