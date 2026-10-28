package babel.biblioteca.validation.main100;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SkyLightMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises native light storage, height maps, queues and packet layers through the real mixins. */
public final class SkyLightChecks {
    public static void run() throws Exception {
        for (int period : new int[]{16, 384}) {
            closedAndOpenColumns(period);
            boundaryPropagation(period);
        }
        chunkBoundaries();
        newChunkLifecycle();
        hybridModes();
        batchRebuildQueueBound();
        System.out.println("PASS: periodic skylight sources, occlusion, bidirectional seam propagation, convergence, saved chunks, packets and topology changes");
    }

    /** A loaded 3x3 region must not queue its entire lit volume again on a mode change. */
    private static void batchRebuildQueueBound() throws Exception {
        Field getter = LoopingRegistry.class.getDeclaredField("serverSkyLightMode");
        getter.setAccessible(true);
        Object previous = getter.get(null);
        AtomicReference<SkyLightMode> mode = new AtomicReference<>(SkyLightMode.LOOPING);
        LoopingRegistry.setServerSkyLightModeGetter(dim -> mode.get());
        try {
            Fixture f = new Fixture(384, false);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) f.load(x, z, true);
            f.drain();
            f.expect(8, f.top - 1, 8, 15);
            mode.set(SkyLightMode.HYBRID);
            LoopingRegistry.bumpRevision();
            var sky = f.engine.getLayerListener(LightLayer.SKY);
            ((babel.biblioteca.LoopingSkyLight) sky).biblioteca$prepareSkyUpdates();
            int queuedLongs = ((babel.biblioteca.mixin.LightEngineAccessor) sky).biblioteca$decreaseQueue().size();
            require(queuedLongs <= 200000, "bulk rebuild work scales with the region boundary, not all 884736 cells: " + queuedLongs);
            f.drain();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) f.expect(x * 16 + 8, f.min + 1, z * 16 + 8, 15);
            mode.set(SkyLightMode.DISABLED); LoopingRegistry.bumpRevision(); f.drain();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) f.expect(x * 16 + 8, f.top - 1, z * 16 + 8, 0);
            mode.set(SkyLightMode.LOOPING); LoopingRegistry.bumpRevision(); f.drain();
            f.expect(8, f.top - 1, 8, 15);
            System.out.println("PASS: 3x3 384-row sky rebuild: bounded boundary queue, hybrid/disabled/looping convergence");
        } finally {
            getter.set(null, previous);
            LoopingRegistry.bumpRevision();
        }
    }

    private static void closedAndOpenColumns(int period) throws Exception {
        Fixture f = new Fixture(period, false);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) f.put(x, f.min, z, Blocks.BEDROCK.defaultBlockState());
        f.load(0, 0, true); f.drain();
        f.expect(8, f.top - 1, 8, 0); f.expect(8, f.min + 1, 8, 0); f.expect(8, f.top, 8, 0);
        f.put(8, f.min, 8, Blocks.AIR.defaultBlockState()); f.drain();
        f.expect(8, f.min, 8, 15); f.expect(8, f.top - 1, 8, 15);
        f.expect(9, f.min + 1, 8, 14); f.expect(9, f.min, 8, 0);
        require(f.view.canSeeSky(new BlockPos(8, f.top, 8)), "Open periodic column can see sky");
        f.put(8, f.top - 1, 8, Blocks.STONE.defaultBlockState()); f.drain();
        f.expect(8, f.min + 1, 8, 0); f.expect(9, f.top - 1, 8, 0);
        require(!f.view.canSeeSky(new BlockPos(8, f.top, 8)), "Closed periodic column cannot see sky");
        f.put(8, f.top - 1, 8, Blocks.AIR.defaultBlockState());
        f.put(8, f.min, 8, Blocks.WATER.defaultBlockState()); f.drain();
        f.expect(8, f.top - 1, 8, 0);
        f.put(8, f.min, 8, Blocks.GLASS.defaultBlockState()); f.drain();
        f.expect(8, f.top - 1, 8, 15);
        f.put(8, f.min, 8, Blocks.TINTED_GLASS.defaultBlockState()); f.drain();
        f.expect(8, f.top - 1, 8, 0);
        SeamFixture.data(f.level).looping = false; LoopingRegistry.bumpRevision();
        require(f.engine.hasLightWork(), "Disabling looping schedules lighting"); f.drain();
        f.expect(8, f.top, 8, 15); f.expect(8, f.top - 1, 8, 15); f.expect(8, f.min, 8, 0);
        SeamFixture.data(f.level).looping = true; LoopingRegistry.bumpRevision(); f.drain();
        f.expect(8, f.top - 1, 8, 0);

        Fixture client = new Fixture(period, true);
        SeamFixture.data(client.level).looping = false; LoopingRegistry.bumpRevision();
        client.load(0, 0, false);
        for (int sy = f.level.getMinSectionY(); sy <= f.level.getMaxSectionY(); sy++) {
            SectionPos pos = SectionPos.of(0, sy, 0);
            DataLayer data = f.engine.getLayerListener(LightLayer.SKY).getDataLayerData(pos);
            require(data != null, "Server sends explicit sky layers for empty sections");
            client.engine.queueSectionData(LightLayer.SKY, pos, data.copy());
        }
        client.drain();
        SeamFixture.data(client.level).looping = true; LoopingRegistry.bumpRevision(); client.drain();
        client.expect(8, client.top - 1, 8, 0); client.expect(8, client.top, 8, 0);
    }

    public static Fixture tunnel(int period) throws Exception {
        Fixture f = new Fixture(period, false);
        for (int y = f.min; y < f.top; y++) {
            f.put(5, y, 8, Blocks.STONE.defaultBlockState()); f.put(7, y, 8, Blocks.STONE.defaultBlockState());
            f.put(6, y, 7, Blocks.STONE.defaultBlockState()); f.put(6, y, 9, Blocks.STONE.defaultBlockState());
        }
        f.put(6, f.min + period / 2, 8, Blocks.STONE.defaultBlockState());
        f.put(5, f.top - 2, 8, Blocks.AIR.defaultBlockState());
        f.load(0, 0, false); f.drain(); return f;
    }

    private static void boundaryPropagation(int period) throws Exception {
        Fixture f = tunnel(period);
        f.expect(6, f.top - 2, 8, 13); f.expect(6, f.top - 1, 8, 12);
        f.expect(6, f.min, 8, 11); f.expect(6, f.min + 1, 8, 10); f.expect(6, f.top + 1, 8, 10);
        require(f.notifications.contains(SectionPos.of(0, f.level.getMinSectionY(), 0)), "Seam neighbour mesh invalidation");
        f.put(5, f.top - 2, 8, Blocks.STONE.defaultBlockState());
        f.put(5, f.min + 1, 8, Blocks.AIR.defaultBlockState()); f.drain();
        f.expect(6, f.min + 1, 8, 13); f.expect(6, f.min, 8, 12);
        f.expect(6, f.top - 1, 8, 11); f.expect(6, f.top - 2, 8, 10);
        f.put(5, f.min + 1, 8, Blocks.STONE.defaultBlockState()); f.drain();
        f.expect(6, f.min, 8, 0); f.expect(6, f.top - 1, 8, 0);
        f.put(6, f.top - 1, 8, Blocks.TORCH.defaultBlockState()); f.drain();
        require(f.engine.getLayerListener(LightLayer.BLOCK).getLightValue(new BlockPos(6, f.min, 8)) == 13, "Block light still crosses the seam");
        f.put(6, f.top - 1, 8, Blocks.AIR.defaultBlockState()); f.drain();
        require(f.engine.getLayerListener(LightLayer.BLOCK).getLightValue(new BlockPos(6, f.min, 8)) == 0, "Removed block light also converges");
        Field check = LoopingRegistry.class.getDeclaredField("serverSkyLightMode"); check.setAccessible(true);
        Object saved = check.get(null);
        try {
            LoopingRegistry.setServerSkyLightModeGetter(dim -> SkyLightMode.DISABLED); f.expect(4, f.top, 8, 0);
            LoopingRegistry.setServerSkyLightModeGetter(dim -> SkyLightMode.LOOPING); f.expect(4, f.top, 8, 15);
        } finally { check.set(null, saved); LoopingRegistry.bumpRevision(); }
    }

    private static void hybridModes() throws Exception {
        Field getter = LoopingRegistry.class.getDeclaredField("serverSkyLightMode"); getter.setAccessible(true);
        Object saved = getter.get(null);
        AtomicReference<SkyLightMode> mode = new AtomicReference<>(SkyLightMode.HYBRID);
        LoopingRegistry.setServerSkyLightModeGetter(dim -> mode.get());
        try {
            for (int period : new int[]{16, 384}) {
                Fixture f = new Fixture(period, false);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) f.put(x, f.min, z, Blocks.BEDROCK.defaultBlockState());
                f.load(0, 0, true); f.drain();
                f.expect(8, f.min + 1, 8, 15); f.expect(8, f.top - 1, 8, 15); f.expect(8, f.top, 8, 0);
                for (SkyLightMode next : new SkyLightMode[]{SkyLightMode.LOOPING, SkyLightMode.DISABLED, SkyLightMode.HYBRID}) {
                    mode.set(next); LoopingRegistry.bumpRevision();
                    require(f.engine.hasLightWork(), "Changing the sky rule schedules a rebuild without changing looping");
                    f.drain(); f.expect(8, f.min + 1, 8, next == SkyLightMode.HYBRID ? 15 : 0);
                }
                int roof = f.min + period / 2;
                f.put(8, roof, 8, Blocks.STONE.defaultBlockState()); f.drain();
                f.expect(8, roof + 1, 8, 15); f.expect(8, roof, 8, 0); f.expect(8, roof - 1, 8, 14);
                require(!f.view.canSeeSky(new BlockPos(8, roof - 1, 8)), "Diffuse sky below a roof is not vanilla exposure");
                f.put(8, roof, 8, Blocks.GLASS.defaultBlockState()); f.drain(); f.expect(8, roof - 1, 8, 15);
                f.put(8, roof, 8, Blocks.TINTED_GLASS.defaultBlockState()); f.drain(); f.expect(8, roof - 1, 8, 14);
                f.put(8, roof, 8, Blocks.AIR.defaultBlockState()); f.drain(); f.expect(8, roof - 1, 8, 15);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) f.put(x, f.top - 1, z, Blocks.STONE.defaultBlockState());
                f.drain(); f.expect(8, f.top - 2, 8, 0); f.expect(8, f.min + 1, 8, 0);
                f.put(8, f.top - 1, 8, Blocks.AIR.defaultBlockState()); f.drain(); f.expect(8, f.min + 1, 8, 15);
                hybridSeam(period);
            }
            Fixture custom = new Fixture(0, 256, false);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) custom.put(x, 0, z, Blocks.BEDROCK.defaultBlockState());
            custom.load(0, 0, false); custom.drain(); custom.expect(8, 255, 8, 15); custom.expect(8, 256, 8, 0);
            mode.set(SkyLightMode.DISABLED); custom.drain(); custom.expect(8, 255, 8, 0);
            SeamFixture.data(custom.level).looping = false; LoopingRegistry.bumpRevision(); custom.drain(); custom.expect(8, 255, 8, 15);
        } finally { getter.set(null, saved); LoopingRegistry.bumpRevision(); }
        System.out.println("PASS: hybrid sky uses vanilla exposure above bedrock, dynamic roofs and glass, both seam directions, 3-mode rebuilds and custom world heights");
    }

    private static void hybridSeam(int period) throws Exception {
        Fixture f = tunnel(period);
        // Water blocks direct sky in this tube without blocking diffuse light crossing the seam.
        f.put(6, f.top - 1, 8, Blocks.WATER.defaultBlockState()); f.drain();
        f.expect(6, f.top - 2, 8, 13); f.expect(6, f.top - 1, 8, 12);
        f.expect(6, f.min, 8, 11); f.expect(6, f.min + 1, 8, 10);
        f.put(5, f.top - 2, 8, Blocks.STONE.defaultBlockState());
        f.put(5, f.min + 1, 8, Blocks.AIR.defaultBlockState()); f.drain();
        f.expect(6, f.min + 1, 8, 13); f.expect(6, f.min, 8, 12);
        f.expect(6, f.top - 1, 8, 11); f.expect(6, f.top - 2, 8, 10);
        f.put(5, f.min + 1, 8, Blocks.STONE.defaultBlockState()); f.drain();
        f.expect(6, f.min, 8, 0); f.expect(6, f.top - 1, 8, 0);
        f.put(6, f.top - 1, 8, Blocks.GLASS.defaultBlockState()); f.drain();
        f.expect(6, f.top - 2, 8, 15); f.expect(6, f.min, 8, 14); f.expect(6, f.min + 1, 8, 13);
    }

    private static void newChunkLifecycle() throws Exception {
        Fixture f = new Fixture(384, false);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) f.put(x, f.min, z, Blocks.BEDROCK.defaultBlockState());
        f.load(0, 0, false); f.drain(); f.expect(8, f.top - 1, 8, 0);
        SeamFixture.data(f.level).looping = false; LoopingRegistry.bumpRevision(); f.drain();
        f.expect(8, f.top - 1, 8, 15); f.expect(8, f.min, 8, 0);
        require(f.engine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(0, f.min >> 4, 0)) != null, "New-chunk initialization preserves physical nonempty sections on vanilla restoration");
        SeamFixture.data(f.level).looping = true; LoopingRegistry.bumpRevision(); f.drain(); f.expect(8, f.top - 1, 8, 0);
    }

    private static void chunkBoundaries() throws Exception {
        Fixture f = new Fixture(16, false);
        for (int z = 0; z < 16; z++) for (int x = 16; x < 32; x++) f.put(x, f.min, z, Blocks.BEDROCK.defaultBlockState());
        f.load(0, 0, false); f.drain(); f.load(1, 0, true); f.drain();
        f.expect(16, f.min + 2, 8, 14); f.expect(17, f.min + 2, 8, 13);
        f.engine.setLightEnabled(new ChunkPos(1, 0), false); f.loaded.remove(ChunkPos.pack(1, 0)); f.drain();
        f.load(1, 0, true); f.drain(); f.expect(16, f.min + 2, 8, 14);
    }

    public static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    public static final class Fixture {
        public final Level level;
        public final int min, top;
        public final LevelLightEngine engine;
        public final BlockAndLightGetter view;
        final Map<Long, Chunk> chunks = new HashMap<>();
        final Set<Long> loaded = new HashSet<>();
        final Set<SectionPos> notifications = new HashSet<>();
        public Fixture(int period, boolean client) throws Exception {
            this(-64, period, client);
        }
        public Fixture(int minimum, int period, boolean client) throws Exception {
            min = minimum; top = min + period; level = SeamFixture.create(min, period); SeamFixture.data(level).client = client;
            engine = new LevelLightEngine(new LightChunkGetter() {
                public LightChunk getChunkForLighting(int x, int z) { return chunks.get(ChunkPos.pack(x, z)); }
                public BlockAndLightGetter getLevel() { return level; }
                public void onLightUpdate(LightLayer layer, SectionPos section) { if (layer == LightLayer.SKY) notifications.add(section); }
            }, true, true);
            view = new BlockAndLightGetter() {
                public LevelLightEngine getLightEngine() { return engine; }
                public BlockEntity getBlockEntity(BlockPos pos) { return null; }
                public BlockState getBlockState(BlockPos pos) { return SeamFixture.block(level, pos); }
                public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
                public int getMinY() { return min; }
                public int getHeight() { return period; }
            };
        }
        Chunk chunk(int x, int z) { return chunks.computeIfAbsent(ChunkPos.pack(x, z), key -> new Chunk(x, z)); }
        public void put(int x, int y, int z, BlockState state) {
            BlockPos pos = new BlockPos(x, y, z);
            if (state.isAir()) SeamFixture.data(level).blocks.remove(pos); else SeamFixture.data(level).blocks.put(pos, state);
            Chunk chunk = chunk(x >> 4, z >> 4); chunk.sources.update(chunk, x & 15, y, z & 15);
            if (loaded.contains(ChunkPos.pack(x >> 4, z >> 4))) {
                engine.updateSectionStatus(SectionPos.of(pos), chunk.empty(y >> 4)); engine.checkBlock(pos);
            }
        }
        public void load(int x, int z, boolean legacy) {
            Chunk chunk = chunk(x, z); loaded.add(ChunkPos.pack(x, z));
            for (int sy = level.getMinSectionY(); sy <= level.getMaxSectionY(); sy++) {
                SectionPos section = SectionPos.of(x, sy, z); engine.updateSectionStatus(section, chunk.empty(sy));
                if (legacy) engine.queueSectionData(LightLayer.SKY, section, new DataLayer(15));
            }
            if (!legacy && !level.isClientSide()) {
                // Native initializeLight(false) precedes lightChunk(false) for new terrain.
                engine.setLightEnabled(new ChunkPos(x, z), false);
                engine.propagateLightSources(new ChunkPos(x, z));
            } else engine.setLightEnabled(new ChunkPos(x, z), true);
        }
        public void drain() {
            int batches = 0, visits = 0;
            while (engine.hasLightWork()) {
                require(++batches <= 16, "Light update queues must converge"); visits += engine.runLightUpdates();
                require(visits < 5_000_000, "Bounded periodic propagation");
            }
        }
        public int sky(int x, int y, int z) { return engine.getLayerListener(LightLayer.SKY).getLightValue(new BlockPos(x, y, z)); }
        public void expect(int x, int y, int z, int value) {
            int actual = sky(x, y, z);
            require(actual == value, "Sky at " + x + "," + y + "," + z + " (height " + (top-min) + "): expected " + value + ", got " + actual);
        }
        final class Chunk implements LightChunk {
            final int x, z;
            final ChunkSkyLightSources sources = new ChunkSkyLightSources(level);
            Chunk(int x, int z) { this.x = x; this.z = z; }
            public BlockState getBlockState(BlockPos pos) {
                if (pos.getY() < min || pos.getY() >= top) return Blocks.AIR.defaultBlockState();
                return SeamFixture.data(level).blocks.getOrDefault(new BlockPos(x * 16 + (pos.getX() & 15), pos.getY(), z * 16 + (pos.getZ() & 15)), Blocks.AIR.defaultBlockState());
            }
            public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            public int getHeight() { return top - min; }
            public int getMinY() { return min; }
            public ChunkSkyLightSources getSkyLightSources() { return sources; }
            public void findBlockLightSources(BiConsumer<BlockPos, BlockState> action) {
                SeamFixture.data(level).blocks.forEach((pos, state) -> { if ((pos.getX() >> 4) == x && (pos.getZ() >> 4) == z && state.getLightEmission() > 0) action.accept(pos, state); });
            }
            boolean empty(int sy) { return SeamFixture.data(level).blocks.keySet().stream().noneMatch(p -> (p.getX() >> 4) == x && (p.getZ() >> 4) == z && (p.getY() >> 4) == sy); }
        }
    }
}
