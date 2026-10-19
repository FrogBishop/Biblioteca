package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the 1.21.1 boolean cube test for allocation-free seam scans. */
@Mixin(Frustum.class)
public interface LegacyFrustumInvoker {

    @Invoker("cubeInFrustum")
    boolean biblioteca$cubeInFrustum(double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ);
}
