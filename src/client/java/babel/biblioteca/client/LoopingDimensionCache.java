package babel.biblioteca.client;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.network.LoopingStatePayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    private record Snapshot(Set<ResourceKey<Level>> looping, Set<ResourceKey<Level>> beacon,
                            Set<ResourceKey<Level>> noVoidFog, Map<ResourceKey<Level>, SkyLightMode> skyModes) {}
    private static final Snapshot EMPTY = new Snapshot(Set.of(), Set.of(), Set.of(), Map.of());
    // Light and mesh workers read a complete immutable snapshot while the client replaces it.
    private static volatile Snapshot snapshot = EMPTY;

    private LoopingDimensionCache() {}

    /** Replaces every set with the server's current snapshot. */
    public static void applyPayload(LoopingStatePayload payload) {
        snapshot = new Snapshot(keys(payload.loopingDimensions()), keys(payload.beaconIgnoresSky()),
                keys(payload.noVoidFog()), payload.skyLightModes().entrySet().stream().collect(
                        Collectors.toUnmodifiableMap(entry -> key(entry.getKey()), Map.Entry::getValue)));
        LoopingRegistry.bumpRevision();
    }

    /** Drops what the last server said; called on disconnect. */
    public static void clear() {
        snapshot = EMPTY;
        LoopingRegistry.bumpRevision();
    }

    private static ResourceKey<Level> key(String id) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
    }

    private static Set<ResourceKey<Level>> keys(Set<String> keys) {
        return keys.stream().map(LoopingDimensionCache::key).collect(Collectors.toUnmodifiableSet());
    }

    public static boolean isLooping(ResourceKey<Level> dimension) {
        return snapshot.looping().contains(dimension);
    }

    public static boolean beaconsIgnoreSky(ResourceKey<Level> dimension) {
        return snapshot.beacon().contains(dimension);
    }

    public static boolean hasNoVoidFog(ResourceKey<Level> dimension) {
        return snapshot.noVoidFog().contains(dimension);
    }

    public static SkyLightMode getSkyLightMode(ResourceKey<Level> dimension) {
        return snapshot.skyModes().getOrDefault(dimension, SkyLightMode.DEFAULT);
    }
}
