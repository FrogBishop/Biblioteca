package babel.biblioteca.validation.neoforge;

import babel.biblioteca.Compat;
import babel.biblioteca.client.LoopingClientSettings;
import babel.biblioteca.client.LoopingDimensionCache;
import babel.biblioteca.validation.SeamFixture;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class NeoForgeClientChecks {
    public static AutoCloseable installMinecraftFixture() throws Exception {
        var singleton = Arrays.stream(Minecraft.class.getDeclaredFields())
                .filter(f -> f.getType() == Minecraft.class && Modifier.isStatic(f.getModifiers())).findFirst().orElseThrow();
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        Minecraft client = (Minecraft) SeamFixture.allocate(Minecraft.class);
        for (var field : Minecraft.class.getDeclaredFields()) {
            if (field.getType().getSimpleName().equals("SearchRegistry") && !Modifier.isStatic(field.getModifiers())) {
                var constructor = field.getType().getDeclaredConstructor(); constructor.setAccessible(true);
                field.setAccessible(true); field.set(client, constructor.newInstance());
            }
        }
        for (Class<?> type = Minecraft.class; type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) if (field.getType() == Thread.class && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true); field.set(client, Thread.currentThread());
            }
        }
        singleton.set(null, client);
        return () -> singleton.set(null, previous);
    }

    public static void run() throws Exception {
        var key = Compat.dimensionKey(Compat.id("test", "orbit"));
        if (!LoopingDimensionCache.isLooping(key)) throw new AssertionError("Native transport did not apply initial state before level creation");
        NeoForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggingOut(null, null, null));
        if (LoopingDimensionCache.isLooping(key)) throw new AssertionError("Native disconnect event retained state");
        var config = FMLPaths.CONFIGDIR.get();
        LoopingClientSettings.setPreloadSections(5);
        if (!Files.isRegularFile(config.resolve("biblioteca-client.json"))) throw new AssertionError("Wrong config location");
        LoopingClientSettings.setConfigDirectory(config);
        if (LoopingClientSettings.preloadSections() != 5) throw new AssertionError("Config did not reload");
        LoopingClientSettings.setPreloadSections(99);
        if (LoopingClientSettings.preloadSections() != LoopingClientSettings.MAX_PRELOAD_SECTIONS) throw new AssertionError("Config clamp");
        LoopingClientSettings.setPreloadSections(-1);
        if (LoopingClientSettings.preloadSections() != 0) throw new AssertionError("Config lower clamp");
        System.out.println("DATA_OK NeoForge legacy client: real registered handler, initial sync, disconnect and config persistence");
    }
}
