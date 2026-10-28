package babel.biblioteca.client.fabric;

import babel.biblioteca.client.BibliotecaClient;
import babel.biblioteca.client.LoopingDimensionCache;
import babel.biblioteca.network.LoopingStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricBibliotecaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BibliotecaClient.initialize(FabricLoader.getInstance().getConfigDir());
        //? if >=1.20.5 {
        ClientPlayNetworking.registerGlobalReceiver(LoopingStatePayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> BibliotecaClient.applyPayload(context.client(), payload)));
        //?} else {
        /*ClientPlayNetworking.registerGlobalReceiver(LoopingStatePayload.ID,
                (client, handler, buffer, responseSender) -> {
                    LoopingStatePayload payload = LoopingStatePayload.fromBuffer(buffer);
                    client.execute(() -> BibliotecaClient.applyPayload(client, payload));
                });
        *///?}
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(LoopingDimensionCache::clear));
    }
}
