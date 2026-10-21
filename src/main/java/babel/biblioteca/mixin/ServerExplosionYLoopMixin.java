package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Damages and throws entities that a blast reaches across the seam.
 *
 * <p>An explosion now finds those entities (the search hook widens its query near a seam), and the
 * damage distance is measured on the ring already. Two coordinate reads still used the entity's
 * absolute position and would have wasted the hit:
 *
 * <ul>
 *   <li>the visibility test samples points inside the entity's box and traces each back to the
 *       blast. A box a whole world height away puts every sample behind the terrain in between,
 *       so the exposure factor collapses to zero and the entity takes no damage;</li>
 *   <li>the knockback direction is the blast centre subtracted from the entity's eyes. Across the
 *       seam that vector points almost straight up or down, so the entity is launched vertically
 *       instead of away from the blast.</li>
 * </ul>
 *
 * <p>Both are answered with the copy of the entity nearest the blast, the same rule the rest of
 * the mod uses. The blast's own centre is the reference because it is the one point the whole
 * calculation already agrees on.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionYLoopMixin {

    @Shadow @Final private Vec3 center;

    /** Sample the copy of the entity box that is next to the blast, not the stored one. */
    @ModifyExpressionValue(
            method = "getSeenPercent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB biblioteca$foldSampledBox(
            AABB box, @Local(argsOnly = true) Vec3 center, @Local(argsOnly = true) Entity entity) {

        Level level = entity.level();
        if (level == null || !LoopingRegistry.isLooping(level)) return box;

        return SeamPositions.foldToward(level, box, center.y);
    }

    @ModifyExpressionValue(
            method = "hurtEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$foldKnockbackOrigin(Vec3 origin) {
        return biblioteca$foldOrigin(origin);
    }

    @ModifyExpressionValue(
            method = "hurtEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$foldKnockbackOriginForTnt(Vec3 origin) {
        return biblioteca$foldOrigin(origin);
    }

    private Vec3 biblioteca$foldOrigin(Vec3 origin) {
        if (origin == null) return null;

        ServerExplosion self = (ServerExplosion) (Object) this;
        Level level = self.level();
        if (level == null || !LoopingRegistry.isLooping(level)) return origin;

        return SeamPositions.foldToward(level, origin, this.center.y);
    }
}
