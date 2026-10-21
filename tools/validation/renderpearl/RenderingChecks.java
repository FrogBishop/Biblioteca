package babel.biblioteca.validation;

import babel.biblioteca.client.LoopingTranslucency;
import babel.biblioteca.client.TranslucentQuadOrder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.CompactVectorArray;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.DynamicGpuData.ChunkSectionInfo;
import net.minecraft.client.renderer.DynamicGpuData.IndexedDraw;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelRenderer.ChunkDrawGroup;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public final class RenderingChecks {
    private static final List<Buffer> allocated = new ArrayList<>();
    private static int fences;
    private static int waits;
    private static int frames;
    private static int checkedQuads;

    public static void run() throws Exception {
        for (String hook : List.of("prepareTranslucency", "captureTranslucentSource", "finishTranslucencyFrame", "releaseTranslucency")) {
            require(Arrays.stream(LevelRenderer.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains(hook)), "Missing mixin: " + hook);
        }
        require(Arrays.stream(GameRenderer.class.getDeclaredMethods()).noneMatch(m -> m.getName().contains("useOitInLoopingDimensions")), "OIT is still forced");
        verifyOption();
        verifyIndependentOrders();
        installDevice();

        try (LoopingTranslucency renderer = new LoopingTranslucency()) {
            Long2ObjectMap<MeshData.SortState> states = new Long2ObjectOpenHashMap<>();
            states.put(key(0, 304, 0), state(IndexType.SHORT, 1.0F, 15.0F));
            states.put(key(0, -64, 0), state(IndexType.INT, 0.5F, 15.5F));
            List<ChunkSectionInfo> infos = List.of(info(0, 304, 0), info(0, -80, 0), info(0, -64, 0));
            checkFrame(renderer, infos, states, -64, 384, new Vec3(0, -63, 0), i -> true);
            checkFrame(renderer, infos, states, -64, 384, new Vec3(0, -63.125, 0), i -> i.y() < 0);
            states.put(key(0, 304, 0), state(IndexType.SHORT, 15.0F, 1.0F));
            checkFrame(renderer, infos, states, -64, 384, new Vec3(0, -63.125, 0), i -> true);
            checkFrame(renderer, infos, states, -64, 384, new Vec3(0, 320.875, 0), i -> true);

            Random random = new Random(782341L);
            for (int test = 0; test < 160; test++) {
                int period = test % 2 == 0 ? 16 : 384;
                states.clear();
                List<ChunkSectionInfo> copies = new ArrayList<>();
                for (int section = 0; section < 3; section++) {
                    CompactVectorArray points = new CompactVectorArray(25 + random.nextInt(60));
                    for (int q = 0; q < points.size(); q++) {
                        points.set(q, random.nextFloat() * 16, random.nextFloat() * 16, random.nextFloat() * 16);
                    }
                    states.put(key(section * 16, -64, 0), new MeshData.SortState(points, section % 2 == 0 ? IndexType.SHORT : IndexType.INT));
                    for (int layer = -2; layer <= 2; layer++) copies.add(info(section * 16, -64 + layer * period, 0));
                }
                Vec3 camera = new Vec3(random.nextDouble() * 48, -64 + random.nextDouble() * period, random.nextDouble() * 20);
                checkFrame(renderer, copies, states, -64, period, camera, i -> true);
                checkFrame(renderer, copies, states, -64, period, camera.add(0.01, -0.03, 0.02), i -> i.x() != 16);
            }

            for (IndexType type : IndexType.values()) {
                int count = type == IndexType.SHORT ? 16384 : 17001;
                CompactVectorArray points = new CompactVectorArray(count);
                for (int q = 0; q < count; q++) points.set(q, 0, (q % 16) + 0.125F, 0);
                states.clear();
                states.put(key(0, 0, 0), new MeshData.SortState(points, type));
                checkFrame(renderer, List.of(info(0, 0, 0)), states, 0, 16, Vec3.ZERO, i -> true);
            }

            verifyUnknownMesh(renderer);
            int buffers = allocated.size();
            require(fences > 3 && waits > 0, "ring buffer fence lifecycle was not used");
            renderer.close();
            require(allocated.stream().allMatch(buffer -> buffer.closed), "buffers leaked on reset/OIT switch");
            states.clear();
            states.put(key(0, 0, 0), state(IndexType.SHORT, 0.5F, 1.5F));
            checkFrame(renderer, List.of(info(0, 0, 0)), states, 0, 16, Vec3.ZERO, i -> true);
            require(allocated.size() == buffers + 3, "fallback did not recreate its buffer ring after a reset");
        }
        require(allocated.stream().allMatch(buffer -> buffer.closed), "buffers leaked at close");
        System.out.println("PASS: " + frames + " ordered frames / " + checkedQuads + " quad draws, including global interleaving, both index widths, culling, mesh replacement and resource lifecycle");
    }

    private static void verifyOption() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        GameRenderer renderer = (GameRenderer) unsafe.allocateInstance(GameRenderer.class);
        GameRenderState state = (GameRenderState) unsafe.allocateInstance(GameRenderState.class);
        OptionsRenderState options = (OptionsRenderState) unsafe.allocateInstance(OptionsRenderState.class);
        LevelRenderState level = (LevelRenderState) unsafe.allocateInstance(LevelRenderState.class);
        set(renderer, "gameRenderState", state);
        set(state, "optionsRenderState", options);
        set(state, "levelRenderState", level);
        options.improvedTransparency = false;
        require(!renderer.useImprovedTransparency(), "option OFF did not select the fallback");
        options.improvedTransparency = true;
        require(renderer.useImprovedTransparency(), "option ON did not select OIT");
        level.renderWireframeTerrain = true;
        require(!renderer.useImprovedTransparency(), "wireframe no longer follows vanilla");
        System.out.println("PASS: vanilla Improved Transparency OFF/ON and wireframe selection");
    }

    private static void verifyIndependentOrders() {
        CompactVectorArray centers = new CompactVectorArray(2);
        centers.set(0, 0, 1, 0);
        centers.set(1, 0, 15, 0);
        TranslucentQuadOrder original = new TranslucentQuadOrder(babel.biblioteca.client.QuadCenters.of(centers));
        TranslucentQuadOrder lowerCopy = new TranslucentQuadOrder(babel.biblioteca.client.QuadCenters.of(centers));
        original.update(0, 0, 0);
        lowerCopy.update(0, 16, 0);
        require(original.quad(0) == 1 && lowerCopy.quad(0) == 0, "periodic copies shared a quad order");
        original.update(0, 7.99, 0);
        require(original.quad(0) == 1, "near-side order");
        original.update(0, 8.01, 0);
        require(original.quad(0) == 0, "sub-block camera motion did not update the order");
        original.update(0, 8.0, 0);
        require(original.quad(0) == 0, "ties are not deterministic");
        require(lowerCopy.quad(0) == 0, "sorting one copy mutated another");
    }

    private static void checkFrame(LoopingTranslucency renderer, List<ChunkSectionInfo> infos,
                                   Long2ObjectMap<MeshData.SortState> states, int minY, int period,
                                   Vec3 camera, Predicate<ChunkSectionInfo> visible) {
        Buffer verticesA = new Buffer(16 * 1024 * 1024, GpuBuffer.USAGE_VERTEX, false);
        Buffer verticesB = new Buffer(16 * 1024 * 1024, GpuBuffer.USAGE_VERTEX, false);
        List<ChunkDrawGroup> groups = new ArrayList<>();
        List<Expected> expected = new ArrayList<>();
        for (int i = 0; i < infos.size(); i++) {
            ChunkSectionInfo info = infos.get(i);
            MeshData.SortState state = states.get(key(info.x(), Math.floorMod(info.y() - minY, period) + minY, info.z()));
            Buffer vertices = i % 2 == 0 ? verticesA : verticesB;
            IndexedDraw draw = new IndexedDraw(state.centroids().size() * 6, 1, 42, 1000 + i * 100, i);
            groups.add(new ChunkDrawGroup(vertices.slice(), null, state.indexType(), new ArrayList<>(List.of(draw))));
            if (visible.test(info)) {
                for (int q = 0; q < state.centroids().size(); q++) {
                    double x = (double) info.x() + state.centroids().getX(q) - camera.x;
                    double y = (double) info.y() + state.centroids().getY(q) - camera.y;
                    double z = (double) info.z() + state.centroids().getZ(q) - camera.z;
                    expected.add(new Expected(i, q, x * x + y * y + z * z, vertices, draw.baseVertex(), info));
                }
            }
        }
        expected.sort(Comparator.comparingDouble(Expected::distance).reversed()
                .thenComparingInt(e -> e.info.x()).thenComparingInt(e -> e.info.y())
                .thenComparingInt(e -> e.info.z()).thenComparingInt(Expected::quad));
        renderer.rebuild(infos, groups, states, minY, period, camera, visible);
        int index = 0;
        for (ChunkDrawGroup group : groups.reversed()) {
            require(group.indexBuffer() != null, "sorted draw has no index buffer");
            for (IndexedDraw draw : group.draws().reversed()) {
                ByteBuffer command = ByteBuffer.allocate(20).order(ByteOrder.nativeOrder());
                draw.write(command);
                command.flip();
                require(command.getInt() == draw.indexCount() && command.getInt() == 1
                                && command.getInt() == draw.firstIndex() && command.getInt() == draw.baseVertex()
                                && command.getInt() == draw.baseInstance(), "indirect command disagrees with direct draw");
                try (GpuBufferSlice.MappedView mapped = group.indexBuffer().buffer().map(false, false)) {
                    ByteBuffer bytes = mapped.data().order(ByteOrder.nativeOrder());
                    bytes.position(draw.firstIndex() * group.indexType().bytes);
                    for (int n = 0; n < draw.indexCount(); n += 6) {
                        Expected e = expected.get(index++);
                        require(draw.baseInstance() == e.instance && draw.baseVertex() == e.baseVertex
                                && group.vertexBuffer().buffer() == e.vertices, "section/vertex binding changed at frame " + frames
                                + ", quad " + (index - 1) + ": expected section " + e.instance + " got " + draw.baseInstance());
                        int base = e.quad * 4;
                        for (int offset : new int[] {0, 1, 2, 2, 3, 0}) {
                            int value = group.indexType() == IndexType.SHORT ? Short.toUnsignedInt(bytes.getShort()) : bytes.getInt();
                            require(value == base + offset, "wrong quad order or malformed index: expected " + (base + offset) + " got " + value);
                        }
                    }
                }
            }
        }
        require(index == expected.size(), "missing or duplicate translucent geometry");
        renderer.endFrame();
        frames++;
        checkedQuads += index;
    }

    private static void verifyUnknownMesh(LoopingTranslucency renderer) {
        Buffer vertex = new Buffer(128, GpuBuffer.USAGE_VERTEX, false);
        Buffer indices = new Buffer(128, GpuBuffer.USAGE_INDEX, false);
        IndexedDraw original = new IndexedDraw(6, 1, 4, 20, 0);
        List<ChunkDrawGroup> groups = new ArrayList<>(List.of(new ChunkDrawGroup(
                vertex.slice(), indices.slice(), IndexType.INT, new ArrayList<>(List.of(original)))));
        renderer.rebuild(List.of(info(0, 0, 0)), groups, new Long2ObjectOpenHashMap<>(), 0, 16, Vec3.ZERO, i -> true);
        require(groups.size() == 1 && groups.getFirst().draws().getFirst() == original
                && groups.getFirst().indexBuffer().buffer() == indices, "unknown custom mesh was discarded");
        renderer.endFrame();
    }

    private static void installDevice() throws Exception {
        CommandEncoder encoder = (CommandEncoder) Proxy.newProxyInstance(RenderingChecks.class.getClassLoader(), new Class<?>[] {CommandEncoder.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("createFence")) throw new AssertionError(method);
                    fences++;
                    return Proxy.newProxyInstance(RenderingChecks.class.getClassLoader(), new Class<?>[] {GpuFence.class}, (p, m, a) -> {
                        if (m.getName().equals("awaitCompletion")) { waits++; return true; }
                        if (m.getName().equals("close")) return null;
                        throw new AssertionError(m);
                    });
                });
        GpuDevice device = (GpuDevice) Proxy.newProxyInstance(RenderingChecks.class.getClassLoader(), new Class<?>[] {GpuDevice.class}, (proxy, method, args) -> {
            if (method.getName().equals("createCommandEncoder")) return encoder;
            if (method.getName().equals("createBuffer") && args[2] instanceof Long size) {
                Buffer buffer = new Buffer(Math.toIntExact(size), (int) args[1], true);
                allocated.add(buffer);
                return buffer;
            }
            throw new AssertionError(method);
        });
        Field field = RenderSystem.class.getDeclaredField("DEVICE");
        field.setAccessible(true);
        require(field.get(null) == null, "validation must not replace a live GPU device");
        field.set(null, device);
    }

    private static MeshData.SortState state(IndexType type, float... heights) {
        CompactVectorArray points = new CompactVectorArray(heights.length);
        for (int i = 0; i < heights.length; i++) points.set(i, 0, heights[i], 0);
        return new MeshData.SortState(points, type);
    }

    private static long key(int x, int y, int z) { return SectionPos.asLong(x >> 4, y >> 4, z >> 4); }
    private static ChunkSectionInfo info(int x, int y, int z) { return new ChunkSectionInfo(x, y, z, 1.0F); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private record Expected(int instance, int quad, double distance, GpuBuffer vertices, int baseVertex, ChunkSectionInfo info) {}

    private static final class Buffer implements GpuBuffer {
        final int size;
        final int usage;
        final ByteBuffer storage;
        boolean closed;
        Buffer(int size, int usage, boolean mapped) {
            this.size = size;
            this.usage = usage;
            this.storage = mapped ? ByteBuffer.allocate(size).order(ByteOrder.nativeOrder()) : null;
        }
        public long size() { return this.size; }
        public int usage() { return this.usage; }
        public boolean isClosed() { return this.closed; }
        public void close() { this.closed = true; }
        public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
            require(!this.closed && this.storage != null, "invalid buffer mapping");
            ByteBuffer view = this.storage.duplicate().order(ByteOrder.nativeOrder());
            view.position((int) offset).limit((int) (offset + length));
            return new GpuBufferSlice.MappedView(slice(offset, length), view.slice().order(ByteOrder.nativeOrder()), () -> {});
        }
    }
}
