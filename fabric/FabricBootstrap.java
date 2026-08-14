package cozycraft;

import java.lang.reflect.*;
import java.util.function.*;

public final class FabricBootstrap {
    private static final String[] IDS = {"warm_overworld", "echoes_below", "around_the_campfire"};
    private FabricBootstrap() {}

    public static void init() {
        Bootstrap.init();
        try {
            Class<?> tabs = Class.forName("net.minecraft.world.item.CreativeModeTabs");
            Object ingredients = tabs.getField("INGREDIENTS").get(null);
            Class<?> events = Class.forName("net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents");
            Object event = events.getMethod("modifyOutputEvent", Class.forName("net.minecraft.resources.ResourceKey")).invoke(null, ingredients);
            Class<?> listenerType = Class.forName("net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents$ModifyOutput");
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType}, (proxy, method, args) -> {
                if (method.getName().equals("modifyOutput") && args != null && args.length == 1) {
                    Object output = args[0];
                    Class<?> registries = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                    Object itemRegistry = registries.getField("ITEM").get(null);
                    Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
                    Method get = findMethod(itemRegistry.getClass(), "get", 1);
                    for (String id : IDS) {
                        Object identifier = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class).invoke(null, "cozycraft_records", id);
                        Object item = get.invoke(itemRegistry, identifier);
                        if (item != null) findAccept(output.getClass(), item).invoke(output, item);
                    }
                    return null;
                }
                return null;
            });
            event.getClass().getMethod("register", listenerType).invoke(event, listener);
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
