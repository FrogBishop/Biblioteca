package babel.biblioteca.validation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Capture vertices emitted by the native fishing curve, including ordinary-world controls. */
public final class AttachmentRenderChecks {
    public static void run() throws Exception {
        Class<?> worldType = Class.forName("babel.biblioteca.validation.ParticleTestLevel");
        Object level = SeamFixture.allocate(worldType);
        worldType.getField("minimum").setInt(level, 0);
        Field singleton = Minecraft.class.getDeclaredField("instance"); singleton.setAccessible(true);
        Field world = Minecraft.class.getDeclaredField("level"); world.setAccessible(true);
        Object old = singleton.get(null), mc = old == null ? SeamFixture.allocate(Minecraft.class) : old, oldLevel = world.get(mc);
        Method draw = java.util.Arrays.stream(FishingHookRenderer.class.getDeclaredMethods()).filter(m -> m.getName().equals("stringVertex")).findFirst().orElseThrow();
        draw.setAccessible(true);
        try {
            singleton.set(null, mc); world.set(mc, level);
            for (int period : new int[]{16, 384}) for (boolean looping : new boolean[]{true, false}) for (int direction : new int[]{-1, 1}) {
                worldType.getField("period").setInt(level, period); worldType.getField("looping").setBoolean(level, looping);
                List<Double> vertices = new ArrayList<>(); Object[] args = new Object[draw.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    Class<?> type = draw.getParameterTypes()[i];
                    if (type == float.class) args[i] = i == 1 ? direction * (float) (period - 1) : 1.0f;
                    else if (type.isInterface()) args[i] = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, values) -> {
                        if ((method.getName().equals("vertex") || method.getName().equals("addVertex")) && values.length >= 3) {
                            int y = values[0] instanceof Number ? 1 : 2;
                            vertices.add(((Number) values[y]).doubleValue());
                        }
                        return method.getReturnType().isInstance(proxy) ? proxy : null;
                    });
                    else if (type.getSimpleName().equals("Pose")) args[i] = new PoseStack().last();
                    else if (type.getSimpleName().equals("Matrix4f")) {
                        args[i] = type.getConstructor().newInstance();
                        try { type.getMethod("setIdentity").invoke(args[i]); } catch (NoSuchMethodException joml) { }
                    }
                }
                draw.invoke(null, args);
                double expected = (looping ? -direction : direction * (period - 1)) + 0.25;
                if (vertices.isEmpty() || vertices.stream().anyMatch(y -> Math.abs(y - expected) > 0.001))
                    throw new AssertionError("native fishing endpoint " + vertices + " expected " + expected);
            }
        } finally { world.set(mc, oldLevel); singleton.set(null, old); }
        System.out.println("PASS: native fishing-line vertices take the short periodic path in both directions and retain ordinary-world endpoints");
    }
}
