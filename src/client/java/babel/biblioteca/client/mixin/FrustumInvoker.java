package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes Frustum's private cube test so the ring culling can re-test a shifted box. */
@Mixin(Frustum.class)
public interface FrustumInvoker {

    @Invoker("cubeInFrustum")
    int biblioteca$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}