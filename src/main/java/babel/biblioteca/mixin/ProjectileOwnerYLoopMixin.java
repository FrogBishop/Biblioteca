package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Retains the native owner/passenger and pickability filters, comparing their nearby hitboxes. */
@Mixin(Projectile.class)
public abstract class ProjectileOwnerYLoopMixin {
    @WrapOperation(method = "isOutsideOwnerCollisionRange", at = @At(value = "INVOKE",
            target = "Ljava/util/stream/Stream;noneMatch(Ljava/util/function/Predicate;)Z", remap = false))
    private boolean biblioteca$nearOwner(Stream<Entity> candidates, Predicate<Entity> predicate,
            Operation<Boolean> original, @Local AABB projectileArea) {
        var level = Compat.level((Entity)(Object)this);
        if (!LoopingRegistry.isLooping(level)) return original.call(candidates, predicate);
        return original.call(candidates, (Predicate<Entity>) entity -> Compat.level(entity) == level
                ? projectileArea.intersects(SeamPositions.foldToward(level, entity.getBoundingBox(), projectileArea.getCenter().y))
                : predicate.test(entity));
    }
}
