package biblioteca.sodiumscene;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.system.MemoryUtil;

/** Forward to the real GPU writer while observing both Sodium particle writer generations. */
public final class ParticleForwarder {
    public double sum;
    public int count;
    public boolean optimized;
    private static void interfaces(Class<?> type, Set<Class<?>> result) {
        if (type == null) return;
        for (Class<?> itf : type.getInterfaces()) {
            if (Modifier.isPublic(itf.getModifiers()) && itf.getName().contains("sodium")
                    && (itf.getSimpleName().contains("Writer") || itf.getSimpleName().contains("Drain") || itf.getSimpleName().contains("Sink"))) result.add(itf);
            interfaces(itf, result);
        }
        interfaces(type.getSuperclass(), result);
    }
    public VertexConsumer wrap(VertexConsumer original) {
        Set<Class<?>> interfaces = new LinkedHashSet<>(); interfaces.add(VertexConsumer.class); interfaces(original.getClass(), interfaces);
        return (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), interfaces.toArray(Class<?>[]::new), (proxy, method, args) -> {
            if (method.getName().equals("createSink")) {
                Object sink = forward(method, original, args);
                Set<Class<?>> sinkInterfaces = new LinkedHashSet<>(); interfaces(sink.getClass(), sinkInterfaces);
                optimized = true;
                return Proxy.newProxyInstance(sink.getClass().getClassLoader(), sinkInterfaces.toArray(Class<?>[]::new), (p, call, values) -> {
                    if (call.getName().equals("writeParticle")) { sum += ((Number)values[1]).doubleValue(); count++; }
                    return forward(call, sink, values);
                });
            }
            if (method.getName().equals("push") && args.length == 4 && args[1] instanceof Long pointer && args[2] instanceof Integer vertices) {
                int stride = ((Number)(SceneProbe.has(args[3], "getVertexSize", 0)
                        ? SceneProbe.invoke(args[3], "getVertexSize") : SceneProbe.field(args[3], "stride"))).intValue();
                for (int i = 0; i < vertices; i++) { sum += MemoryUtil.memGetFloat(pointer + (long)i * stride + 4); count++; }
                optimized = true;
            } else if ((method.getName().equals("vertex") || method.getName().equals("addVertex")) && args.length == 3 && args[1] instanceof Number) {
                sum += ((Number)args[1]).doubleValue(); count++;
            }
            Object result = forward(method, original, args);
            return result == original ? proxy : result;
        });
    }
    private static Object forward(Method method, Object target, Object[] args) throws Throwable {
        try { return method.invoke(target, args); } catch (InvocationTargetException error) { throw error.getCause(); }
    }
}
