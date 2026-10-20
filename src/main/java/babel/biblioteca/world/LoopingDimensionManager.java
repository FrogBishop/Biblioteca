package babel.biblioteca.world;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.SavedDataStorage;

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
    private static volatile Set<ResourceKey<Level>> voidFogSnapshot = Set.of();
    private static volatile Set<ResourceKey<Level>> skyLightSnapshot = Set.of();

    private LoopingDimensionManager() {}

    public static void onServerStarted(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            Biblioteca.LOGGER.error("[Biblioteca] Could not find overworld when loading looping data!");
            return;
        }
        SavedDataStorage storage = overworld.getDataStorage();
        serverData = storage.computeIfAbsent(LoopingWorldData.TYPE);
        republishSnapshots();

        Biblioteca.LOGGER.info("[Biblioteca] Looping dimensions: {}", loopingSnapshot);
        Biblioteca.LOGGER.info("[Biblioteca] Beacon ignores sky: {}", beaconIgnoresSkySnapshot);
        Biblioteca.LOGGER.info("[Biblioteca] Void fog: {}", voidFogSnapshot);
        Biblioteca.LOGGER.info("[Biblioteca] Sky light: {}", skyLightSnapshot);

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
        voidFogSnapshot = data == null ? Set.of() : data.getVoidFogDimensions();
        skyLightSnapshot = data == null ? Set.of() : data.getSkyLightDimensions();
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

    public static boolean showsVoidFog(ResourceKey<Level> dimension) {
        return voidFogSnapshot.contains(dimension);
    }

    public static void setVoidFog(ResourceKey<Level> dimension, boolean shown) {
        if (serverData != null) {
            serverData.setVoidFog(dimension, shown);
            republishSnapshots();
            LoopingRegistry.bumpRevision();
        }
    }

    public static Set<ResourceKey<Level>> getVoidFogDimensions() {
        return voidFogSnapshot;
    }

    public static boolean keepsSkyLight(ResourceKey<Level> dimension) {
        return skyLightSnapshot.contains(dimension);
    }

    public static void setSkyLight(ResourceKey<Level> dimension, boolean kept) {
        if (serverData != null) {
            serverData.setSkyLight(dimension, kept);
            republishSnapshots();
            LoopingRegistry.bumpRevision();
        }
    }

    public static Set<ResourceKey<Level>> getSkyLightDimensions() {
        return skyLightSnapshot;
    }
}
