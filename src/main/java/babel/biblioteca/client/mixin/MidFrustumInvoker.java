package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the 1.21.8 integer cube test for allocation-free seam scans. */
@Mixin(Frustum.class)
public interface MidFrustumInvoker {

    @Invoker("cubeInFrustum")
    int biblioteca$cubeInFrustum(double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ);
}
