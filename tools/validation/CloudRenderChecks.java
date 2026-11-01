package babel.biblioteca.validation;

import babel.biblioteca.client.CloudLayers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/** Native packed cloud geometry at its original allocation limit, plus bounded layer planning. */
public final class CloudRenderChecks {
    public static void run() throws Exception {
        CloudLayers layers = new CloudLayers();
        for (int period : new int[]{16, 384}) for (double camera : new double[]{-64.5, -63.5, 319.5, 320.5, 9000}) {
            float nearest = layers.prepare(192, camera, 32, period, true);
            check(Math.abs(nearest - camera) <= period / 2.0, "cloud nearest image does not follow camera");
            check(layers.count() <= 65 && layers.count() >= 2, "bounded cloud count");
            Set<Float> heights = new HashSet<>(); double previous = Double.POSITIVE_INFINITY;
            for (int i = 0; i < layers.count(); i++) {
                float height = nearest + layers.shift(i);
                check(heights.add(height), "duplicate cloud image");
                check(Math.abs((height - 192) / period - Math.rint((height - 192) / period)) < 0.001, "cloud shifted by fractional period");
                double distance = Math.abs(height + 2 - camera);
                check(distance <= previous, "cloud images must be far to near"); previous = distance;
            }
            layers.prepare(192, camera, 32, period, false);
            check(layers.count() == 1 && layers.shift(0) == 0, "disabled multi-cloud retains one nearest layer");
        }
        check(layers.prepare(192, -60, 32, 0, true) == 192 && layers.count() == 1, "ordinary cloud height changed");
        layers.prepare(8, 0, Integer.MAX_VALUE, 16, true);
        check(layers.count() == 65, "extreme view distance exceeded cloud capacity");
        boolean packedMesh = checkNativeFaces();
        System.out.println("PASS: periodic clouds retain bounded ordered layers and ordinary controls"
                + (packedMesh ? "; native packed mesh includes both sides within original capacity" : ""));
    }

    private static boolean checkNativeFaces() throws Exception {
        Class<?> type;
        try { type = Class.forName("net.minecraft.client.renderer.CloudRenderer"); }
        catch (ClassNotFoundException legacy) { return false; }
        Method build = null;
        for (Method method : type.getDeclaredMethods()) if (method.getName().equals("buildMesh")
                && java.util.List.of(method.getParameterTypes()).contains(ByteBuffer.class)) build = method;
        if (build == null) return false;
        Object renderer = SeamFixture.allocate(type);
        Field texture = type.getDeclaredField("texture"); texture.setAccessible(true);
        Constructor<?> constructor = texture.getType().getDeclaredConstructor(long[].class, int.class, int.class); constructor.setAccessible(true);
        long[] checkerboard = new long[16];
        for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) if ((x + z) % 2 == 0) checkerboard[z * 4 + x] = (0xffffffffL << 4) | 15;
        texture.set(renderer, constructor.newInstance(checkerboard, 4, 4));
        Method capacity = type.getDeclaredMethod("getSizeForCloudDistance", int.class); capacity.setAccessible(true); build.setAccessible(true);
        Object inside = build.getParameterTypes()[0].getEnumConstants()[1];
        for (int radius : new int[]{1, 4, 16, 32}) for (boolean fancy : new boolean[]{false, true}) {
            ByteBuffer buffer = ByteBuffer.allocate(((Number) capacity.invoke(null, radius)).intValue());
            build.invoke(renderer, inside, buffer, 0, 0, fancy, radius);
            check(buffer.position() > 0 && buffer.position() <= buffer.capacity() && buffer.position() % 3 == 0, "native cloud capacity");
            Set<Integer> faces = new HashSet<>();
            for (int offset = 2; offset < buffer.position(); offset += 3) faces.add(buffer.get(offset) & 7);
            check(faces.contains(0) && (!fancy || faces.contains(1)), "cloud mesh missing a vertical face");
        }
        return true;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
