package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import babel.biblioteca.client.LoopingRenderSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends the frustum visibility test so a culled AABB is considered visible
 * if any of its Y-shifted mirror positions falls within [camY-range, camY+range]
 * AND passes the frustum test at that shifted position.
 *
 * Re-entry guard: the recursive call to isVisible() is blocked by a ThreadLocal
 * flag, so the injector fires only on the outermost call.
 */
@Mixin(value = Frustum.class, priority = 900)
public abstract class FrustumYLoopMixin {

    // Prevents re-entrant firing of this injector when we call isVisible() below.
    private static final ThreadLocal<Boolean> biblioteca$inMirrorCheck =
            ThreadLocal.withInitial(() -> false);

    @Inject(method = "isVisible(Lnet/minecraft/world/phys/AABB;)Z",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void biblioteca$checkMirrorVisibility(AABB aabb, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (biblioteca$inMirrorCheck.get()) return;  // prevent recursion

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!LoopingDimensionCache.isLooping(mc.level.dimension())) return;

        int    height = mc.level.getHeight();
        int    range  = LoopingRenderSettings.get();
        double camY   = mc.player.getEyeY();

        biblioteca$inMirrorCheck.set(true);
        try {
            Frustum self = (Frustum) (Object) this;
            int triggered = 0;
            for (int dir = -1; dir <= 1; dir += 2) {
                for (int n = 1; n <= 64; n++) {
                    double dy = (double) dir * n * height;

                    double shiftedMinY = aabb.minY + dy;
                    double shiftedMaxY = aabb.maxY + dy;

                    double nearEdge = camY < shiftedMinY ? shiftedMinY - camY
                                    : camY > shiftedMaxY ? camY - shiftedMaxY
                                    : 0;
                    if (nearEdge > range) break;

                    if (self.isVisible(new AABB(
                            aabb.minX, shiftedMinY, aabb.minZ,
                            aabb.maxX, shiftedMaxY, aabb.maxZ))) {
                        triggered++;
                        if (triggered == 1) {
                            babel.biblioteca.Biblioteca.LOGGER.info(
                                "[Biblioteca] FrustumLoop triggered: aabb.y=[{},{}] shift={} camY={}",
                                String.format("%.1f", aabb.minY), String.format("%.1f", aabb.maxY),
                                dy, String.format("%.1f", camY));
                        }
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        } finally {
            biblioteca$inMirrorCheck.set(false);
        }
    }
}
