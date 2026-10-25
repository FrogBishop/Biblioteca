package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CrossbowAttackMob.class)
public interface LegacyCrossbowAimYLoopMixin {
    @WrapOperation(method = "shootCrossbowProjectile(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/projectile/Projectile;FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY(D)D"))
    private double biblioteca$nearTarget(LivingEntity target, double fraction, Operation<Double> original, @Local(argsOnly = true) Projectile projectile) {
        double y = original.call(target, fraction);
        return LoopingRegistry.isLooping(Compat.level(projectile))
                ? SeamPositions.foldForDistance(Compat.level(projectile), y, projectile.getY()) : y;
    }
}
