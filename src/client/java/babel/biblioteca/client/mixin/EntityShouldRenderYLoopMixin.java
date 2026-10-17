package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measures the distance to an entity around the ring instead of straight through the world.
 *
 * EntityRenderer.shouldRender asks the entity itself whether it is close enough to bother with,
 * and the vanilla answer is a straight-line distance capped at a few hundred blocks. An entity
 * on the far side of the seam is that far away in absolute terms even though the ring puts it a
 * few blocks from the camera, so it is discarded before its render state is ever extracted.
 * That is the gate that keeps an entity standing at the bottom of the world from appearing
 * overhead when the player is at the top.
 *
 * The vertical term is measured around the ring here, which matches where
 * EntityRenderYLoopMixin is going to draw the entity. FireworkRocketEntity and ThrownTrident
 * override the method with rules of their own and keep vanilla behaviour.
 */
@Mixin(Entity.class)
public abstract class EntityShouldRenderYLoopMixin {

    @Inject(method = "shouldRender(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void biblioteca$ringDistance(
            double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {

        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (level == null) return;
        if (!LoopingRegistry.isLooping(level)) return;

        int period = level.getHeight();
        if (period <= 0) return;

        double xd = self.getX() - camX;
        double yd = self.getY() - camY;
        double zd = self.getZ() - camZ;

        // Keep the vertical term as the offset to the nearest ring layer, not the absolute gap.
        yd -= (double) Math.round(yd / period) * period;

        cir.setReturnValue(self.shouldRenderAtSqrDistance(xd * xd + yd * yd + zd * zd));
    }
}
