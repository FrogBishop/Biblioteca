package babel.biblioteca.client;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;
import babel.biblioteca.network.LoopingStatePayload;
import java.nio.file.Path;

public final class BibliotecaClient {

    private BibliotecaClient() {}

    public static void initialize(Path configDirectory) {
        LoopingClientSettings.setConfigDirectory(configDirectory);
        // Wire the client checkers into the shared LoopingRegistry.
        LoopingRegistry.setClientChecker(LoopingDimensionCache::isLooping);
        LoopingRegistry.setClientBeaconChecker(LoopingDimensionCache::beaconsIgnoreSky);
        LoopingRegistry.setClientNoVoidFogChecker(LoopingDimensionCache::hasNoVoidFog);
        LoopingRegistry.setClientSkyLightModeGetter(LoopingDimensionCache::getSkyLightMode);
    }

    /** Called on the client thread by each loader's network handler. */
    public static void applyPayload(Minecraft client, LoopingStatePayload payload) {
        var dimension = client.level == null ? null : client.level.dimension();
        boolean looping = dimension != null && LoopingDimensionCache.isLooping(dimension);
        var skyMode = dimension == null ? null : LoopingDimensionCache.getSkyLightMode(dimension);
        LoopingDimensionCache.applyPayload(payload);
        if (dimension != null && (looping != LoopingDimensionCache.isLooping(dimension)
                || skyMode != LoopingDimensionCache.getSkyLightMode(dimension))) {
            //? if >=26.2 {
            client.levelExtractor.allChanged();
            //?} else {
            /*client.levelRenderer.allChanged();
            *///?}
        }
    }
}
