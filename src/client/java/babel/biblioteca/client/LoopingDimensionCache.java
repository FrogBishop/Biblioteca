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
    private static final Set<ResourceKey<Level>> voidFog = new HashSet<>();
    private static final Set<ResourceKey<Level>> skyLight = new HashSet<>();

    private LoopingDimensionCache() {}

    /** Replaces every set with the server's current snapshot. */
    public static void applyPayload(LoopingStatePayload payload) {
        fill(loopingDimensions, payload.loopingDimensions());
        fill(beaconIgnoresSky, payload.beaconIgnoresSky());
        fill(voidFog, payload.voidFog());
        fill(skyLight, payload.skyLight());
        LoopingRegistry.bumpRevision();
    }

    /** Drops what the last server said; called on disconnect. */
    public static void clear() {
        loopingDimensions.clear();
        beaconIgnoresSky.clear();
        voidFog.clear();
        skyLight.clear();
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

    public static boolean beaconsIgnoreSky(ResourceKey<Level> dimension) {
        return beaconIgnoresSky.contains(dimension);
    }

    public static boolean showsVoidFog(ResourceKey<Level> dimension) {
        return voidFog.contains(dimension);
    }

    public static boolean keepsSkyLight(ResourceKey<Level> dimension) {
        return skyLight.contains(dimension);
    }
}
