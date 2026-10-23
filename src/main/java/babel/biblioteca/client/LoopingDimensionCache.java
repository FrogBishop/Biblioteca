package babel.biblioteca.client;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.Compat;
import babel.biblioteca.network.LoopingStatePayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-side mirror of the server's {@code LoopingWorldData}, populated by
 * {@link LoopingStatePayload}.
 *
 * <p>The client only ever asks whether a dimension has an attribute, never which dimensions have
 * it, so only the predicates are exposed. The cache starts empty, is replaced as a whole by each
 * payload, and is cleared on disconnect so the next server's attributes are not mixed with the
 * previous server's.
 */
public final class LoopingDimensionCache {

    private static final Set<ResourceKey<Level>> loopingDimensions = new HashSet<>();
    private static final Set<ResourceKey<Level>> beaconIgnoresSky = new HashSet<>();
    private static final Set<ResourceKey<Level>> noVoidFog = new HashSet<>();
    private static final Set<ResourceKey<Level>> noSkyLight = new HashSet<>();

    private LoopingDimensionCache() {}

    /** Replaces every set with the server's current snapshot. */
    public static void applyPayload(LoopingStatePayload payload) {
        fill(loopingDimensions, payload.loopingDimensions());
        fill(beaconIgnoresSky, payload.beaconIgnoresSky());
        fill(noVoidFog, payload.noVoidFog());
        fill(noSkyLight, payload.noSkyLight());
        LoopingRegistry.bumpRevision();
    }

    /** Drops what the last server said; called on disconnect. */
    public static void clear() {
        loopingDimensions.clear();
        beaconIgnoresSky.clear();
        noVoidFog.clear();
        noSkyLight.clear();
        LoopingRegistry.bumpRevision();
    }

    private static void fill(Set<ResourceKey<Level>> target, Set<String> keys) {
        target.clear();
        for (String key : keys) {
            target.add(Compat.dimensionKey(Compat.parseId(key)));
        }
    }

    public static boolean isLooping(ResourceKey<Level> dimension) {
        return loopingDimensions.contains(dimension);
    }

    public static boolean beaconsIgnoreSky(ResourceKey<Level> dimension) {
        return beaconIgnoresSky.contains(dimension);
    }

    public static boolean hasNoVoidFog(ResourceKey<Level> dimension) {
        return noVoidFog.contains(dimension);
    }

    public static boolean hasNoSkyLight(ResourceKey<Level> dimension) {
        return noSkyLight.contains(dimension);
    }
}
