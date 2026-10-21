package babel.biblioteca.client;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.network.LoopingStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class BibliotecaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Receive S2C looping-state packet and populate the client-side cache.
        //? if >=1.20.5 {
        ClientPlayNetworking.registerGlobalReceiver(
                LoopingStatePayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> LoopingDimensionCache.applyPayload(payload)
                )
        );
        //?} else {
        /*ClientPlayNetworking.registerGlobalReceiver(
                LoopingStatePayload.ID,
                (client, handler, buffer, responseSender) -> {
                    LoopingStatePayload payload = LoopingStatePayload.fromBuffer(buffer);
                    client.execute(() -> LoopingDimensionCache.applyPayload(payload));
                }
        );
        *///?}

        // Wire the client checkers into the shared LoopingRegistry.
        LoopingRegistry.setClientChecker(LoopingDimensionCache::isLooping);
        LoopingRegistry.setClientBeaconChecker(LoopingDimensionCache::beaconsIgnoreSky);
        LoopingRegistry.setClientNoVoidFogChecker(LoopingDimensionCache::hasNoVoidFog);
        LoopingRegistry.setClientNoSkyLightChecker(LoopingDimensionCache::hasNoSkyLight);

        // Attributes belong to the server we are connected to; drop them on disconnect so the next
        // server's payload is not mixed with the previous one.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(LoopingDimensionCache::clear));
    }
}
