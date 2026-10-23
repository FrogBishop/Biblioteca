package babel.biblioteca.validation;

import babel.biblioteca.client.LoopingWeather;
import babel.biblioteca.client.PeriodicTerrainPlan;
import babel.biblioteca.client.QuadCenters;
import babel.biblioteca.client.TranslucentQuadOrder;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Independent exhaustive reference ordering and boundary coverage checks, without a graphics device. */
public final class TerrainChecks {
    private record Expected(PeriodicTerrainPlan.Copy<Integer> copy, int quad, double distance) {}

    public static void run() throws Exception {
        int quads = 0;
        Random random = new Random(71439923);
        try (PeriodicTerrainPlan<Integer> plan = new PeriodicTerrainPlan<>()) {
            for (int frame = 0; frame < 120; frame++) {
                int period = frame % 2 == 0 ? 16 : 384;
                Vec3 camera = new Vec3(random.nextDouble() * 48, -64 + random.nextDouble() * period, random.nextDouble() * 16);
                List<PeriodicTerrainPlan.Source<Integer>> sources = new ArrayList<>();
                for (int section = 0; section < 3; section++) {
                    float[] coords = new float[(25 + random.nextInt(50)) * 3];
                    for (int i = 0; i < coords.length; i++) coords[i] = random.nextFloat() * 16;
                    sources.add(new PeriodicTerrainPlan.Source<>(section, section * 16, -64, 0, coords.length * 2,
                            section % 2 == 0 ? 2 : 4, QuadCenters.of(coords)));
                }
                var copies = PeriodicTerrainPlan.copies(sources, period, camera, 512, (x, y, z, xx, yy, zz) -> y < camera.y + 100);
                int bruteCount = 0;
                for (var source : sources) {
                    for (int layer = -100; layer <= 100; layer++) {
                        int y = source.y() + layer * period;
                        if (Math.abs(y + 8 - camera.y) <= 512 && y < camera.y + 100) {
                            bruteCount++;
                            check(copies.contains(new PeriodicTerrainPlan.Copy<>(source, source.x(), y, source.z())), "copy missing");
                        }
                    }
                }
                check(copies.size() == bruteCount, "extra/duplicate periodic copy");
                quads += checkOrder(plan, copies, camera);
                quads += checkOrder(plan, copies, camera.add(0.012, -0.025, 0.001));
            }
            for (int width : new int[]{2, 4}) {
                int count = width == 2 ? 16384 : 17001;
                float[] centers = new float[count * 3];
                for (int q = 0; q < count; q++) centers[q * 3 + 1] = q * 0.125f;
                var source = new PeriodicTerrainPlan.Source<>(0, 0, 0, 0, count * 6, width, QuadCenters.of(centers));
                quads += checkOrder(plan, List.of(new PeriodicTerrainPlan.Copy<>(source, 0, 0, 0)), Vec3.ZERO);
            }
            var unknown = new PeriodicTerrainPlan.Source<>(99, 0, 0, 0, 18, 2, null);
            var fallback = plan.sort(List.of(new PeriodicTerrainPlan.Copy<>(unknown, 0, 0, 0)), Vec3.ZERO);
            check(fallback.draws().size() == 1 && !fallback.draws().get(0).sorted() && !fallback.indices().hasRemaining(), "unknown mesh must retain native indices");
            plan.close();
            check(plan.sort(List.of(), Vec3.ZERO).draws().isEmpty(), "reset/reuse");
        }
        checkCentroidAdapters();
        checkNativeDraws();
        checkWeather();
        System.out.println("DATA_OK terrain: " + quads + " quads, global order, copy visibility, both index widths, source replacement, centroid formats, weather slices");
    }

    private static int checkOrder(PeriodicTerrainPlan<Integer> plan, List<PeriodicTerrainPlan.Copy<Integer>> copies, Vec3 camera) {
        List<Expected> expected = new ArrayList<>();
        for (var copy : copies) {
            var centers = copy.source().centers();
            for (int q = 0; q < centers.size(); q++) {
                double x = copy.x() + (double) centers.x(q) - camera.x;
                double y = copy.y() + (double) centers.y(q) - camera.y;
                double z = copy.z() + (double) centers.z(q) - camera.z;
                expected.add(new Expected(copy, q, x*x + y*y + z*z));
            }
        }
        expected.sort(Comparator.comparingDouble(Expected::distance).reversed().thenComparingInt(e -> e.copy.x())
                .thenComparingInt(e -> e.copy.y()).thenComparingInt(e -> e.copy.z()).thenComparingInt(Expected::quad));
        var result = plan.sort(copies, camera);
        ByteBuffer bytes = result.indices().duplicate().order(ByteOrder.nativeOrder());
        int at = 0;
        for (var draw : result.draws()) {
            check(draw.sorted() && draw.indexCount() > 0 && draw.indexCount() % 6 == 0, "invalid draw run");
            int width = draw.copy().source().indexBytes();
            bytes.position(draw.firstIndex() * width);
            for (int n = 0; n < draw.indexCount(); n += 6) {
                var expectedQuad = expected.get(at++);
                check(draw.copy().equals(expectedQuad.copy), "global draw order/source binding");
                for (int offset : new int[]{0, 1, 2, 2, 3, 0}) {
                    int actual = width == 2 ? Short.toUnsignedInt(bytes.getShort()) : bytes.getInt();
                    check(actual == expectedQuad.quad * 4 + offset, "index address/order");
                }
            }
        }
        check(at == expected.size(), "missing/duplicate alpha faces");
        return at;
    }

    private static void checkCentroidAdapters() throws Exception {
        ByteBuffer vertexBytes = ByteBuffer.allocate(16 + 8 * 32).order(ByteOrder.nativeOrder());
        vertexBytes.position(16);
        for (int v = 0; v < 8; v++) {
            vertexBytes.putFloat(16 + v * 32, v * 2f);
            vertexBytes.putFloat(20 + v * 32, v * 3f);
            vertexBytes.putFloat(24 + v * 32, -v * 4f);
        }
        QuadCenters captured = QuadCenters.fromVertices(vertexBytes, 32, 8);
        check(captured.size() == 2 && captured.x(0) == 2 && captured.y(1) == 15 && captured.z(1) == -20,
                "classic VBO centroid extraction/byte offsets");
        check(vertexBytes.position() == 16, "centroid capture consumed source upload");
        try {
            Class<?> vector = Class.forName("org.joml.Vector3f");
            Object array = java.lang.reflect.Array.newInstance(vector, 2);
            java.lang.reflect.Array.set(array, 0, vector.getConstructor(float.class, float.class, float.class).newInstance(1f, 2f, 3f));
            java.lang.reflect.Array.set(array, 1, vector.getConstructor(float.class, float.class, float.class).newInstance(4f, 5f, 6f));
            QuadCenters vectors = (QuadCenters) QuadCenters.class.getMethod("of", array.getClass()).invoke(null, array);
            check(vectors.size() == 2 && vectors.z(1) == 6, "Vector3f centroid adapter");
        } catch (ClassNotFoundException expected) { /* Before 1.19.3, uploaded vertex bytes supply the centroids. */ }
        try {
            Class<?> type = Class.forName("com.mojang.blaze3d.vertex.CompactVectorArray");
            Object compact = type.getConstructor(int.class).newInstance(2);
            type.getMethod("set", int.class, float.class, float.class, float.class).invoke(compact, 1, 4f, 5f, 6f);
            QuadCenters adapted = (QuadCenters) QuadCenters.class.getMethod("of", type).invoke(null, compact);
            check(adapted.size() == 2 && adapted.z(1) == 6 && adapted.identity() == compact, "compact centroid adapter");
        } catch (ClassNotFoundException expected) { /* Vector3f era. */ }
        QuadCenters points = QuadCenters.of(new float[]{0, 1, 0, 0, 15, 0});
        TranslucentQuadOrder a = new TranslucentQuadOrder(points), b = new TranslucentQuadOrder(points);
        a.update(0, 0, 0);
        b.update(0, 16, 0);
        check(a.quad(0) == 1 && b.quad(0) == 0, "copies share mutable quad order");
        a.update(0, 8.01, 0);
        check(a.quad(0) == 0 && b.quad(0) == 0, "sub-block camera movement");
        a.update(0, 8, 0);
        check(a.quad(0) == 0, "deterministic ties");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void checkNativeDraws() throws Exception {
        Class<?> factory;
        try { factory = Class.forName("babel.biblioteca.client.GpuTerrainDraws"); }
        catch (ClassNotFoundException otherBackend) { return; }
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Class<?> gpuType = Class.forName("com.mojang.blaze3d.buffers.GpuBuffer");
        Class<?> concrete = Class.forName("com.mojang.blaze3d.opengl.GlBuffer");
        if (java.lang.reflect.Modifier.isAbstract(concrete.getModifiers())) {
            concrete = Class.forName("com.mojang.blaze3d.opengl.GlBuffer$Direct");
        }
        Object vertices = unsafe.allocateInstance(concrete), original = unsafe.allocateInstance(concrete), sorted = unsafe.allocateInstance(concrete);
        Class<?> handleType = Class.forName("babel.biblioteca.client.GpuTerrainBuffer");
        var constructor = handleType.getConstructors()[0];
        Object indexType = Enum.valueOf((Class) constructor.getParameterTypes()[2], "SHORT");
        Object handle = constructor.newInstance(vertices, original, indexType, 39, 8192, 1f);
        var source = new PeriodicTerrainPlan.Source<>(handle, 16, 304, 32, 12, 2, null);
        var copy = new PeriodicTerrainPlan.Copy<>(source, 16, -80, 32);
        var create = factory.getMethod("create", PeriodicTerrainPlan.Draw.class, gpuType, Vec3.class, int.class);
        for (boolean useSorted : new boolean[]{false, true}) {
            Object nativeDraw = create.invoke(null, new PeriodicTerrainPlan.Draw<>(copy, 71, 6, useSorted), sorted, new Vec3(8, -63, 4), 2);
            Class<?> type = nativeDraw.getClass();
            check(type.getMethod("vertexBuffer").invoke(nativeDraw) == vertices, "native source vertex binding");
            check(type.getMethod("indexBuffer").invoke(nativeDraw) == (useSorted ? sorted : original), "native independent index binding");
            check((int) type.getMethod("firstIndex").invoke(nativeDraw) == (useSorted ? 71 : 39)
                    && (int) type.getMethod("indexCount").invoke(nativeDraw) == 6, "native index slice addressing");
            try { check((int) type.getMethod("baseVertex").invoke(nativeDraw) == 8192, "Uber buffer base vertex lost"); }
            catch (NoSuchMethodException beforeUberBuffers) { }
            Class<?> uploader = Class.forName("com.mojang.blaze3d.systems.RenderPass$UniformUploader");
            Object[][] uploads = new Object[1][];
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(TerrainChecks.class.getClassLoader(), new Class[]{uploader},
                    (p, method, arguments) -> { uploads[0] = arguments; return null; });
            Object callback = type.getMethod("uniformUploaderConsumer").invoke(nativeDraw);
            if (callback instanceof java.util.function.Consumer consumer) {
                consumer.accept(proxy);
                check(uploads[0][0].equals("ModelOffset"), "1.21.5 model-offset uniform");
                float[] offsets = (float[]) uploads[0][1];
                check(offsets[0] == 8 && offsets[1] == -17 && offsets[2] == 28, "camera-relative periodic translation");
            } else {
                Class<?> sliceType = Class.forName("com.mojang.blaze3d.buffers.GpuBufferSlice");
                Object slices = java.lang.reflect.Array.newInstance(sliceType, 3);
                for (int i = 0; i < 3; i++) java.lang.reflect.Array.set(slices, i, gpuType.getMethod("slice").invoke(vertices));
                ((java.util.function.BiConsumer) callback).accept(slices, proxy);
                check(uploads[0][1] == java.lang.reflect.Array.get(slices, 2), "wrong per-copy uniform slice");
                check(uploads[0][0].equals("DynamicTransforms") || uploads[0][0].equals("ChunkSection"), "native uniform name");
            }
        }
        System.out.println("DATA_OK native GPU draw records: source buffers, sorted slices, base vertex, per-copy uniforms");
    }

    private static void checkWeather() {
        for (int period : new int[]{16, 128, 384}) {
            for (int camera = -85; camera < period - 40; camera++) {
                for (int radius : new int[]{1, 5, 10, 32}) {
                    var slices = LoopingWeather.visibleSlices(camera, radius, -64, period);
                    int covered = camera - radius;
                    for (var slice : slices) {
                        check(slice.minY() >= -64 && slice.maxY() <= -64 + period && slice.minY() < slice.maxY(), "weather lookup out of source world");
                        check(slice.cameraY() >= -64 && slice.cameraY() < -64 + period, "biome source row");
                        check(slice.minY() + slice.offsetY() == covered, "weather gap/overlap");
                        covered = slice.topY();
                        for (int surface : new int[]{-64, -60, -64 + period - 1, -64 + period}) {
                            check(slice.bottomY(surface) >= surface + slice.offsetY(), "precipitation below copied terrain");
                        }
                    }
                    check(covered == camera + radius, "weather range incomplete");
                }
            }
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
