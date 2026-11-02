package biblioteca.sodiumtest.mixin;

import biblioteca.sodiumtest.SodiumProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.jellysquid.mods.sodium.client.model.vertex.VertexDrain;
import me.jellysquid.mods.sodium.client.model.vertex.formats.particle.ParticleVertexSink;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Observe the vertices actually sent to Sodium, preserving its optimized particle sink. */
@Mixin(value = SingleQuadParticle.class, priority = 500)
public abstract class SodiumParticleObserver extends Particle {
    protected SodiumParticleObserver(ClientLevel level, double x, double y, double z) { super(level, x, y, z); }

    @WrapMethod(method = "render")
    private void observe(VertexConsumer vertices, Camera camera, float partial, Operation<Void> original) {
        Particle particle = (Particle) (Object) this;
        if (!SodiumProbe.tracked(particle)) { original.call(vertices, camera, partial); return; }
        double currentY = y, previousY = yo;
        int previousAge = age, previousLifetime = lifetime;
        double[] sum = {0}; int[] count = {0}; boolean[] optimized = {false};
        VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class, VertexDrain.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createSink")) {
                        Object sink;
                        try { sink = method.invoke(vertices, args); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                        optimized[0] = true;
                        return Proxy.newProxyInstance(ParticleVertexSink.class.getClassLoader(), new Class<?>[]{ParticleVertexSink.class}, (p, call, values) -> {
                            if (call.getName().equals("writeParticle")) { sum[0] += ((Number) values[1]).doubleValue(); count[0]++; }
                            try { return call.invoke(sink, values); }
                            catch (InvocationTargetException failure) { throw failure.getCause(); }
                        });
                    }
                    if (method.getName().equals("vertex") && args.length == 3 && args[0] instanceof Number) {
                        sum[0] += ((Number) args[1]).doubleValue(); count[0]++;
                    }
                    try {
                        Object value = method.invoke(vertices, args);
                        return value == vertices ? proxy : value;
                    } catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        original.call(consumer, camera, partial);
        if (y != currentY || yo != previousY || age != previousAge || lifetime != previousLifetime)
            throw new AssertionError("Particle rendering mutated simulation/lifetime");
        if (count[0] != 4) throw new AssertionError("Expected four native particle vertices, got " + count[0]);
        SodiumProbe.particleRendered(particle, camera.getPosition().y, sum[0] / count[0], optimized[0], previousAge);
    }
}
