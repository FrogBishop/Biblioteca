package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets a mob land a melee hit on a target on the other side of the seam.
 *
 * <p>Melee reach is not a distance here: the mob's attack box is intersected with the target's
 * hitbox. Two boxes a whole world height apart never intersect, so the mob walks up to the target,
 * sees it, and still swings at nothing. Moving the target's hitbox onto the copy nearest the mob
 * makes the intersection the one the ring has.
 *
 * <p>The player side of the same question is a distance check and is already covered by the
 * attack-range hook; this is the mob-side, box-based variant of it.
 */
@Mixin(Mob.class)
public abstract class MobMeleeRangeYLoopMixin {

    @ModifyVariable(
            method = "isWithinMeleeAttackRange",
            at = @At("STORE"), ordinal = 0)
    private AABB biblioteca$foldTargetHitbox(AABB hitbox) {
        Mob self = (Mob) (Object) this;
        Level level = self.level();
        if (hitbox == null || level == null || !LoopingRegistry.isLooping(level)) return hitbox;

        return SeamPositions.foldToward(level, hitbox, self.getY() + (double) self.getBbHeight() * 0.5);
    }
}
