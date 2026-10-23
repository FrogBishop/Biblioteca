package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Measures line of sight around the ring instead of through the world.
 *
 * <p>The test builds a ray from the observer's eyes to the target's eyes and asks the level whether
 * anything blocks it. Two creatures on either side of the seam are a whole world height apart by
 * that measure, so the ray crosses every layer of the world in between and always hits something:
 * a mob cannot see, and therefore cannot target, anything across the boundary.
 *
 * <p>Only the target's eye row is moved, onto the copy nearest the observer's eyes. The blocking
 * test then walks the few blocks that actually separate the two on the ring, which is also what the
 * movement and reach code already assumes.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySightYLoopMixin {

//? if >=1.21.4 {
    @ModifyVariable(
            method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/ClipContext$Block;"
                    + "Lnet/minecraft/world/level/ClipContext$Fluid;D)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)

//?} else {
/*//? if >=1.21.2 {
    @ModifyExpressionValue(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Ljava/util/function/DoubleSupplier;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/function/DoubleSupplier;getAsDouble()D"))
//?} else {
    @ModifyExpressionValue(
            //? if >=1.17 {
            method = "hasLineOfSight",
            //?} else {
            method = "canSee",
            //?}
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeY()D"))
//?}

*///?}
    private double biblioteca$foldTargetEyeHeight(double targetEyeY) {
        LivingEntity self = (LivingEntity) (Object) this;
        Level level = Compat.level(self);
        if (level == null || !LoopingRegistry.isLooping(level)) return targetEyeY;

        int period = Compat.height(level);
        if (period <= 0) return targetEyeY;

        return SeamPositions.foldToward(targetEyeY, self.getEyeY(), period);
    }
}
