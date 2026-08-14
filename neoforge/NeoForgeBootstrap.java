package cozycraft;

import java.lang.reflect.*;
import java.util.function.*;

@net.neoforged.fml.common.Mod("cozycraft_records")
public final class NeoForgeBootstrap {
    private static final String[] IDS = {"warm_overworld", "echoes_below", "around_the_campfire"};
    public NeoForgeBootstrap() {
        Bootstrap.init();
        try {
            Class<?> context = Class.forName("net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext");
            Object ctx = context.getMethod("get").invoke(null);
            Object bus = ctx.getClass().getMethod("getModEventBus").invoke(ctx);
            Class<?> consumer = Consumer.class;
            Consumer<Object> listener = event -> {
                try {
                    Class<?> tabEvent = Class.forName("net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent");
                    if (!tabEvent.isInstance(event)) return;
                    Class<?> tabs = Class.forName("net.minecraft.world.item.CreativeModeTabs");
                    Object ingredients = tabs.getField("INGREDIENTS").get(null);
                    Object key = event.getClass().getMethod("getTabKey").invoke(event);
                    if (!ingredients.equals(key)) return;
                    Object registry = Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField("ITEM").get(null);
                    Class<?> identifier = Class.forName("net.minecraft.resources.Identifier");
                    Method get = findMethod(registry.getClass(), "get", 1);
                    for (String id : IDS) {
                        Object ident = identifier.getMethod("fromNamespaceAndPath", String.class, String.class).invoke(null, "cozycraft_records", id);
                        Object item = get.invoke(registry, ident);
                        if (item != null) findAccept(event.getClass(), item).invoke(event, item);
                    }
                } catch (Throwable t) { throw new RuntimeException(t); }
            };
            bus.getClass().getMethod("addListener", Consumer.class).invoke(bus, listener);
        } catch (Throwable t) {
            throw new RuntimeException("Cozycraft Records creative tab registration failed", t);
        }
    }
    private static Method findMethod(Class<?> c, String name, int count) throws NoSuchMethodException {
        for (Class<?> x = c; x != null; x = x.getSuperclass()) for (Method m : x.getMethods())
            if (m.getName().equals(name) && m.getParameterCount() == count) { m.setAccessible(true); return m; }
        throw new NoSuchMethodException(name);
    }
    private static Method findAccept(Class<?> c, Object value) throws NoSuchMethodException {
        for (Method m : c.getMethods()) if (m.getName().equals("accept") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(value.getClass())) { m.setAccessible(true); return m; }
        throw new NoSuchMethodException("accept for " + value.getClass());
    }
}
