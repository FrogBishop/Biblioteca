package biblioteca.periodicprobe.mixin;

import biblioteca.periodicprobe.PeriodicVisualChecks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

/** Observes the actual native GPU submission after Biblioteca expands its draw. */
@Mixin(CloudRenderer.class)
public abstract class VisualCloudObserver {
    @WrapMethod(method = "render(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V")
    private void probe(RenderPass pass, RenderPipeline pipeline, Operation<Void> original) {
        int[] draws = {0}; Object[] transform = {null}; Set<Object> slices = new HashSet<>();
        RenderPass observer = (RenderPass) Proxy.newProxyInstance(RenderPass.class.getClassLoader(), new Class<?>[]{RenderPass.class}, (proxy, method, args) -> {
            if (method.getName().equals("setUniform") && args[0].equals("DynamicTransforms")) transform[0] = args[1];
            if (method.getName().equals("drawIndexed")) { draws[0]++; slices.add(transform[0]); }
            try { return method.invoke(pass, args); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        });
        original.call(observer, pipeline);
        PeriodicVisualChecks.clouds(this, draws[0], slices.size());
    }
}
