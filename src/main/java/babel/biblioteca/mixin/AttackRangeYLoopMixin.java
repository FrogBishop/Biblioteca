package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measures attack reach around the ring.
 *
 * <p>Attacks use their own reach component rather than the interaction-range check, with a minimum
 * and maximum distance. Both are absolute, so an entity across the seam is out of reach in the same
 * way a villager is; the mob side is affected too, because a mob's attack range is computed from the
 * same component.
 */
@Mixin(AttackRange.class)
public class AttackRangeYLoopMixin {

    @Inject(method = "isInRange(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;D)Z",
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$ringReach(
            LivingEntity attacker, AABB box, double extraBuffer, CallbackInfoReturnable<Boolean> cir) {

        Level level = attacker.level();
        if (!LoopingRegistry.isLooping(level)) return;

        AttackRange self = (AttackRange) (Object) this;
        double distance = Math.sqrt(SeamPositions.distanceToSqr(level, attacker.getEyePosition(), box));
        double minReach = self.effectiveMinRange(attacker) - self.hitboxMargin() - extraBuffer;
        double maxReach = self.effectiveMaxRange(attacker) + self.hitboxMargin() + extraBuffer;
        cir.setReturnValue(distance >= minReach && distance <= maxReach);
    }
}
