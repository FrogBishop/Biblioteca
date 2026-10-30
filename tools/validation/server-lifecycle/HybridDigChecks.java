package babel.biblioteca.validation.save;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

/** Drives normal Level.setBlockAndUpdate calls and waits for the real asynchronous light worker. */
public final class HybridDigChecks {
    private static final int[] BASES = {0, 40, -40};
    private final ServerLevel level;
    private final boolean reloaded;
    private int scenario;
    private int phase;
    private int ticks;
    private boolean complete;

    public HybridDigChecks(ServerLevel level, boolean reloaded) {
        this.level = level;
        this.reloaded = reloaded;
        if (!reloaded) buildSolidVolume();
    }

    public boolean tick() {
        if (complete) return true;
        ticks++;
        if (ticks < 30 || level.getChunkSource().getLightEngine().hasLightWork()) {
            if (ticks > 300) fail("light worker did not settle");
            return false;
        }
        ticks = 0;
        if (reloaded) {
            for (int base : BASES) expectOpenShaft(base, "reloaded");
            System.out.println("HYBRID_DIG_OK reloaded 8x8 shafts: all direct-sky samples are 15");
            complete = true;
            return true;
        }
        int base = BASES[scenario];
        if (phase == 0) {
            expect(base + 8, 16, base + 8, 0, "solid volume");
            // Remove the entire 8x8 column, including the physical bottom and empty upper rows.
            for (int y = level.getMaxY(); y >= level.getMinY(); y--)
                for (int x = base + 4; x < base + 12; x++) for (int z = base + 4; z < base + 12; z++)
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            System.out.println("HYBRID_DIG_EXCAVATED base=" + base + " 8x8 full-height shaft");
            phase = 1;
        } else if (phase == 1) {
            expectOpenShaft(base, "after digging");
            roof(base, true);
            phase = 2;
        } else if (phase == 2) {
            expect(base + 8, 16, base + 8, 0, "roof replaced");
            roof(base, false);
            phase = 3;
        } else {
            expectOpenShaft(base, "roof removed");
            System.out.println("HYBRID_DIG_OK base=" + base + " dig, close roof and reopen roof");
            if (++scenario == BASES.length) {
                complete = true;
                return true;
            }
            phase = 0;
            buildSolidVolume();
        }
        return false;
    }

    private void buildSolidVolume() {
        int base = BASES[scenario];
        for (int y = 0; y < 32; y++) for (int x = base; x < base + 16; x++) for (int z = base; z < base + 16; z++)
            level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
        // Copied player worlds can contain water beside the shaft. Keep a solid wall
        // through the whole period so fluid flow cannot invalidate the all-air premise.
        for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
            for (int z = base + 3; z <= base + 12; z++) {
                level.setBlockAndUpdate(new BlockPos(base + 3, y, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(base + 12, y, z), Blocks.STONE.defaultBlockState());
            }
            for (int x = base + 4; x < base + 12; x++) {
                level.setBlockAndUpdate(new BlockPos(x, y, base + 3), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, y, base + 12), Blocks.STONE.defaultBlockState());
            }
        }
    }

    private void roof(int base, boolean solid) {
        for (int x = base + 4; x < base + 12; x++) for (int z = base + 4; z < base + 12; z++)
            level.setBlockAndUpdate(new BlockPos(x, 40, z), (solid ? Blocks.STONE : Blocks.AIR).defaultBlockState());
    }

    private void expectOpenShaft(int base, String phase) {
        for (int x = base + 4; x < base + 12; x++) for (int z = base + 4; z < base + 12; z++)
            for (int y : new int[]{level.getMinY(), -32, 0, 15, 16, 31, 40, level.getMaxY()})
                expect(x, y, z, 15, phase);
    }

    private void expect(int x, int y, int z, int expected, String phase) {
        BlockPos pos = new BlockPos(x, y, z);
        int actual = level.getBrightness(LightLayer.SKY, pos);
        if (actual != expected) {
            int sourceY = level.getChunk(x >> 4, z >> 4).getSkyLightSources().getLowestSourceY(x & 15, z & 15);
            fail(phase + " at " + x + "," + y + "," + z + ": sky=" + actual
                    + " expected=" + expected + " heightmapSourceY=" + sourceY
                    + " block=" + level.getBlockState(pos) + " cachedSourceY=" + cachedSource(x, z)
                    + " blockers=" + columnBlocks(x, z));
        }
    }

    private String columnBlocks(int x, int z) {
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (int y = level.getMaxY(); y >= level.getMinY(); y--) {
            var state = level.getBlockState(new BlockPos(x, y, z));
            if (!state.isAir() && count++ < 8) result.append(y).append(':').append(state).append(';');
        }
        return result + " total=" + count;
    }

    private Object cachedSource(int x, int z) {
        try {
            Object engine = level.getChunkSource().getLightEngine().getLayerListener(LightLayer.SKY);
            var field = engine.getClass().getDeclaredField("biblioteca$sources");
            field.setAccessible(true);
            var sources = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>) field.get(engine);
            int[] values = (int[]) sources.get(babel.biblioteca.Compat.chunkKey(x >> 4, z >> 4));
            return values == null ? "missing" : values[(x & 15) + (z & 15) * 16];
        } catch (ReflectiveOperationException exception) {
            return exception.getClass().getSimpleName();
        }
    }

    private static void fail(String message) {
        System.out.println("HYBRID_DIG_FAILURE " + message);
        throw new AssertionError(message);
    }
}
