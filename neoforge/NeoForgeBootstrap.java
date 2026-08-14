package cozycraft;

import java.lang.reflect.*;
import java.util.function.Consumer;

@net.neoforged.fml.common.Mod("cozycraft_records")
public final class NeoForgeBootstrap {
    private static final String MOD_ID = "cozycraft_records";
    private static final String[] IDS = {"warm_overworld", "echoes_below", "around_the_campfire"};

    public NeoForgeBootstrap() {
        try {
            Class<?> ctx = Class.forName("net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext");
            Object context = ctx.getMethod("get").invoke(null);
            Object bus = ctx.getMethod("getModEventBus").invoke(context);
            addTypedListener(bus, Class.forName("net.neoforged.neoforge.registries.RegisterEvent"), (Consumer<Object>) this::register);
            addTypedListener(bus, Class.forName("net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent"), (Consumer<Object>) this::creative);
        } catch (Throwable t) {
            throw new RuntimeException("Cozycraft Records failed to initialize", t);
        }
    }

    private void register(Object event) {
        try {
            Class<?> regs = Class.forName("net.minecraft.core.registries.Registries");
            Object soundKey = regs.getField("SOUND_EVENT").get(null);
            Object itemKey = regs.getField("ITEM").get(null);
            Object current = event.getClass().getMethod("getRegistryKey").invoke(event);
            if (same(current, soundKey)) {
                registerEntries(event, soundKey, (helper, id) -> {
                    Class<?> ident = Class.forName("net.minecraft.resources.Identifier");
                    Object key = ident.getMethod("fromNamespaceAndPath", String.class, String.class).invoke(null, MOD_ID, id);
                    Class<?> sound = Class.forName("net.minecraft.sounds.SoundEvent");
                    Object value = sound.getMethod("createVariableRangeEvent", ident).invoke(null, key);
                    helperRegister(helper, key, value);
                });
            } else if (same(current, itemKey)) {
                registerEntries(event, itemKey, (helper, id) -> helperRegister(helper, makeIdentifier(id), makeItem(id)));
            }
        } catch (Throwable t) {
            throw new RuntimeException("Cozycraft Records registry registration failed", t);
        }
    }

    private void creative(Object event) {
        try {
            Class<?> tabs = Class.forName("net.minecraft.world.item.CreativeModeTabs");
            Object ingredients = tabs.getField("INGREDIENTS").get(null);
            Object tabKey = event.getClass().getMethod("getTabKey").invoke(event);
            if (!ingredients.equals(tabKey)) return;
            Class<?> builtins = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object registry = builtins.getField("ITEM").get(null);
            Method get = findMethod(registry.getClass(), "get", 1);
            Method accept = findAccept(event.getClass());
            for (String id : IDS) {
                Object item = get.invoke(registry, makeIdentifier(id));
                if (item != null) accept.invoke(event, item);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Cozycraft Records creative tab registration failed", t);
        }
    }

    private Object makeItem(String id) throws Exception {
        Class<?> ident = Class.forName("net.minecraft.resources.Identifier");
        Class<?> resourceKey = Class.forName("net.minecraft.resources.ResourceKey");
        Class<?> regs = Class.forName("net.minecraft.core.registries.Registries");
        Class<?> item = Class.forName("net.minecraft.world.item.Item");
        Class<?> props = Class.forName("net.minecraft.world.item.Item$Properties");
        Object identifier = ident.getMethod("fromNamespaceAndPath", String.class, String.class).invoke(null, MOD_ID, id);
        Object itemKey = resourceKey.getMethod("create", Class.class, Object.class).invoke(null, regs.getField("ITEM").get(null), identifier);
        Object songKey = resourceKey.getMethod("create", Class.class, Object.class).invoke(null, regs.getField("JUKEBOX_SONG").get(null), identifier);
        Object p = props.getDeclaredConstructor().newInstance();
        invokeCompatible(p, "setId", itemKey);
        invokeCompatible(p, "stacksTo", Integer.valueOf(1));
        invokeCompatible(p, "jukeboxPlayable", songKey);
        return item.getConstructor(props).newInstance(p);
    }

    private interface Registrar { void put(Object helper, String id) throws Exception; }

    private void registerEntries(Object event, Object registryKey, Registrar registrar) throws Exception {
        Method reg = null;
        for (Method m : event.getClass().getMethods()) {
            if (m.getName().equals("register") && m.getParameterCount() == 2 && m.getParameterTypes()[0].isAssignableFrom(registryKey.getClass())) { reg = m; break; }
        }
        if (reg == null) throw new NoSuchMethodException("RegisterEvent.register");
        Consumer<Object> consumer = helper -> { try { for (String id : IDS) registrar.put(helper, id); } catch (Throwable t) { throw new RuntimeException(t); } };
        reg.invoke(event, registryKey, consumer);
    }

    private static Object makeIdentifier(String id) throws Exception {
        Class<?> ident = Class.forName("net.minecraft.resources.Identifier");
        return ident.getMethod("fromNamespaceAndPath", String.class, String.class).invoke(null, MOD_ID, id);
    }

    private static void helperRegister(Object helper, Object id, Object value) throws Exception {
        for (Method m : helper.getClass().getMethods()) {
            if (!m.getName().equals("register") || m.getParameterCount() != 2) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p[0].isAssignableFrom(id.getClass()) && p[1].isAssignableFrom(value.getClass())) { m.invoke(helper, id, value); return; }
        }
        throw new NoSuchMethodException("RegisterHelper.register");
    }

    private static boolean same(Object a, Object b) { return a == b || (a != null && a.equals(b)) || String.valueOf(a).equals(String.valueOf(b)); }

    private static void addTypedListener(Object bus, Class<?> type, Consumer<Object> listener) throws Exception {
        for (Method m : bus.getClass().getMethods()) {
            if (m.getName().equals("addListener") && m.getParameterCount() == 2 && m.getParameterTypes()[0] == Class.class && Consumer.class.isAssignableFrom(m.getParameterTypes()[1])) { m.invoke(bus, type, listener); return; }
        }
        throw new NoSuchMethodException("IEventBus.addListener");
    }

    private static Method findMethod(Class<?> c, String name, int count) throws NoSuchMethodException {
        for (Class<?> x = c; x != null; x = x.getSuperclass()) for (Method m : x.getMethods()) if (m.getName().equals(name) && m.getParameterCount() == count) return m;
        throw new NoSuchMethodException(name);
    }

    private static Method findAccept(Class<?> c) throws NoSuchMethodException {
        for (Method m : c.getMethods()) if (m.getName().equals("accept") && m.getParameterCount() == 1) {
            String n = m.getParameterTypes()[0].getName();
            if (n.equals("net.minecraft.world.item.ItemLike") || n.equals("net.minecraft.world.item.Item")) return m;
        }
        throw new NoSuchMethodException("CreativeModeTab.Output.accept");
    }

    private static Object invokeCompatible(Object target, String name, Object arg) throws Exception {
        for (Method m : target.getClass().getMethods()) if (m.getName().equals(name) && m.getParameterCount() == 1 && wrap(m.getParameterTypes()[0]).isAssignableFrom(arg.getClass())) return m.invoke(target, arg);
        throw new NoSuchMethodException(name);
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class; if (c == long.class) return Long.class; if (c == boolean.class) return Boolean.class;
        if (c == float.class) return Float.class; if (c == double.class) return Double.class; if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class; if (c == char.class) return Character.class; return c;
    }
}
