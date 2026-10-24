package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShulkerBullet.class)
public abstract class ShulkerBulletYLoopMixin {
    @WrapOperation(method = "selectNextMoveDirection", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;containing(DDD)Lnet/minecraft/core/BlockPos;"))
    private BlockPos biblioteca$nearHomingTarget(double x, double y, double z, Operation<BlockPos> original) {
        ShulkerBullet self = (ShulkerBullet)(Object)this;
        Level level = self.level();
        return original.call(x, LoopingRegistry.isLooping(level) ? SeamPositions.foldForDistance(level, y, self.getY()) : y, z);
    }
}
