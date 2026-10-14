package babel.biblioteca;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds the per-dimension looping choices made on the world-creation screen
 * so that MinecraftServerMixin can apply them the first time a brand-new world loads.
 *
 * This is only meaningful in singleplayer (same JVM), which is also the only
 * context where the world-creation screen is shown.
 */
public final class PendingLoopingConfig {

    private static boolean hasPending = false;
    private static final Set<ResourceKey<Level>> pendingEnabled = new HashSet<>();

    private PendingLoopingConfig() {}

    /** Called from LoopingConfigScreen when the player confirms their choices. */
    public static void set(Set<ResourceKey<Level>> enabled) {
        pendingEnabled.clear();
        pendingEnabled.addAll(enabled);
        hasPending = true;
    }

    public static boolean hasPending() {
        return hasPending;
    }

    public static Set<ResourceKey<Level>> getPendingEnabled() {
        return Set.copyOf(pendingEnabled);
    }

    /** Consume and clear — called once the server has applied the pending config. */
    public static void consume() {
        hasPending = false;
        pendingEnabled.clear();
    }
}
