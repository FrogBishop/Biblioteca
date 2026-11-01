package babel.biblioteca.validation;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Exercises the native billboard method, keeping the same particle alive across camera crossings. */
public final class ParticleRenderChecks implements Opcodes {
    public static void run() throws Exception {
        Class<?> worldType = worldType();
        Class<?> cameraType = cameraType();
        Method nativeDraw = null, sink = null;
        for (Method m : SingleQuadParticle.class.getDeclaredMethods()) {
            boolean camera = List.of(m.getParameterTypes()).contains(Camera.class);
            if (camera && (m.getName().equals("extractRotatedQuad") || m.getName().equals("renderRotatedQuad"))) nativeDraw = m;
            if (!camera && (m.getName().equals("extractRotatedQuad") || m.getName().equals("renderRotatedQuad"))) sink = m;
        }
        if (nativeDraw == null) {
            for (Method m : SingleQuadParticle.class.getDeclaredMethods()) if (m.getName().equals("render")) nativeDraw = m;
        }
        if (nativeDraw == null) throw new AssertionError("Native particle draw method missing");
        nativeDraw.setAccessible(true);
        Class<?> particleType = particleType(SingleQuadParticle.class, sink, "CaptureParticle");
        ClientLevel level = (ClientLevel) SeamFixture.allocate(worldType);
        Camera camera = (Camera) SeamFixture.allocate(cameraType);
        Particle particle = (Particle) SeamFixture.allocate(particleType);
        put(particle, "level", level);
        put(particle, "age", 37); put(particle, "lifetime", 160);
        for (Class<?> type = SingleQuadParticle.class; type != null; type = type.getSuperclass()) {
            for (Field f : type.getDeclaredFields()) if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                    && f.getType().getSimpleName().contains("Quaternion")) {
                f.setAccessible(true); f.set(particle, quaternion(f.getType()));
            }
        }
        for (Method m : Camera.class.getMethods()) if ((m.getName().equals("rotation") || m.getName().equals("getRotation")) && m.getParameterCount() == 0) {
            put(camera, "rotationValue", quaternion(m.getReturnType()));
        }
        for (int period : new int[]{16, 384}) {
            int minimum = SeamFixture.hasSections() ? -64 : 0;
            put(level, "minimum", minimum); put(level, "period", period);
            for (boolean looping : new boolean[]{true, false}) {
                put(level, "looping", looping);
                for (int direction : new int[]{-1, 1}) {
                    double y = direction > 0 ? minimum + period - 1.25 : minimum + 1.25;
                    put(particle, "y", y); put(particle, "yo", y - 0.25);
                    for (double eye : new double[]{minimum + 0.5, minimum + period - 0.5, minimum + 0.5,
                            minimum + 3.0 * period + 0.5}) {
                        put(camera, "positionValue", new Vec3(0, eye, 0));
                        for (float alpha : new float[]{0, 0.5f, 1}) {
                            double expected = y - 0.25 + alpha * 0.25 - eye;
                            if (looping) {
                                while (expected > period * 0.5) expected -= period;
                                while (expected < -period * 0.5) expected += period;
                            }
                            double actual = draw(particle, camera, nativeDraw, sink, alpha);
                            check(Math.abs(actual - expected) < 0.0001, "native particle position after crossing: " + actual + " expected " + expected);
                            check(get(particle, "age").equals(37) && get(particle, "lifetime").equals(160), "particle lifetime was reset");
                            check(get(particle, "y").equals(y) && get(particle, "yo").equals(y - 0.25), "particle simulation/interpolation state was mutated");
                        }
                    }
                }
            }
        }
        checkVibrations(level, camera, sink);
        checkPointCulling(level, worldType);
        System.out.println("PASS: native living-particle rendering preserves age/trajectory across repeated seams, interpolation and ordinary worlds; periodic point culling");
    }

    private static double draw(Particle particle, Camera camera, Method method, Method sink, float alpha) throws Exception {
        List<Double> vertices = new ArrayList<>();
        Object[] args = new Object[method.getParameterCount()];
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (types[i] == Camera.class) args[i] = camera;
            else if (types[i] == float.class) args[i] = alpha;
            else if (types[i].getSimpleName().contains("Quaternion")) args[i] = quaternion(types[i]);
            else if (java.util.function.Consumer.class.isAssignableFrom(types[i])) args[i] = (java.util.function.Consumer<Object>) value -> {};
            else if (sink == null && types[i].isInterface()) args[i] = Proxy.newProxyInstance(
                    types[i].getClassLoader(), new Class<?>[]{types[i]}, (proxy, called, values) -> {
                        if ((called.getName().equals("vertex") || called.getName().equals("addVertex"))
                                && values.length >= 3 && values[1] instanceof Number number) vertices.add(number.doubleValue());
                        return called.getReturnType().isInstance(proxy) ? proxy : null;
                    });
        }
        method.setAccessible(true); method.invoke(particle, args);
        return sink == null ? vertices.stream().mapToDouble(Double::doubleValue).average().orElseThrow()
                : ((Number) get(particle, "drawY")).doubleValue();
    }

    private static void checkVibrations(ClientLevel level, Camera camera, Method sink) throws Exception {
        Class<?> vibration;
        try { vibration = Class.forName("net.minecraft.client.particle.VibrationSignalParticle"); }
        catch (ClassNotFoundException older) { return; }
        Class<?> capture = particleType(vibration, sink, "CaptureVibrationParticle");
        Method render = java.util.Arrays.stream(vibration.getDeclaredMethods())
                .filter(m -> (m.getName().equals("render") || m.getName().equals("extract")) && m.getParameterCount() == 3)
                .findFirst().orElseThrow();
        for (int period : new int[]{16, 384}) for (boolean looping : new boolean[]{true, false}) {
            int min = -64, top = min + period;
            put(level, "minimum", min); put(level, "period", period); put(level, "looping", looping);
            Particle particle = (Particle) SeamFixture.allocate(capture);
            put(particle, "level", level); put(particle, "x", 0.5); put(particle, "y", top - 0.5); put(particle, "z", 0.5);
            put(particle, "age", 0); put(particle, "lifetime", 4);
            Object target = Class.forName("net.minecraft.world.level.gameevent.BlockPositionSource")
                    .getConstructor(net.minecraft.core.BlockPos.class).newInstance(new net.minecraft.core.BlockPos(0, min, 0));
            try { put(particle, "target", target); }
            catch (NoSuchFieldException legacy) {
                Class<?> path = Class.forName("net.minecraft.world.level.gameevent.vibrations.VibrationPath");
                Object trajectory = path.getConstructors()[0].newInstance(new net.minecraft.core.BlockPos(0, top - 1, 0), target, 4);
                put(particle, "vibrationPath", trajectory);
            }
            particle.tick();
            double movement = ((Number) get(particle, "y")).doubleValue() - (top - 0.5);
            check(looping ? Math.abs(movement) < 2 : Math.abs(movement) > 2, "native vibration trajectory uses the wrong destination image");
            put(camera, "positionValue", new Vec3(0, min + 0.5, 0));
            double display = draw(particle, camera, render, sink, 0.5f);
            check(looping ? Math.abs(display) < 2 : Math.abs(display) > 2, "native vibration display uses the wrong camera image");
        }
        System.out.println("PASS: native vibration particle tick/render, legacy path origins and independent camera frame");
    }

    private static void checkPointCulling(ClientLevel level, Class<?> worldType) throws Exception {
        Class<?> frustum = Class.forName("net.minecraft.client.renderer.culling.Frustum");
        Method point;
        try { point = frustum.getMethod("pointInFrustum", double.class, double.class, double.class); }
        catch (NoSuchMethodException older) { return; }
        Field singleton = field(Minecraft.class, "instance");
        Object old = singleton.get(null), mc = old == null ? SeamFixture.allocate(Minecraft.class) : old;
        Field world = field(Minecraft.class, "level"); Object previousLevel = world.get(mc);
        try {
            singleton.set(null, mc); world.set(mc, level);
            put(level, "minimum", -64); put(level, "period", 384); put(level, "looping", true);
            Object matrix = Class.forName("org.joml.Matrix4f").getConstructor().newInstance();
            var constructor = java.util.Arrays.stream(frustum.getConstructors()).filter(c -> c.getParameterCount() == 2).findFirst().orElseThrow();
            Object view = constructor.newInstance(matrix, matrix);
            frustum.getMethod("prepare", double.class, double.class, double.class).invoke(view, 0, -63.75, 0);
            check((boolean) point.invoke(view, 0.0, 319.75, 0.0), "living particle was culled before periodic extraction");
            put(level, "looping", false);
            check(!(boolean) point.invoke(view, 0.0, 319.75, 0.0), "ordinary point culling changed");
        } finally { world.set(mc, previousLevel); singleton.set(null, old); }
    }

    private static ClassWriter writer(String name, Class<?> parent) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(V17, ACC_PUBLIC, name, null, Type.getInternalName(parent), null);
        return w;
    }
    private static Class<?> worldType() throws Exception {
        String name = "babel/biblioteca/validation/ParticleTestLevel";
        ClassWriter w = writer(name, ClientLevel.class);
        for (String f : new String[]{"minimum", "period", "looping"}) w.visitField(ACC_PUBLIC, f, f.equals("looping") ? "Z" : "I", null, null).visitEnd();
        getter(w, name, "getHeight", "period", "I");
        getter(w, name, "getMaxBuildHeight", "period", "I");
        getter(w, name, "getMinBuildHeight", "minimum", "I");
        getter(w, name, "getMinY", "minimum", "I");
        getter(w, name, "biblioteca$isLooping", "looping", "Z");
        w.visitEnd(); return MethodHandles.lookup().defineClass(w.toByteArray());
    }
    private static Class<?> cameraType() throws Exception {
        String name = "babel/biblioteca/validation/ParticleTestCamera";
        ClassWriter w = writer(name, Camera.class);
        w.visitField(ACC_PUBLIC, "positionValue", Type.getDescriptor(Vec3.class), null, null).visitEnd();
        for (Method m : Camera.class.getMethods()) if (m.getParameterCount() == 0) {
            if (m.getName().equals("position") || m.getName().equals("getPosition")) getter(w, name, m.getName(), "positionValue", Type.getDescriptor(Vec3.class));
            if (m.getName().equals("rotation") || m.getName().equals("getRotation")) {
                w.visitField(ACC_PUBLIC, "rotationValue", Type.getDescriptor(m.getReturnType()), null, null).visitEnd();
                getter(w, name, m.getName(), "rotationValue", Type.getDescriptor(m.getReturnType()));
            }
        }
        w.visitEnd(); return MethodHandles.lookup().defineClass(w.toByteArray());
    }
    private static Class<?> particleType(Class<?> parent, Method sink, String simpleName) throws Exception {
        String name = "babel/biblioteca/validation/" + simpleName;
        ClassWriter w = writer(name, parent);
        w.visitField(ACC_PUBLIC, "drawY", "F", null, null).visitEnd();
        if (sink != null) {
            MethodVisitor mv = w.visitMethod(ACC_PUBLIC, sink.getName(), Type.getMethodDescriptor(sink), null, null);
            mv.visitCode(); mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(FLOAD, 4); mv.visitFieldInsn(PUTFIELD, name, "drawY", "F");
            mv.visitInsn(RETURN); mv.visitMaxs(0, 0); mv.visitEnd();
        }
        for (String method : new String[]{"getU0", "getU1", "getV0", "getV1", "getQuadSize", "getLightColor", "getLightCoords"}) {
            String desc = method.equals("getQuadSize") ? "(F)F" : method.startsWith("getLight") ? "(F)I" : "()F";
            MethodVisitor mv = w.visitMethod(ACC_PUBLIC, method, desc, null, null); mv.visitCode();
            boolean integer = method.startsWith("getLight"); mv.visitInsn(integer ? ICONST_0 : FCONST_0); mv.visitInsn(integer ? IRETURN : FRETURN); mv.visitMaxs(0, 0); mv.visitEnd();
        }
        w.visitEnd(); return MethodHandles.lookup().defineClass(w.toByteArray());
    }
    private static void getter(ClassWriter w, String owner, String name, String field, String type) {
        MethodVisitor mv = w.visitMethod(ACC_PUBLIC, name, "()" + type, null, null);
        mv.visitCode(); mv.visitVarInsn(ALOAD, 0); mv.visitFieldInsn(GETFIELD, owner, field, type);
        mv.visitInsn(type.length() == 1 ? IRETURN : ARETURN); mv.visitMaxs(0, 0); mv.visitEnd();
    }
    private static Object quaternion(Class<?> type) throws Exception {
        try { return type.getConstructor().newInstance(); }
        catch (NoSuchMethodException older) { return type.getConstructor(float.class, float.class, float.class, float.class).newInstance(0, 0, 0, 1); }
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    private static void put(Object value, String name, Object data) throws Exception { field(value.getClass(), name).set(value, data); }
    private static Object get(Object value, String name) throws Exception { return field(value.getClass(), name).get(value); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
