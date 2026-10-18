package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.SeamCull;
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
 * A box counts as visible when a periodic twin of it is in view. Which twin that would be depends on
 * where the box sits relative to the camera's own row, so the three offsets around the nearest one
 * are tested; testing only the nearest would miss boxes that straddle the boundary between two rows.
 * The answer is reported as "partially visible" rather than "fully visible" so the octree keeps
 * descending instead of trusting a box it never actually tested.
 *
 * The test runs whenever the plain test rejected the box, which is what the return value has
 * to be read for. JOML's FrustumIntersection.intersectAab returns -2 for a box fully inside,
 * -1 for one that intersects, and an outside box reports 0 when the first plane culled it and
 * 1 when any later plane did. So "rejected" is result >= 0, not result == 0: reading only 0
 * would silently drop every box culled by anything but the left plane, which is most of them
 * at most angles.
 */
@Mixin(value = Frustum.class, priority = 900)
public abstract class FrustumYLoopMixin {

    @Shadow private double camY;

    @Inject(method = "cubeInFrustum(DDDDDD)I", at = @At("RETURN"), cancellable = true)
    private void biblioteca$visibleAtRingPosition(
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            CallbackInfoReturnable<Integer> cir) {

        if (cir.getReturnValue() < 0) return;
        if (SeamCull.inTwinTest()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (!LoopingRegistry.isLooping(minecraft.level)) return;

        double period  = minecraft.level.getHeight();
        double centreY = (minY + maxY) * 0.5;
        long   nearest = Math.round((camY - centreY) / period);

        SeamCull.enterTwinTest();
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
            SeamCull.exitTwinTest();
        }
    }
}
