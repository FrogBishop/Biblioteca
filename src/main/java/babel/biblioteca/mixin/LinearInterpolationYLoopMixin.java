package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.AbstractInterpolationHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps the remaining linear target nearby after an intermediate tick commits a seam crossing. */
@Mixin(LinearInterpolationHandler.class)
public abstract class LinearInterpolationYLoopMixin extends AbstractInterpolationHandler {
    protected LinearInterpolationYLoopMixin(Entity entity, int steps) { super(entity, steps); }

    @WrapOperation(method = "doInterpolate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;lerp(Lnet/minecraft/world/phys/Vec3;D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$nearTarget(Vec3 current, Vec3 target, double alpha, Operation<Vec3> original) {
        return original.call(current, LoopingRegistry.isLooping(this.entity.level())
                ? SeamPositions.foldToward(this.entity.level(), target, current.y) : target, alpha);
    }
}
