package babel.biblioteca.client;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.network.LoopingStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entrypoint: receives the per-dimension attributes and wires them into
 * {@link LoopingRegistry}, the shared side-agnostic facade the mixins call.
 */
public class BibliotecaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                LoopingStatePayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> LoopingDimensionCache.applyPayload(payload)));

        LoopingRegistry.setClientChecker(LoopingDimensionCache::isLooping);
        LoopingRegistry.setClientBeaconChecker(LoopingDimensionCache::beaconsIgnoreSky);
        LoopingRegistry.setClientVoidFogChecker(LoopingDimensionCache::showsVoidFog);
        LoopingRegistry.setClientSkyLightChecker(LoopingDimensionCache::keepsSkyLight);

        // Attributes belong to the server we are connected to; drop them on disconnect so the next
        // server's payload is not mixed with the previous one.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(LoopingDimensionCache::clear));
    }
}
