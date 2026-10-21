package babel.biblioteca.client;

import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Renderer-independent copy visibility and translucent ordering shared by all supported releases. */
public final class PeriodicTerrainPlan<H> implements AutoCloseable {
    @FunctionalInterface
    public interface Visibility {
        boolean test(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    }

    public record Source<H>(H handle, int x, int y, int z, int indexCount, int indexBytes, QuadCenters centers) {}
    public record Copy<H>(Source<H> source, int x, int y, int z) {}
    public record Draw<H>(Copy<H> copy, int firstIndex, int indexCount, boolean sorted) {}
    public record Result<H>(List<Draw<H>> draws, ByteBuffer indices) {}
    private record Key(int x, int y, int z) {}

    private final Map<Key, Cached> orders = new HashMap<>();
    private ByteBuffer storage;
    private long generation;

    public static <H> List<Copy<H>> copies(List<Source<H>> sources, int period, Vec3 camera,
                                          double distance, Visibility visible) {
        if (period <= 0) return List.of();
        List<Copy<H>> result = new ArrayList<>();
        for (Source<H> source : sources) {
            if (source.indexCount() <= 0) continue;
            int first = (int) Math.ceil((camera.y - distance - source.y() - 8.0) / period);
            int last = (int) Math.floor((camera.y + distance - source.y() - 8.0) / period);
            for (int layer = first; layer <= last; layer++) {
                int y = source.y() + layer * period;
                if (visible.test(source.x(), y, source.z(), source.x() + 16.0, y + 16.0, source.z() + 16.0)) {
                    result.add(new Copy<>(source, source.x(), y, source.z()));
                }
            }
        }
        return result;
    }

    /** The returned index view remains valid until the next call or close(). Upload it before then. */
    public Result<H> sort(List<Copy<H>> copies, Vec3 camera) {
        generation++;
        Comparator<Cursor<H>> comparator = Comparator.<Cursor<H>>comparingDouble(Cursor::distance).reversed()
                .thenComparingInt(cursor -> cursor.copy.x())
                .thenComparingInt(cursor -> cursor.copy.y())
                .thenComparingInt(cursor -> cursor.copy.z());
        PriorityQueue<Cursor<H>> queue = new PriorityQueue<>(comparator);
        int bytes = 0;
        for (Copy<H> copy : copies) {
            Source<H> source = copy.source();
            if (source.indexCount() <= 0) continue;
            QuadCenters centers = source.centers();
            Cached cached = null;
            if (centers != null && centers.size() * 6 == source.indexCount()) {
                Key key = new Key(copy.x(), copy.y(), copy.z());
                cached = orders.get(key);
                if (cached == null || cached.identity != centers.identity()) {
                    cached = new Cached(centers);
                    orders.put(key, cached);
                }
                cached.used = generation;
                cached.order.update(camera.x - copy.x(), camera.y - copy.y(), camera.z - copy.z());
                bytes = Math.addExact(bytes, Math.multiplyExact(source.indexCount(), source.indexBytes()));
            }
            queue.add(new Cursor<>(copy, cached, camera));
        }

        ensureCapacity(bytes);
        ByteBuffer indices = storage == null ? ByteBuffer.allocate(0)
                : storage.duplicate().order(ByteOrder.nativeOrder());
        indices.clear().limit(bytes);
        List<Draw<H>> draws = new ArrayList<>();
        while (!queue.isEmpty()) {
            Cursor<H> cursor = queue.remove();
            if (cursor.cached == null) {
                draws.add(new Draw<>(cursor.copy, 0, cursor.copy.source().indexCount(), false));
                continue;
            }
            int indexBytes = cursor.copy.source().indexBytes();
            int firstIndex = indices.position() / indexBytes;
            int count = 0;
            do {
                TranslucentQuadOrder.writeQuad(indices, indexBytes, cursor.cached.order.quad(cursor.next++));
                count += 6;
            } while (cursor.next < cursor.cached.order.size()
                    && (queue.isEmpty() || comparator.compare(cursor, queue.peek()) <= 0));
            draws.add(new Draw<>(cursor.copy, firstIndex, count, true));
            if (cursor.next < cursor.cached.order.size()) queue.add(cursor);
        }
        indices.flip();
        orders.entrySet().removeIf(entry -> entry.getValue().used != generation);
        return new Result<>(draws, indices);
    }

    private void ensureCapacity(int bytes) {
        if (bytes == 0 || storage != null && storage.capacity() >= bytes) return;
        if (storage != null) MemoryUtil.memFree(storage);
        storage = MemoryUtil.memAlloc(Math.max(65536, bytes)).order(ByteOrder.nativeOrder());
    }

    @Override
    public void close() {
        orders.clear();
        if (storage != null) {
            MemoryUtil.memFree(storage);
            storage = null;
        }
    }

    private static final class Cached {
        final Object identity;
        final TranslucentQuadOrder order;
        long used;
        Cached(QuadCenters centers) {
            identity = centers.identity();
            order = new TranslucentQuadOrder(centers);
        }
    }

    private static final class Cursor<H> {
        final Copy<H> copy;
        final Cached cached;
        final double centerDistance;
        int next;
        Cursor(Copy<H> copy, Cached cached, Vec3 camera) {
            this.copy = copy;
            this.cached = cached;
            double x = copy.x() + 8.0 - camera.x;
            double y = copy.y() + 8.0 - camera.y;
            double z = copy.z() + 8.0 - camera.z;
            centerDistance = x * x + y * y + z * z;
        }
        double distance() { return cached == null ? centerDistance : cached.order.distance(next); }
    }
}
