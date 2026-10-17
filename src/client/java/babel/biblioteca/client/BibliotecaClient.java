package babel.biblioteca.client;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.network.LoopingStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

public class BibliotecaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Receive S2C looping-state packet and populate the client-side cache.
        ClientPlayNetworking.registerGlobalReceiver(
                LoopingStatePayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> LoopingDimensionCache.applyPayload(payload)
                )
        );

        // Wire the client checkers into the shared LoopingRegistry.
        LoopingRegistry.setClientChecker(LoopingDimensionCache::isLooping);
        LoopingRegistry.setClientBeaconChecker(LoopingDimensionCache::beaconsIgnoreSky);

        // Inject the "Dimension Attributes…" button into the world-creation screen.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof CreateWorldScreen cws) {
                CreateWorldScreenInjector.inject(cws, scaledWidth, scaledHeight);
            }
        });
    }
}
