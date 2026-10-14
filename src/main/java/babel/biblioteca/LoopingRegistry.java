package babel.biblioteca;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

/**
 * Unified runtime check for "is this dimension Y-looping?".
 * Server side: delegates to LoopingDimensionManager.
 * Client side: delegates to LoopingDimensionCache.
 * Both register their delegate at their respective initializers.
 */
public final class LoopingRegistry {

    private static Predicate<ResourceKey<Level>> serverCheck = dim -> false;
    private static Predicate<ResourceKey<Level>> clientCheck = dim -> false;

    private LoopingRegistry() {}

    public static void setServerChecker(Predicate<ResourceKey<Level>> check) {
        serverCheck = check;
    }

    public static void setClientChecker(Predicate<ResourceKey<Level>> check) {
        clientCheck = check;
    }

    /** Picks the right delegate based on which side the level is on. */
    public static boolean isLooping(Level level) {
        return level.isClientSide()
                ? clientCheck.test(level.dimension())
                : serverCheck.test(level.dimension());
    }

    public static boolean isLoopingClient(ResourceKey<Level> dim) {
        return clientCheck.test(dim);
    }
}
