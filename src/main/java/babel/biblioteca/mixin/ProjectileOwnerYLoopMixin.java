package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Crossing the coordinate seam at spawn does not mean a projectile has left its owner. */
@Mixin(Projectile.class)
public abstract class ProjectileOwnerYLoopMixin {
    @WrapOperation(method = "lambda$isOutsideOwnerCollisionRange$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB biblioteca$nearOwner(Entity entity, Operation<AABB> original,
            @Local(argsOnly = true) AABB projectileArea) {
        AABB box = original.call(entity);
        return LoopingRegistry.isLooping(entity.level())
                ? SeamPositions.foldToward(entity.level(), box, projectileArea.getCenter().y) : box;
    }
}
