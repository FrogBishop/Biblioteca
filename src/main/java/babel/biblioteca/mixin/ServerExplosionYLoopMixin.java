package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
//? if >=1.21.2 {
import net.minecraft.world.level.ServerExplosion;
//?} else {
/*import net.minecraft.world.level.Explosion;
*///?}
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
//? if >=1.21.2 {
@Mixin(ServerExplosion.class)
//?} else {
/*@Mixin(Explosion.class)
*///?}
public abstract class ServerExplosionYLoopMixin {

//? if >=1.21.2 {
    @Shadow @Final private Vec3 center;
//?} else {
/*    @Shadow @Final private double y;
    @Shadow @Final private Level level;
*///?}

    /** Sample the copy of the entity box that is next to the blast, not the stored one. */
    @ModifyExpressionValue(
            method = "getSeenPercent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB biblioteca$foldSampledBox(
            AABB box, @Local(argsOnly = true) Vec3 center, @Local(argsOnly = true) Entity entity) {

        Level level = Compat.level(entity);
        if (level == null || !LoopingRegistry.isLooping(level)) return box;

        return SeamPositions.foldToward(level, box, center.y);
    }

//? if >=1.21.9 {
    // NeoForge moves the body into a List<BlockPos> overload; match both signatures.
    @ModifyExpressionValue(
            method = "hurtEntities*",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$foldKnockbackOrigin(Vec3 origin) {
        return biblioteca$foldOrigin(origin);
    }

    @ModifyExpressionValue(
            method = "hurtEntities*",
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

//?} else {
/*    @ModifyExpressionValue(
            //? if >=1.21.2 {
            method = "hurtEntities",
            //?} else {
            method = "explode",
            //?}
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeY()D"))
    private double biblioteca$foldEyeY(double y) { return biblioteca$foldOriginY(y); }

    @ModifyExpressionValue(
            //? if >=1.21.2 {
            method = "hurtEntities",
            //?} else {
            method = "explode",
            //?}
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D"))
    private double biblioteca$foldTntY(double y) { return biblioteca$foldOriginY(y); }

    private double biblioteca$foldOriginY(double originY) {
        //? if >=1.21.2 {
        Level level = ((ServerExplosion) (Object) this).level();
        double centerY = this.center.y;
        //?} else {
        Level level = this.level;
        double centerY = this.y;
        //?}
        return LoopingRegistry.isLooping(level) ? SeamPositions.foldForDistance(level, originY, centerY) : originY;
    }

*///?}
}
