package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The snowball's muzzle position has already wrapped when the spawn callback computes its aim. */
@Mixin(SnowGolem.class)
public abstract class SnowGolemAimYLoopMixin {
    //? if >=1.21.2 {
    //? if >=26.1 {
    @ModifyVariable(method = "lambda$performRangedAttack$0", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    //?} else {
    /*@ModifyVariable(method = "method_63010", remap = false, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    *///?}
    //? if >=26.3 {
    private double biblioteca$nearSnowballTarget(double targetY, @Local(argsOnly = true) Snowball projectile) {
    //?} else {
    /*private static double biblioteca$nearSnowballTarget(double targetY, @Local(argsOnly = true) Snowball projectile) {
    *///?}
        return LoopingRegistry.isLooping(Compat.level(projectile))
                ? SeamPositions.foldForDistance(Compat.level(projectile), targetY, projectile.getY()) : targetY;
    }
    //?} else {
    /*@WrapOperation(method = "performRangedAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/throwableitemprojectile/Snowball;getY()D"))
    private double biblioteca$nearSnowballMuzzle(Snowball projectile, Operation<Double> original, @Local(argsOnly = true) LivingEntity target) {
        double y = original.call(projectile);
        return LoopingRegistry.isLooping(Compat.level(projectile))
                ? SeamPositions.foldForDistance(Compat.level(projectile), y, target.getEyeY() - 1.1F) : y;
    }
    *///?}
}
