package babel.biblioteca.validation;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SkyLightMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Same light scenarios against the native pre-1.20 graph and the modern queued light engine. */
public final class SkyChecks {
    private static final AtomicReference<SkyLightMode> MODE = new AtomicReference<>(SkyLightMode.DEFAULT);
    public static void run() throws Exception {
        Field server = LoopingRegistry.class.getDeclaredField("serverSkyLightMode"), client = LoopingRegistry.class.getDeclaredField("clientSkyLightMode");
        server.setAccessible(true); client.setAccessible(true);
        Object oldServer = server.get(null), oldClient = client.get(null);
        LoopingRegistry.setServerSkyLightModeGetter(dimension -> MODE.get());
        LoopingRegistry.setClientSkyLightModeGetter(dimension -> MODE.get());
        try {
            int min = SeamFixture.hasSections() ? -64 : 0;
            for (int height : new int[]{16, SeamFixture.hasSections() ? 384 : 256}) {
                closedColumns(min, height);
                tunnel(min, height, SkyLightMode.LOOPING);
                tunnel(min, height, SkyLightMode.HYBRID);
            }
            neighbors(min);
        } finally { server.set(null, oldServer); client.set(null, oldClient); LoopingRegistry.bumpRevision(); }
        System.out.println("PASS: native sky engines: 3 modes, old saved light, roofs/glass/water, both seam directions, retraction, packet layers and chunk borders");
    }
    private static void mode(SkyLightMode mode) { MODE.set(mode); LoopingRegistry.bumpRevision(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void closedColumns(int min, int height) throws Exception {
        mode(SkyLightMode.LOOPING); Fixture f = new Fixture(min, height, false);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) f.put(x, min, z, Blocks.BEDROCK.defaultBlockState());
        f.load(0, 0, true); f.drain(); f.expect(8, f.top - 1, 8, 0); f.expect(8, f.top, 8, 0);
        mode(SkyLightMode.HYBRID); check(f.engine.hasLightWork(), "mode change schedules native lighting"); f.drain();
        f.expect(8, min + 1, 8, 15); f.expect(8, f.top - 1, 8, 15); f.expect(8, f.top, 8, 0);
        f.put(8, min + height / 2, 8, Blocks.STONE.defaultBlockState()); f.drain();
        f.expect(8, min + height / 2 - 1, 8, 14); f.expect(8, min + height / 2 + 1, 8, 15);
        mode(SkyLightMode.DISABLED); f.drain(); f.expect(8, f.top - 1, 8, 0);
        mode(SkyLightMode.LOOPING); f.drain(); f.expect(8, f.top - 1, 8, 0);
        f.put(8, min + height / 2, 8, Blocks.AIR.defaultBlockState());
        f.put(8, min, 8, Blocks.AIR.defaultBlockState()); f.drain();
        f.expect(8, min, 8, 15); f.expect(8, f.top - 1, 8, 15); f.expect(9, min + 1, 8, 14);
        f.put(8, min, 8, Blocks.WATER.defaultBlockState()); f.drain(); f.expect(8, f.top - 1, 8, 0);
        f.put(8, min, 8, Blocks.GLASS.defaultBlockState()); f.drain(); f.expect(8, f.top - 1, 8, 15);
        f.put(8, min, 8, Blocks.STONE.defaultBlockState()); f.drain(); f.expect(8, f.top - 1, 8, 0);
        f.level.getClass().getField("looping").setBoolean(f.level, false); LoopingRegistry.bumpRevision(); f.drain();
        f.expect(8, f.top - 1, 8, 15); f.expect(8, min, 8, 0);
        f.level.getClass().getField("looping").setBoolean(f.level, true); LoopingRegistry.bumpRevision(); f.drain(); f.expect(8, f.top - 1, 8, 0);

        Fixture client = new Fixture(min, height, true); client.load(0, 0, false);
        for (int sy = min >> 4; sy < f.top >> 4; sy++) {
            SectionPos section = SectionPos.of(0, sy, 0);
            DataLayer data = f.engine.getLayerListener(LightLayer.SKY).getDataLayerData(section);
            check(data != null, "empty periodic sections have explicit server sky data"); client.queue(section, data.copy());
        }
        client.drain(); client.expect(8, f.top - 1, 8, 0); client.expect(8, f.top, 8, 0);
    }

    private static void tunnel(int min, int height, SkyLightMode mode) throws Exception {
        mode(mode); Fixture f = new Fixture(min, height, false);
        for (int y = min; y < f.top; y++) {
            f.put(5, y, 8, Blocks.STONE.defaultBlockState()); f.put(7, y, 8, Blocks.STONE.defaultBlockState());
            f.put(6, y, 7, Blocks.STONE.defaultBlockState()); f.put(6, y, 9, Blocks.STONE.defaultBlockState());
        }
        f.put(6, min + height / 2, 8, Blocks.STONE.defaultBlockState());
        if (mode == SkyLightMode.HYBRID) f.put(6, f.top - 1, 8, Blocks.WATER.defaultBlockState());
        f.put(5, f.top - 2, 8, Blocks.AIR.defaultBlockState()); f.load(0, 0, false); f.drain();
        f.expect(6, f.top - 2, 8, 13); f.expect(6, f.top - 1, 8, 12); f.expect(6, min, 8, 11); f.expect(6, min + 1, 8, 10);
        f.put(5, f.top - 2, 8, Blocks.STONE.defaultBlockState()); f.put(5, min + 1, 8, Blocks.AIR.defaultBlockState()); f.drain();
        f.expect(6, min + 1, 8, 13); f.expect(6, min, 8, 12); f.expect(6, f.top - 1, 8, 11); f.expect(6, f.top - 2, 8, 10);
        f.put(5, min + 1, 8, Blocks.STONE.defaultBlockState()); f.drain();
        f.expect(6, min, 8, 0); f.expect(6, f.top - 1, 8, 0);
    }

    private static void neighbors(int min) throws Exception {
        mode(SkyLightMode.LOOPING); Fixture f = new Fixture(min, 16, false);
        for (int x = 16; x < 32; x++) for (int z = 0; z < 16; z++) f.put(x, min, z, Blocks.BEDROCK.defaultBlockState());
        f.load(0, 0, false); f.drain(); f.load(1, 0, true); f.drain(); f.expect(16, min + 2, 8, 14); f.expect(17, min + 2, 8, 13);
        f.enabled.invoke(f.engine, new ChunkPos(1, 0), false); f.loaded.remove(Compat.chunkKey(1, 0)); f.drain();
        f.load(1, 0, true); f.drain(); f.expect(16, min + 2, 8, 14);
    }

    private static final class Fixture {
        final Level level;
        final int min, top;
        final LevelLightEngine engine;
        final boolean modern;
        final Method run, enabled, propagate, queue;
        final Map<Long, Chunk> chunks = new HashMap<>();
        final Set<Long> loaded = new HashSet<>();
        Fixture(int min, int height, boolean client) throws Exception {
            this.min = min; top = min + height; level = SeamFixture.create(min, height);
            level.getClass().getField("client").setBoolean(level, client);
            try { Field side = Level.class.getDeclaredField("isClientSide"); side.setAccessible(true); side.setBoolean(level, client); }
            catch (NoSuchFieldException ignored) {}
            Method current;
            try { current = LevelLightEngine.class.getMethod("runLightUpdates"); }
            catch (NoSuchMethodException ignored) { current = LevelLightEngine.class.getMethod("runUpdates", int.class, boolean.class, boolean.class); }
            run = current; modern = run.getParameterCount() == 0;
            enabled = LevelLightEngine.class.getMethod(modern ? "setLightEnabled" : "enableLightSources", ChunkPos.class, boolean.class);
            propagate = modern ? LevelLightEngine.class.getMethod("propagateLightSources", ChunkPos.class) : null;
            queue = Arrays.stream(LevelLightEngine.class.getMethods()).filter(m -> m.getName().equals("queueSectionData")).findFirst().orElseThrow();
            LightChunkGetter source = (LightChunkGetter)Proxy.newProxyInstance(LightChunkGetter.class.getClassLoader(), new Class<?>[]{LightChunkGetter.class}, (proxy, method, args) -> {
                if (method.getName().equals("getLevel")) return level;
                if (method.getName().equals("getChunkForLighting")) { Chunk chunk = chunks.get(Compat.chunkKey((int)args[0], (int)args[1])); return chunk == null ? null : chunk.proxy; }
                if (method.getName().equals("onLightUpdate")) return null;
                if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                throw new AssertionError("Unexpected light source method: " + method);
            });
            engine = new LevelLightEngine(source, false, true);
        }
        Chunk chunk(int x, int z) throws Exception {
            long key = Compat.chunkKey(x, z); Chunk chunk = chunks.get(key);
            if (chunk == null) { chunk = new Chunk(x, z); chunks.put(key, chunk); }
            return chunk;
        }
        void put(int x, int y, int z, BlockState state) throws Exception {
            BlockPos pos = new BlockPos(x, y, z);
            if (state.isAir()) SeamFixture.data(level).blocks.remove(pos); else SeamFixture.data(level).blocks.put(pos, state);
            Chunk chunk = chunk(x >> 4, z >> 4);
            if (modern) chunk.sources.getClass().getMethod("update", BlockGetter.class, int.class, int.class, int.class).invoke(chunk.sources, chunk.proxy, x & 15, y, z & 15);
            if (loaded.contains(Compat.chunkKey(x >> 4, z >> 4))) {
                engine.updateSectionStatus(SectionPos.of(pos), chunk.empty(y >> 4)); engine.checkBlock(pos);
            }
        }
        void queue(SectionPos section, DataLayer layer) throws Exception {
            if (queue.getParameterCount() == 3) queue.invoke(engine, LightLayer.SKY, section, layer);
            else queue.invoke(engine, LightLayer.SKY, section, layer, true);
        }
        void load(int x, int z, boolean saved) throws Exception {
            Chunk chunk = chunk(x, z); loaded.add(Compat.chunkKey(x, z));
            for (int sy = min >> 4; sy < top >> 4; sy++) {
                SectionPos section = SectionPos.of(x, sy, z); engine.updateSectionStatus(section, chunk.empty(sy));
                if (saved) { DataLayer data = new DataLayer(); Arrays.fill(data.getData(), (byte)0xff); queue(section, data); }
            }
            enabled.invoke(engine, new ChunkPos(x, z), true);
            if (modern && !saved && !level.isClientSide()) propagate.invoke(engine, new ChunkPos(x, z));
        }
        void drain() throws Exception {
            int passes = 0;
            while (engine.hasLightWork()) {
                check(++passes <= 40, "native sky queues must converge: " + MODE.get());
                if (modern) run.invoke(engine); else run.invoke(engine, 200000, true, true);
            }
        }
        void expect(int x, int y, int z, int expected) {
            int actual = engine.getLayerListener(LightLayer.SKY).getLightValue(new BlockPos(x, y, z));
            check(actual == expected, "Sky " + MODE.get() + " at " + x + "," + y + "," + z + " in " + (top-min) + " rows: " + actual + " != " + expected);
        }
        final class Chunk {
            final int x, z; final Object proxy, sources;
            Chunk(int x, int z) throws Exception {
                this.x = x; this.z = z;
                Class<?> api = modern ? Class.forName("net.minecraft.world.level.chunk.LightChunk") : BlockGetter.class;
                sources = modern ? Class.forName("net.minecraft.world.level.lighting.ChunkSkyLightSources").getConstructors()[0].newInstance(level) : null;
                proxy = Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (object, method, args) -> {
                    switch (method.getName()) {
                        case "getBlockState": return state((BlockPos)args[0]);
                        case "getFluidState": return state((BlockPos)args[0]).getFluidState();
                        case "getBlockEntity": return null;
                        case "getHeight": return top - min;
                        case "getMinY": case "getMinBuildHeight": return min;
                        case "getMaxY": return top - 1;
                        case "getMaxBuildHeight": return top;
                        case "getSkyLightSources": return sources;
                        case "findBlockLightSources": return null;
                        case "toString": return "Light test chunk " + x + "," + z;
                        case "hashCode": return System.identityHashCode(object);
                        case "equals": return object == args[0];
                        default: if (method.isDefault()) return InvocationHandler.invokeDefault(object, method, args);
                    }
                    throw new AssertionError("Unexpected chunk method: " + method);
                });
            }
            BlockState state(BlockPos pos) {
                if (pos.getY() < min || pos.getY() >= top) return Blocks.AIR.defaultBlockState();
                return SeamFixture.data(level).blocks.getOrDefault(new BlockPos(x * 16 + (pos.getX() & 15), pos.getY(), z * 16 + (pos.getZ() & 15)), Blocks.AIR.defaultBlockState());
            }
            boolean empty(int sy) { return SeamFixture.data(level).blocks.keySet().stream().noneMatch(pos -> pos.getX() >> 4 == x && pos.getZ() >> 4 == z && pos.getY() >> 4 == sy); }
        }
    }
}
