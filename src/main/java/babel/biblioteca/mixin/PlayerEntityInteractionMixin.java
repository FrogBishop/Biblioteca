package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measures entity interaction reach around the ring.
 *
 * <p>The same problem as block reach, one step further: a villager standing on the other side of the
 * seam is a few blocks away on the ring but a whole world height away in absolute terms, so the
 * server rejects every interaction packet. The client will not even send one, because its own pick
 * never returns that entity.
 *
 * <p>Both of those are answered by measuring the distance to the nearest copy of the target's box;
 * this hook covers the server-side check. {@code ProjectileUtilYLoopMixin} and
 * {@code EntityRenderDispatcherYLoopMixin} cover the client-side pick and name display.
 */
@Mixin(Player.class)
public abstract class PlayerEntityInteractionMixin {

    @Inject(method = "isWithinEntityInteractionRange(Lnet/minecraft/world/phys/AABB;D)Z",
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$ringReach(AABB box, double buffer, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        Level level = self.level();
        if (!LoopingRegistry.isLooping(level)) return;

        double maxRange = self.entityInteractionRange() + buffer;
        double distanceSq = SeamPositions.distanceToSqr(level, self.getEyePosition(), box);
        cir.setReturnValue(distanceSq < maxRange * maxRange);
    }
}
