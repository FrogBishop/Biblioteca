package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measures block interaction range around the ring instead of straight through the world.
 *
 * Vanilla measures the straight line from the eye to the block's box. In a looping dimension the
 * block the player is aiming at may be the copy one period away, which is a whole world height from
 * the eye in absolute terms and outside any reach. The block is therefore also tested one period up
 * and one period down, and the shortest of the three distances is used - the same question vanilla
 * asks, just around the ring.
 *
 * Only the looping case is replaced; in every other dimension vanilla's method runs untouched.
 */
@Mixin(Player.class)
public abstract class PlayerReachMixin {

    @Inject(method = "isWithinBlockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void biblioteca$toroidalReach(
            BlockPos pos, double delta, CallbackInfoReturnable<Boolean> cir) {

        Player self = (Player) (Object) this;
        Level level = Compat.level(self);
        if (!LoopingRegistry.isLooping(level)) return;

        double range = self.blockInteractionRange() + delta;
        int height = Compat.height(level);
        Vec3 eye = self.getEyePosition();

        double minDistSq = Math.min(
                new AABB(pos).distanceToSqr(eye),
                Math.min(
                        aabbDistSq(pos, height, eye),
                        aabbDistSq(pos, -height, eye)));

        cir.setReturnValue(minDistSq < range * range);
    }

    private static double aabbDistSq(BlockPos pos, int yShift, Vec3 eye) {
        return new AABB(
                pos.getX(), pos.getY() + yShift, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0 + yShift, pos.getZ() + 1.0
        ).distanceToSqr(eye);
    }
}
