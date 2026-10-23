package babel.biblioteca.world;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
//? if >=26.1 {
import net.minecraft.world.level.storage.SavedDataStorage;
//?} else {
/*import net.minecraft.world.level.storage.DimensionDataStorage;
*///?}

import java.util.Set;

/**
 * Server-side singleton that owns the LoopingWorldData and exposes the per-dimension
 * attributes to the rest of the mod.
 */
public final class LoopingDimensionManager {

    private static volatile LoopingWorldData serverData = null;

    /**
     * Immutable snapshots of the attributes, replaced on every change.
     *
     * The live data is a HashSet mutated on the server thread, while readers include chunk and
     * light worker threads (the light engine asks whether its dimension loops). Reading the mutable
     * set from those threads is a data race, so it is republished as an immutable set instead:
     * readers take a volatile reference to a set that never changes under them.
     */
    private static volatile Set<ResourceKey<Level>> loopingSnapshot = Set.of();
    private static volatile Set<ResourceKey<Level>> beaconIgnoresSkySnapshot = Set.of();
    private static volatile Set<ResourceKey<Level>> noVoidFogSnapshot = Set.of();
    private static volatile Set<ResourceKey<Level>> noSkyLightSnapshot = Set.of();

    private LoopingDimensionManager() {}

    public static void onServerStarted(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            Biblioteca.LOGGER.error("[Biblioteca] Could not find overworld when loading looping data!");
            return;
        }
        //? if >=26.1 {
        SavedDataStorage storage = overworld.getDataStorage();
        serverData = storage.computeIfAbsent(LoopingWorldData.TYPE);
        //?} else if >=1.21.5 {
        /*DimensionDataStorage storage = overworld.getDataStorage();
        serverData = storage.computeIfAbsent(LoopingWorldData.TYPE);
        *///?} else if >=1.20.2 {
        /*DimensionDataStorage storage = overworld.getDataStorage();
        serverData = storage.computeIfAbsent(LoopingWorldData.TYPE, "biblioteca_looping");
        *///?} else if >=1.17 {
        /*serverData = overworld.getDataStorage().computeIfAbsent(LoopingWorldData::decode,
                LoopingWorldData::empty, "biblioteca_looping");
        *///?} else {
        /*serverData = overworld.getDataStorage().computeIfAbsent(LoopingWorldData::empty, "biblioteca_looping");
        *///?}
        republishSnapshots();

        Biblioteca.LOGGER.info("[Biblioteca] Looping dimensions: {}", loopingSnapshot);
        Biblioteca.LOGGER.info("[Biblioteca] Beacon ignores sky: {}", beaconIgnoresSkySnapshot);
        Biblioteca.LOGGER.info("[Biblioteca] No void fog: {}", noVoidFogSnapshot);
        Biblioteca.LOGGER.info("[Biblioteca] No sky light: {}", noSkyLightSnapshot);

        // The data only became readable now, so anything cached before this point is stale.
        LoopingRegistry.bumpRevision();
    }

    public static void onServerStopped() {
        serverData = null;
        republishSnapshots();
        LoopingRegistry.bumpRevision();
    }

    private static void republishSnapshots() {
        LoopingWorldData data = serverData;
        loopingSnapshot = data == null ? Set.of() : data.getLoopingDimensions();
        beaconIgnoresSkySnapshot = data == null ? Set.of() : data.getBeaconIgnoresSky();
        noVoidFogSnapshot = data == null ? Set.of() : data.getNoVoidFogDimensions();
        noSkyLightSnapshot = data == null ? Set.of() : data.getNoSkyLightDimensions();
    }

    public static boolean isLooping(ResourceKey<Level> dimension) {
        return loopingSnapshot.contains(dimension);
    }

    public static void setLooping(ResourceKey<Level> dimension, boolean enabled) {
        if (serverData != null) {
            serverData.setLooping(dimension, enabled);
            republishSnapshots();
            LoopingRegistry.bumpRevision();
        }
    }

    public static Set<ResourceKey<Level>> getLoopingDimensions() {
        return loopingSnapshot;
    }

    public static boolean beaconIgnoresSky(ResourceKey<Level> dimension) {
        return beaconIgnoresSkySnapshot.contains(dimension);
    }

    public static void setBeaconIgnoresSky(ResourceKey<Level> dimension, boolean ignored) {
        if (serverData != null) {
            serverData.setBeaconIgnoresSky(dimension, ignored);
            republishSnapshots();
            LoopingRegistry.bumpRevision();
        }
    }

    public static Set<ResourceKey<Level>> getBeaconIgnoresSkyDimensions() {
        return beaconIgnoresSkySnapshot;
    }

    public static boolean hasNoVoidFog(ResourceKey<Level> dimension) {
        return noVoidFogSnapshot.contains(dimension);
    }

    public static void setNoVoidFog(ResourceKey<Level> dimension, boolean disabled) {
        if (serverData != null) {
            serverData.setNoVoidFog(dimension, disabled);
            republishSnapshots();
            LoopingRegistry.bumpRevision();
        }
    }

    public static Set<ResourceKey<Level>> getNoVoidFogDimensions() {
        return noVoidFogSnapshot;
    }

    public static boolean hasNoSkyLight(ResourceKey<Level> dimension) {
        return noSkyLightSnapshot.contains(dimension);
    }

    public static void setNoSkyLight(ResourceKey<Level> dimension, boolean disabled) {
        if (serverData != null) {
            serverData.setNoSkyLight(dimension, disabled);
            republishSnapshots();
            LoopingRegistry.bumpRevision();
        }
    }

    public static Set<ResourceKey<Level>> getNoSkyLightDimensions() {
        return noSkyLightSnapshot;
    }
}
