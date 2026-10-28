package babel.biblioteca.validation.save;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.world.LoopingDimensionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Test-only mod: observe a real server saving loaded periodic chunks and releasing its world. */
@Mod("biblioteca_save_check")
public final class SaveLifecycleProbe {
    private static final BlockPos MARKER = new BlockPos(8, 160, 8);
    private static boolean started, ready, enteredShutdown, flushed, failed;
    private static int unloaded;
    private static long shutdownStart;

    public SaveLifecycleProbe() {
        NeoForge.EVENT_BUS.addListener(SaveLifecycleProbe::started);
        NeoForge.EVENT_BUS.addListener(SaveLifecycleProbe::tick);
        NeoForge.EVENT_BUS.addListener(SaveLifecycleProbe::unloaded);
        NeoForge.EVENT_BUS.addListener(SaveLifecycleProbe::stopped);
    }

    private static void check(boolean value, String message) {
        if (!value) {
            failed = true;
            System.out.println("SAVE_TEST_FAILURE " + message);
            throw new AssertionError(message);
        }
    }

    private static void attributes(String phase) {
        check(LoopingDimensionManager.isLooping(Level.OVERWORLD), phase + ": looping state cleared before chunk shutdown");
        check(LoopingDimensionManager.getSkyLightMode(Level.OVERWORLD) == SkyLightMode.HYBRID,
                phase + ": sky mode changed before chunk shutdown");
    }

    private static void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        boolean newWorld = Boolean.getBoolean("biblioteca.saveTest.newWorld");
        if (newWorld && System.getProperty("biblioteca.saveTest.stage", "write").equals("write")) {
            LoopingDimensionManager.setLooping(Level.OVERWORLD, true);
            LoopingDimensionManager.setSkyLightMode(Level.OVERWORLD, SkyLightMode.HYBRID);
            LoopingDimensionManager.setBeaconIgnoresSky(Level.OVERWORLD, true);
            LoopingDimensionManager.setNoVoidFog(Level.OVERWORLD, true);
        }
        attributes("world load");
        if (newWorld) {
            check(LoopingDimensionManager.beaconIgnoresSky(Level.OVERWORLD)
                    && LoopingDimensionManager.hasNoVoidFog(Level.OVERWORLD), "additional attributes survive reload");
        }
        var level = server.overworld();
        check(LoopingRegistry.isLooping(level), "loaded level sees the saved periodic topology");
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            level.setChunkForced(x, z, true);
            level.getChunk(x, z);
        }
        if (System.getProperty("biblioteca.saveTest.stage", "write").equals("read")) {
            check(level.getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK), "saved block did not survive reopening");
            System.out.println("SAVE_TEST_RELOADED block and dimension attributes preserved");
        } else {
            check(level.setBlockAndUpdate(MARKER, Blocks.DIAMOND_BLOCK.defaultBlockState()), "could not write isolated save marker");
        }
        started = true;
    }

    private static void tick(ServerTickEvent.Post event) {
        if (started && !ready && event.getServer().getTickCount() >= 40) {
            ready = true;
            System.out.println("SAVE_TEST_READY 49 forced chunks, native lighting and saved hybrid topology");
        }
    }

    /** Called after stopServer's HEAD injections, before native chunk draining begins. */
    public static void beforeShutdown() {
        check(ready, "server stopped before the loaded-world test became ready");
        shutdownStart = System.nanoTime();
        enteredShutdown = true;
        attributes("begin shutdown");
        System.out.println("SAVE_TEST_DRAINING periodic attributes retained");
    }

    public static void afterChunkFlush() {
        attributes("after chunk flush");
        flushed = true;
        System.out.println("SAVE_TEST_FLUSHED native saveAllChunks returned");
    }

    private static void unloaded(LevelEvent.Unload event) {
        if (enteredShutdown && event.getLevel() instanceof net.minecraft.server.level.ServerLevel) {
            attributes("level unload");
            unloaded++;
        }
    }

    private static void stopped(ServerStoppedEvent event) {
        check(!failed && enteredShutdown && flushed && unloaded > 0, "shutdown failed before saving/unloading completed");
        check(LoopingDimensionManager.getLoopingDimensions().isEmpty()
                        && LoopingDimensionManager.getSkyLightModes().isEmpty()
                        && LoopingDimensionManager.getBeaconIgnoresSkyDimensions().isEmpty()
                        && LoopingDimensionManager.getNoVoidFogDimensions().isEmpty(),
                "closed world left stale dimension attributes");
        long millis = (System.nanoTime() - shutdownStart) / 1_000_000;
        System.out.println("SAVE_TEST_OK stage=" + System.getProperty("biblioteca.saveTest.stage", "write")
                + " shutdownMs=" + millis + " unloadedLevels=" + unloaded);
    }
}
