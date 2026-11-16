package babel.biblioteca.client.neoforge;

import babel.biblioteca.client.BibliotecaClient;
import babel.biblioteca.client.LoopingDimensionCache;
import babel.biblioteca.neoforge.NeoForgeTransport;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Loaded only on the physical client; old network handlers remain safe on dedicated servers. */
public final class NeoForgeBibliotecaClient {
    private NeoForgeBibliotecaClient() {}

    public static void initialize() {
        BibliotecaClient.initialize(FMLPaths.CONFIGDIR.get());
        String renderers = java.util.stream.Stream.of("sodium", "embeddium", "iris", "voxy")
                .filter(ModList.get()::isLoaded).collect(java.util.stream.Collectors.joining(", "));
        babel.biblioteca.client.ClientDiagnostics.setRendererMods(renderers.isEmpty() ? "vanilla" : renderers);
        NeoForgeTransport.receiver = payload -> BibliotecaClient.applyPayload(Minecraft.getInstance(), payload);
        NeoForge.EVENT_BUS.addListener(NeoForgeBibliotecaClient::disconnected);
    }

    private static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        LoopingDimensionCache.clear();
    }
}
