package babel.biblioteca;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.function.Predicate;
import java.util.function.Function;

/**
 * Side-agnostic access to the four per-dimension attributes.
 *
 * <p>The server answers from {@link babel.biblioteca.world.LoopingDimensionManager}, the client
 * from the client's {@code LoopingDimensionCache}; each registers its predicates at its
 * own initializer. Code in a mixin can therefore ask one method without knowing which side it runs
 * on, which is what lets the same block, collision and light hooks serve singleplayer, a client and
 * a dedicated server.
 */
public final class LoopingRegistry {

    private static Predicate<ResourceKey<Level>> serverCheck = dim -> false;
    private static Predicate<ResourceKey<Level>> clientCheck = dim -> false;

    private static Predicate<ResourceKey<Level>> serverBeaconCheck = dim -> false;
    private static Predicate<ResourceKey<Level>> clientBeaconCheck = dim -> false;

    private static Predicate<ResourceKey<Level>> serverNoVoidFogCheck = dim -> false;
    private static Predicate<ResourceKey<Level>> clientNoVoidFogCheck = dim -> false;

    private static Function<ResourceKey<Level>, SkyLightMode> serverSkyLightMode = dim -> SkyLightMode.DEFAULT;
    private static Function<ResourceKey<Level>, SkyLightMode> clientSkyLightMode = dim -> SkyLightMode.DEFAULT;

    /** Bumped whenever a dimension attribute changes. Lets hot paths cache the answer. */
    private static volatile int revision;

    private LoopingRegistry() {}

    /**
     * The current attribute revision. A cached answer is valid while this does not change.
     *
     * <p>Incremented on every change, including the payload replacing the whole client cache, so a
     * cache only has to compare one int.
     */
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

    public static void setServerNoVoidFogChecker(Predicate<ResourceKey<Level>> check) {
        serverNoVoidFogCheck = check;
    }

    public static void setClientNoVoidFogChecker(Predicate<ResourceKey<Level>> check) {
        clientNoVoidFogCheck = check;
    }

    public static void setServerSkyLightModeGetter(Function<ResourceKey<Level>, SkyLightMode> getter) {
        serverSkyLightMode = getter;
    }

    public static void setClientSkyLightModeGetter(Function<ResourceKey<Level>, SkyLightMode> getter) {
        clientSkyLightMode = getter;
    }

    /**
     * Whether the Y axis of this level loops.
     *
     * <p>A Level normally answers from its revision-checked cache. A Level without the mixin
     * falls back to the side's predicate. The LevelAccessor overload only accepts accessors
     * that are also Levels.
     */
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

    /** Should the vanilla void-fog darkening be disabled in this dimension? */
    public static boolean hasNoVoidFog(Level level) {
        return level.isClientSide()
                ? clientNoVoidFogCheck.test(level.dimension())
                : serverNoVoidFogCheck.test(level.dimension());
    }

    /** The selected sky source rule; consumers apply it only in looping dimensions. */
    public static SkyLightMode skyLightMode(Level level) {
        return level.isClientSide()
                ? clientSkyLightMode.apply(level.dimension())
                : serverSkyLightMode.apply(level.dimension());
    }
}
