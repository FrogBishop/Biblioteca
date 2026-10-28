package babel.biblioteca.validation.save;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.world.LoopingDimensionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Test-only mod: observe a real server saving loaded periodic chunks and releasing its world. */
public final class SaveLifecycleChecks {
    private static final BlockPos MARKER = new BlockPos(8, 160, 8);
    private static boolean started, ready, enteredShutdown, flushed, failed;
    private static int unloaded;
    private static long shutdownStart;
    private static HybridDigChecks hybridDig;

    private static BlockPos marker() {
        return Boolean.getBoolean("biblioteca.saveTest.hybridDig") ? new BlockPos(24, 160, 24) : MARKER;
    }

    private SaveLifecycleChecks() {}

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

    public static void started(MinecraftServer server) {
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
            check(level.getBlockState(marker()).is(Blocks.DIAMOND_BLOCK), "saved block did not survive reopening");
            System.out.println("SAVE_TEST_RELOADED block and dimension attributes preserved");
        } else {
            check(level.setBlockAndUpdate(marker(), Blocks.DIAMOND_BLOCK.defaultBlockState()), "could not write isolated save marker");
        }
        if (Boolean.getBoolean("biblioteca.saveTest.hybridDig")) {
            hybridDig = new HybridDigChecks(level, System.getProperty("biblioteca.saveTest.stage", "write").equals("read"));
        }
        started = true;
    }

    public static void tick(MinecraftServer server) {
        if (started && !ready && server.getTickCount() >= 40) {
            if (hybridDig != null && !hybridDig.tick()) return;
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

    public static void unloaded(Level level) {
        if (enteredShutdown && level instanceof net.minecraft.server.level.ServerLevel) {
            attributes("level unload");
            unloaded++;
        }
    }

    public static void stopped(MinecraftServer server) {
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
