package babel.biblioteca.validation.neoforge;

import babel.biblioteca.client.LoopingClientSettings;
import babel.biblioteca.client.LoopingDimensionCache;
import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.validation.SeamFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.nio.file.Files;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class NeoForgeClientChecks {
    /** NeoForge's item hooks consult the client singleton even in isolated world fixtures. */
    public static AutoCloseable installMinecraftFixture() throws Exception {
        var singleton = Arrays.stream(Minecraft.class.getDeclaredFields())
                .filter(f -> f.getType() == Minecraft.class && Modifier.isStatic(f.getModifiers())).findFirst().orElseThrow();
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        singleton.set(null, SeamFixture.allocate(Minecraft.class));
        return () -> singleton.set(null, previous);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void run(LoopingStatePayload payload) throws Exception {
        // Receiving the initial snapshot before a client level exists must still populate the cache.
        Minecraft client = (Minecraft) SeamFixture.allocate(Minecraft.class);
        var singleton = Arrays.stream(Minecraft.class.getDeclaredFields())
                .filter(f -> f.getType() == Minecraft.class && Modifier.isStatic(f.getModifiers())).findFirst().orElseThrow();
        singleton.setAccessible(true);
        Object previousClient = singleton.get(null);
        var handlers = NetworkRegistry.class.getDeclaredField("CLIENTBOUND_HANDLERS");
        handlers.setAccessible(true);
        IPayloadHandler<LoopingStatePayload> handler = (IPayloadHandler) ((Map<?, ?>) ((Map<?, ?>) handlers.get(null))
                .get(ConnectionProtocol.PLAY)).get(LoopingStatePayload.ID);
        if (handler == null) throw new AssertionError("Client payload handler not registered");
        int[] enqueued = {0};
        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, values) -> {
                    if (method.getName().equals("enqueueWork") && values[0] instanceof Runnable task) {
                        enqueued[0]++;
                        task.run();
                        return CompletableFuture.completedFuture(null);
                    }
                    throw new AssertionError("Unexpected context operation: " + method);
                });
        singleton.set(null, client);
        try { handler.handle(payload, context); }
        finally { singleton.set(null, previousClient); }
        if (enqueued[0] != 1) throw new AssertionError("Payload handler did not enqueue on the client thread");
        if (!LoopingDimensionCache.isLooping(babel.biblioteca.Compat.dimensionKey(babel.biblioteca.Compat.id("test", "orbit")))) {
            throw new AssertionError("Initial payload was not applied before level creation");
        }
        NeoForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggingOut(null, null, null));
        if (LoopingDimensionCache.isLooping(babel.biblioteca.Compat.dimensionKey(babel.biblioteca.Compat.id("test", "orbit")))) {
            throw new AssertionError("NeoForge disconnect retained the previous server's state");
        }
        LoopingClientSettings.setPreloadSections(5);
        var configDir = FMLPaths.CONFIGDIR.get();
        if (!Files.isRegularFile(configDir.resolve("biblioteca-client.json"))) throw new AssertionError("Wrong configuration directory");
        LoopingClientSettings.setConfigDirectory(configDir);
        if (LoopingClientSettings.preloadSections() != 5) throw new AssertionError("Settings failed to persist/reload");
        LoopingClientSettings.setPreloadSections(99);
        if (LoopingClientSettings.preloadSections() != LoopingClientSettings.MAX_PRELOAD_SECTIONS) throw new AssertionError("Preload upper bound");
        LoopingClientSettings.setPreloadSections(-1);
        if (LoopingClientSettings.preloadSections() != 0) throw new AssertionError("Preload lower bound");
        System.out.println("DATA_OK NeoForge client: initial sync, disconnect event, configuration location and persistence");
    }
}
