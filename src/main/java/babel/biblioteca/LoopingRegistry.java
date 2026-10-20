package babel.biblioteca;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.function.Predicate;

/**
 * Unified runtime check for the per-dimension attributes of this mod.
 * Server side: delegates to LoopingDimensionManager.
 * Client side: delegates to LoopingDimensionCache.
 * Both register their delegates at their respective initializers.
 */
public final class LoopingRegistry {

    private static Predicate<ResourceKey<Level>> serverCheck = dim -> false;
    private static Predicate<ResourceKey<Level>> clientCheck = dim -> false;

    private static Predicate<ResourceKey<Level>> serverBeaconCheck = dim -> false;
    private static Predicate<ResourceKey<Level>> clientBeaconCheck = dim -> false;

    private static Predicate<ResourceKey<Level>> serverVoidFogCheck = dim -> false;
    private static Predicate<ResourceKey<Level>> clientVoidFogCheck = dim -> false;

    private static Predicate<ResourceKey<Level>> serverSkyLightCheck = dim -> false;
    private static Predicate<ResourceKey<Level>> clientSkyLightCheck = dim -> false;

    /** Bumped whenever a dimension attribute changes. Lets hot paths cache the answer. */
    private static volatile int revision;

    private LoopingRegistry() {}

    /** The current attribute revision. A cached answer is valid while this does not change. */
    public static int revision() {
        return revision;
    }

    public static void bumpRevision() {
        revision++;
    }

    public static void setServerChecker(Predicate<ResourceKey<Level>> check) {
        serverCheck = check;
    }

    public static void setClientChecker(Predicate<ResourceKey<Level>> check) {
        clientCheck = check;
    }

    public static void setServerBeaconChecker(Predicate<ResourceKey<Level>> check) {
        serverBeaconCheck = check;
    }

    public static void setClientBeaconChecker(Predicate<ResourceKey<Level>> check) {
        clientBeaconCheck = check;
    }

    public static void setServerVoidFogChecker(Predicate<ResourceKey<Level>> check) {
        serverVoidFogCheck = check;
    }

    public static void setClientVoidFogChecker(Predicate<ResourceKey<Level>> check) {
        clientVoidFogCheck = check;
    }

    public static void setServerSkyLightChecker(Predicate<ResourceKey<Level>> check) {
        serverSkyLightCheck = check;
    }

    public static void setClientSkyLightChecker(Predicate<ResourceKey<Level>> check) {
        clientSkyLightCheck = check;
    }

    /** Picks the right delegate based on which side the level is on. */
    public static boolean isLooping(Level level) {
        if (level instanceof LoopingLevel cached) {
            return cached.biblioteca$isLooping();
        }

        return isLoopingUncached(level);
    }

    /** The uncached answer. Only the Level-side cache should call this. */
    public static boolean isLoopingUncached(Level level) {
        return level.isClientSide()
                ? clientCheck.test(level.dimension())
                : serverCheck.test(level.dimension());
    }

    /**
     * For code that only has a LevelAccessor (block behaviour, features, level helpers).
     * Anything that is not a Level cannot be a looping dimension.
     */
    public static boolean isLooping(LevelAccessor accessor) {
        return accessor instanceof Level level && isLooping(level);
    }

    /** Does a beacon beam in this dimension skip the vanilla "must see the sky" rule? */
    public static boolean beaconsIgnoreSky(Level level) {
        return level.isClientSide()
                ? clientBeaconCheck.test(level.dimension())
                : serverBeaconCheck.test(level.dimension());
    }

    /** Should the vanilla void-fog darkening be shown in this dimension? */
    public static boolean showsVoidFog(Level level) {
        return level.isClientSide()
                ? clientVoidFogCheck.test(level.dimension())
                : serverVoidFogCheck.test(level.dimension());
    }

    /** Should the vanilla sky-light channel be kept in this dimension? */
    public static boolean keepsSkyLight(Level level) {
        return level.isClientSide()
                ? clientSkyLightCheck.test(level.dimension())
                : serverSkyLightCheck.test(level.dimension());
    }

    public static boolean isLoopingClient(ResourceKey<Level> dim) {
        return clientCheck.test(dim);
    }

    public static boolean beaconsIgnoreSkyClient(ResourceKey<Level> dim) {
        return clientBeaconCheck.test(dim);
    }
}
