package babel.biblioteca.world;

import babel.biblioteca.Biblioteca;
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

    private static LoopingWorldData serverData = null;

    private LoopingDimensionManager() {}

    public static void onServerStarted(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            Biblioteca.LOGGER.error("[Biblioteca] Could not find overworld when loading looping data!");
            return;
        }
        SavedDataStorage storage = overworld.getDataStorage();
        serverData = storage.computeIfAbsent(LoopingWorldData.TYPE);
        Biblioteca.LOGGER.info("[Biblioteca] Looping dimensions: {}", serverData.getLoopingDimensions());
        Biblioteca.LOGGER.info("[Biblioteca] Beacon ignores sky: {}", serverData.getBeaconIgnoresSky());
    }

    public static void onServerStopped() {
        serverData = null;
    }

    public static boolean isLooping(ResourceKey<Level> dimension) {
        return serverData != null && serverData.isLooping(dimension);
    }

    public static void setLooping(ResourceKey<Level> dimension, boolean enabled) {
        if (serverData != null) {
            serverData.setLooping(dimension, enabled);
        }
    }

    public static Set<ResourceKey<Level>> getLoopingDimensions() {
        return serverData == null ? Set.of() : serverData.getLoopingDimensions();
    }

    public static boolean beaconIgnoresSky(ResourceKey<Level> dimension) {
        return serverData != null && serverData.beaconIgnoresSky(dimension);
    }

    public static void setBeaconIgnoresSky(ResourceKey<Level> dimension, boolean ignored) {
        if (serverData != null) {
            serverData.setBeaconIgnoresSky(dimension, ignored);
        }
    }

    public static Set<ResourceKey<Level>> getBeaconIgnoresSkyDimensions() {
        return serverData == null ? Set.of() : serverData.getBeaconIgnoresSky();
    }
}
