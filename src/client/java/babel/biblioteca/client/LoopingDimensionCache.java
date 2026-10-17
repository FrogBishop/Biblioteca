package babel.biblioteca.client;

import babel.biblioteca.LoopingRegistry;
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
    private static final Set<ResourceKey<Level>> beaconIgnoresSky = new HashSet<>();

    private LoopingDimensionCache() {}

    public static void applyPayload(LoopingStatePayload payload) {
        fill(loopingDimensions, payload.loopingDimensions());
        fill(beaconIgnoresSky, payload.beaconIgnoresSky());
        LoopingRegistry.bumpRevision();
    }

    private static void fill(Set<ResourceKey<Level>> target, Set<String> keys) {
        target.clear();
        for (String key : keys) {
            target.add(ResourceKey.create(Registries.DIMENSION, Identifier.parse(key)));
        }
    }

    public static boolean isLooping(ResourceKey<Level> dimension) {
        return loopingDimensions.contains(dimension);
    }

    public static Set<ResourceKey<Level>> getLoopingDimensions() {
        return Set.copyOf(loopingDimensions);
    }

    public static boolean beaconsIgnoreSky(ResourceKey<Level> dimension) {
        return beaconIgnoresSky.contains(dimension);
    }

    public static Set<ResourceKey<Level>> getBeaconIgnoresSky() {
        return Set.copyOf(beaconIgnoresSky);
    }
}
