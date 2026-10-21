package babel.biblioteca.client;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/** Conventional alpha blending for periodic terrain, with independent and globally merged quad orders. */
public final class LoopingTranslucency implements AutoCloseable {

    private static final Comparator<Cursor> FAR_FIRST = Comparator
            .comparingDouble(Cursor::distance).reversed()
            .thenComparingInt(cursor -> cursor.info.x())
            .thenComparingInt(cursor -> cursor.info.y())
            .thenComparingInt(cursor -> cursor.info.z());

    private final Long2ObjectMap<CachedOrder> orders = new Long2ObjectOpenHashMap<>();
    private MappableRingBuffer indexBuffers;
    private boolean bufferUsed;
    private long generation;

    /**
     * Replaces just the translucent draw groups. The original section and all its visible copies
     * get their own order, then a merge interleaves quads from different sections and periods.
     * Consecutive quads using the same section become one draw; consecutive draws sharing buffer
     * bindings become one group, for both vanilla's direct and indirect submission paths.
     */
    public void rebuild(
            List<DynamicGpuData.ChunkSectionInfo> infos,
            List<LevelRenderer.ChunkDrawGroup> groups,
            Long2ObjectMap<MeshData.SortState> sources,
            int minY, int period, Vec3 camera,
            Predicate<DynamicGpuData.ChunkSectionInfo> visible) {
        this.generation++;
        PriorityQueue<Cursor> queue = new PriorityQueue<>(FAR_FIRST);
        int requiredBytes = 0;

        for (LevelRenderer.ChunkDrawGroup group : groups) {
            for (DynamicGpuData.IndexedDraw draw : group.draws()) {
                DynamicGpuData.ChunkSectionInfo info = infos.get(draw.baseInstance());
                if (draw.indexCount() == 0 || !visible.test(info)) continue;
                int sourceY = Math.floorMod(info.y() - minY, period) + minY;
                MeshData.SortState state = sources.get(SectionPos.asLong(info.x() >> 4, sourceY >> 4, info.z() >> 4));
                CachedOrder order = null;
                if (state != null && state.centroids().size() * 6 == draw.indexCount() && draw.instanceCount() == 1) {
                    long copyKey = SectionPos.asLong(info.x() >> 4, info.y() >> 4, info.z() >> 4);
                    order = this.orders.get(copyKey);
                    if (order == null || order.state != state) {
                        order = new CachedOrder(state);
                        this.orders.put(copyKey, order);
                    }
                    order.lastUsed = this.generation;
                    order.quads.update(camera.x - info.x(), camera.y - info.y(), camera.z - info.z());
                    requiredBytes = Math.addExact(requiredBytes, Math.multiplyExact(draw.indexCount(), state.indexType().bytes));
                }
                // A custom SectionMesh without quad metadata keeps its existing indices. Vanilla
                // meshes always provide a SortState; unknown meshes still participate in ordering.
                queue.add(new Cursor(group, draw, info, order, camera));
            }
        }

        List<LevelRenderer.ChunkDrawGroup> sorted = new ArrayList<>();
        if (requiredBytes > 0) {
            ensureCapacity(requiredBytes);
            GpuBufferSlice indices = this.indexBuffers.currentBuffer().slice(0, requiredBytes);
            try (GpuBufferSlice.MappedView mapped = indices.map(false, true)) {
                merge(queue, sorted, indices, mapped.data().order(ByteOrder.nativeOrder()));
            }
            this.bufferUsed = true;
        } else {
            merge(queue, sorted, null, null);
        }

        // Both vanilla prepare paths reverse translucent groups AND their draws. Supply their
        // expected near-first containers while each draw's index range remains far-first.
        Collections.reverse(sorted);
        for (LevelRenderer.ChunkDrawGroup group : sorted) Collections.reverse(group.draws());
        groups.clear();
        groups.addAll(sorted);
        this.orders.long2ObjectEntrySet().removeIf(entry -> entry.getValue().lastUsed != this.generation);
    }

    private static void merge(
            PriorityQueue<Cursor> queue, List<LevelRenderer.ChunkDrawGroup> output,
            GpuBufferSlice indices, ByteBuffer data) {
        while (!queue.isEmpty()) {
            Cursor cursor = queue.remove();
            if (cursor.order == null) {
                append(output, cursor.group.vertexBuffer(), cursor.group.indexBuffer(), cursor.group.indexType(), cursor.draw);
                continue;
            }

            IndexType type = cursor.order.state.indexType();
            int firstIndex = data.position() / type.bytes;
            int count = 0;
            // Emit a whole run until another section/copy has the next farthest quad.
            do {
                TranslucentQuadOrder.writeQuad(data, type, cursor.order.quads.quad(cursor.next));
                cursor.next++;
                count += 6;
            } while (cursor.next < cursor.order.quads.size()
                    && (queue.isEmpty() || FAR_FIRST.compare(cursor, queue.peek()) <= 0));

            append(output, cursor.group.vertexBuffer(), indices, type, new DynamicGpuData.IndexedDraw(
                    count, 1, firstIndex, cursor.draw.baseVertex(), cursor.draw.baseInstance()));
            if (cursor.next < cursor.order.quads.size()) queue.add(cursor);
        }
    }

    private static void append(
            List<LevelRenderer.ChunkDrawGroup> groups, GpuBufferSlice vertices, GpuBufferSlice indices,
            IndexType type, DynamicGpuData.IndexedDraw draw) {
        LevelRenderer.ChunkDrawGroup group = groups.isEmpty() ? null : groups.getLast();
        if (group == null || !group.vertexBuffer().equals(vertices)
                || !Objects.equals(group.indexBuffer(), indices) || group.indexType() != type) {
            group = new LevelRenderer.ChunkDrawGroup(vertices, indices, type, new ArrayList<>());
            groups.add(group);
        }
        group.draws().add(draw);
    }

    private void ensureCapacity(int bytes) {
        if (this.indexBuffers != null && this.indexBuffers.size() >= bytes) return;
        if (this.indexBuffers != null) this.indexBuffers.close();
        this.indexBuffers = new MappableRingBuffer(() -> "Biblioteca sorted translucent indices",
                GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_MAP_WRITE, Math.max(bytes, 65536));
    }

    /** Rotate only after the frame has submitted its draws, so in-flight indices are not overwritten. */
    public void endFrame() {
        if (this.bufferUsed && this.indexBuffers != null) this.indexBuffers.rotate();
        this.bufferUsed = false;
    }

    @Override
    public void close() {
        if (this.indexBuffers != null) {
            this.indexBuffers.close();
            this.indexBuffers = null;
        }
        this.orders.clear();
        this.bufferUsed = false;
    }

    private static final class CachedOrder {
        final MeshData.SortState state;
        final TranslucentQuadOrder quads;
        long lastUsed;

        CachedOrder(MeshData.SortState state) {
            this.state = state;
            this.quads = new TranslucentQuadOrder(state.centroids());
        }
    }

    private static final class Cursor {
        final LevelRenderer.ChunkDrawGroup group;
        final DynamicGpuData.IndexedDraw draw;
        final DynamicGpuData.ChunkSectionInfo info;
        final CachedOrder order;
        final double sectionDistance;
        int next;

        Cursor(LevelRenderer.ChunkDrawGroup group, DynamicGpuData.IndexedDraw draw,
               DynamicGpuData.ChunkSectionInfo info, CachedOrder order, Vec3 camera) {
            this.group = group;
            this.draw = draw;
            this.info = info;
            this.order = order;
            double x = info.x() + 8.0 - camera.x;
            double y = info.y() + 8.0 - camera.y;
            double z = info.z() + 8.0 - camera.z;
            this.sectionDistance = x * x + y * y + z * z;
        }

        double distance() {
            return this.order == null ? this.sectionDistance : this.order.quads.distance(this.next);
        }
    }
}
