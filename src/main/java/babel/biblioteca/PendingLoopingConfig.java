package babel.biblioteca;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds the per-dimension attribute choices made on the world-creation screen so that
 * MinecraftServerMixin can apply them the first time a brand-new world loads.
 *
 * This is only meaningful in singleplayer (same JVM), which is also the only
 * context where the world-creation screen is shown.
 */
public final class PendingLoopingConfig {

    private static boolean hasPending = false;
    private static final Set<ResourceKey<Level>> pendingLooping = new HashSet<>();
    private static final Set<ResourceKey<Level>> pendingBeaconIgnoresSky = new HashSet<>();
    private static final Set<ResourceKey<Level>> pendingNoVoidFog = new HashSet<>();
    private static final Map<ResourceKey<Level>, SkyLightMode> pendingSkyLightModes = new HashMap<>();

    private PendingLoopingConfig() {}

    /** Called from LoopingConfigScreen when the player confirms their choices. */
    public static void set(
            Set<ResourceKey<Level>> looping,
            Set<ResourceKey<Level>> beaconIgnoresSky,
            Set<ResourceKey<Level>> noVoidFog,
            Map<ResourceKey<Level>, SkyLightMode> skyLightModes) {
        pendingLooping.clear();
        pendingLooping.addAll(looping);
        pendingBeaconIgnoresSky.clear();
        pendingBeaconIgnoresSky.addAll(beaconIgnoresSky);
        pendingNoVoidFog.clear();
        pendingNoVoidFog.addAll(noVoidFog);
        pendingSkyLightModes.clear();
        pendingSkyLightModes.putAll(skyLightModes);
        hasPending = true;
    }

    public static boolean hasPending() {
        return hasPending;
    }

    public static Set<ResourceKey<Level>> getPendingLooping() {
        return Set.copyOf(pendingLooping);
    }

    public static Set<ResourceKey<Level>> getPendingBeaconIgnoresSky() {
        return Set.copyOf(pendingBeaconIgnoresSky);
    }

    public static Set<ResourceKey<Level>> getPendingNoVoidFog() {
        return Set.copyOf(pendingNoVoidFog);
    }

    public static Map<ResourceKey<Level>, SkyLightMode> getPendingSkyLightModes() {
        return Map.copyOf(pendingSkyLightModes);
    }

    /** Consume and clear — called once the server has applied the pending config. */
    public static void consume() {
        hasPending = false;
        pendingLooping.clear();
        pendingBeaconIgnoresSky.clear();
        pendingNoVoidFog.clear();
        pendingSkyLightModes.clear();
    }
}
