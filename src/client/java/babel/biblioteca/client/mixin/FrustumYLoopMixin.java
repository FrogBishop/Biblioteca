package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Culls boxes by their ring position instead of their absolute one.
 *
 * Everything that asks "is this in view" funnels through the private cube test below:
 * isVisible(AABB) and cubeInFrustum(BoundingBox) both end up here, and the section
 * occlusion octree uses the BoundingBox form. Patching only isVisible() therefore left the
 * octree culling whole branches at the world's bottom and top, which is why nothing on the
 * far side of the seam was ever considered visible.
 *
 * A box counts as visible when its periodic twin is in view. Only the nearest twin can be
 * in front of the camera, so a single offset is enough. The answer is reported as
 * "partially visible" rather than "fully visible" so the octree keeps descending instead of
 * trusting a box it never actually tested.
 */
@Mixin(value = Frustum.class, priority = 900)
public abstract class FrustumYLoopMixin {

    @Shadow private double camY;

    private static final ThreadLocal<Boolean> BIBLIOTECA_IN_MIRROR_TEST =
            ThreadLocal.withInitial(() -> false);

    @Inject(method = "cubeInFrustum(DDDDDD)I", at = @At("RETURN"), cancellable = true, require = 1)
    private void biblioteca$visibleAtRingPosition(
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            CallbackInfoReturnable<Integer> cir) {

        if (cir.getReturnValue() != 0) return;
        if (BIBLIOTECA_IN_MIRROR_TEST.get()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (!LoopingDimensionCache.isLooping(minecraft.level.dimension())) return;

        double period  = minecraft.level.getHeight();
        double centreY = (minY + maxY) * 0.5;
        long   nearest = Math.round((camY - centreY) / period);

        BIBLIOTECA_IN_MIRROR_TEST.set(true);
        try {
            for (long step = -1; step <= 1; step++) {
                long offset = nearest + step;
                if (offset == 0) continue;

                int shifted = ((FrustumInvoker) (Object) this).biblioteca$cubeInFrustum(
                        minX, minY + offset * period, minZ,
                        maxX, maxY + offset * period, maxZ);
                if (shifted == -2 || shifted == -1) {
                    cir.setReturnValue(-1);
                    return;
                }
            }
        } finally {
            BIBLIOTECA_IN_MIRROR_TEST.set(false);
        }
    }
}