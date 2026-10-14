package babel.biblioteca.client;

import babel.biblioteca.network.LoopingStatePayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-side mirror of the server's LoopingWorldData.
 * Populated by LoopingStatePayload S2C packets.
 */
public final class LoopingDimensionCache {

    private static final Set<ResourceKey<Level>> loopingDimensions = new HashSet<>();

    private LoopingDimensionCache() {}

    public static void applyPayload(LoopingStatePayload payload) {
        loopingDimensions.clear();
        for (String key : payload.loopingDimensions()) {
            Identifier loc = Identifier.parse(key);
            loopingDimensions.add(ResourceKey.create(Registries.DIMENSION, loc));
        }
    }

    public static boolean isLooping(ResourceKey<Level> dimension) {
        return loopingDimensions.contains(dimension);
    }

    public static Set<ResourceKey<Level>> getLoopingDimensions() {
        return Set.copyOf(loopingDimensions);
    }
}
