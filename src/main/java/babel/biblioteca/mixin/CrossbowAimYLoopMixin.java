package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob targeting only; the player's rotation-based shot and vanilla multishot angles stay intact. */
@Mixin(CrossbowItem.class)
public abstract class CrossbowAimYLoopMixin {
    @WrapOperation(method = "shootProjectile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/Projectile;getY()D"))
    private double biblioteca$nearMuzzle(Projectile projectile, Operation<Double> original,
            @Local(argsOnly = true, ordinal = 1) LivingEntity target) {
        double y = original.call(projectile);
        return LoopingRegistry.isLooping(Compat.level(projectile))
                ? SeamPositions.foldForDistance(Compat.level(projectile), y, target.getY(1.0 / 3.0)) : y;
    }
}
