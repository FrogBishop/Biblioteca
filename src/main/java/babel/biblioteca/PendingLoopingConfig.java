package babel.biblioteca;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
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
    private static final Set<ResourceKey<Level>> pendingVoidFog = new HashSet<>();
    private static final Set<ResourceKey<Level>> pendingSkyLight = new HashSet<>();

    private PendingLoopingConfig() {}

    /** Called from LoopingConfigScreen when the player confirms their choices. */
    public static void set(
            Set<ResourceKey<Level>> looping,
            Set<ResourceKey<Level>> beaconIgnoresSky,
            Set<ResourceKey<Level>> voidFog,
            Set<ResourceKey<Level>> skyLight) {
        pendingLooping.clear();
        pendingLooping.addAll(looping);
        pendingBeaconIgnoresSky.clear();
        pendingBeaconIgnoresSky.addAll(beaconIgnoresSky);
        pendingVoidFog.clear();
        pendingVoidFog.addAll(voidFog);
        pendingSkyLight.clear();
        pendingSkyLight.addAll(skyLight);
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

    public static Set<ResourceKey<Level>> getPendingVoidFog() {
        return Set.copyOf(pendingVoidFog);
    }

    public static Set<ResourceKey<Level>> getPendingSkyLight() {
        return Set.copyOf(pendingSkyLight);
    }

    /** Consume and clear — called once the server has applied the pending config. */
    public static void consume() {
        hasPending = false;
        pendingLooping.clear();
        pendingBeaconIgnoresSky.clear();
        pendingVoidFog.clear();
        pendingSkyLight.clear();
    }
}
