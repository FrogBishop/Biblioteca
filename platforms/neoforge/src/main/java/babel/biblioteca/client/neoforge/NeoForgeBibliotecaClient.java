package babel.biblioteca.client.neoforge;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.client.BibliotecaClient;
import babel.biblioteca.client.LoopingDimensionCache;
import babel.biblioteca.network.LoopingStatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Biblioteca.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeBibliotecaClient {
    public NeoForgeBibliotecaClient(IEventBus modBus) {
        BibliotecaClient.initialize(FMLPaths.CONFIGDIR.get());
        modBus.addListener(NeoForgeBibliotecaClient::registerPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(NeoForgeBibliotecaClient::disconnected);
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(LoopingStatePayload.TYPE,
                (payload, context) -> BibliotecaClient.applyPayload(Minecraft.getInstance(), payload));
    }

    private static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        LoopingDimensionCache.clear();
    }
}
