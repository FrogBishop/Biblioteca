package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes Frustum's private cube test so the ring culling can re-test a shifted box. */
@Mixin(Frustum.class)
public interface FrustumInvoker {
    @Accessor("camX") double biblioteca$cameraX();
    @Accessor("camY") double biblioteca$cameraY();
    @Accessor("camZ") double biblioteca$cameraZ();

    @Invoker("cubeInFrustum")
    //? if >=1.21.2 {
    int
    //?} else {
    /*boolean
    *///?}
    biblioteca$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}
