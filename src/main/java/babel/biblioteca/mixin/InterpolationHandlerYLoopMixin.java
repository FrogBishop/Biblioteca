package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** 1.21.5--26.2 keeps one interpolation target across several committed (folded) positions. */
@Mixin(InterpolationHandler.class)
public abstract class InterpolationHandlerYLoopMixin {
    @Shadow @Final private Entity entity;

    @ModifyExpressionValue(method = "interpolate", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/InterpolationHandler$InterpolationData;position:Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$nearTarget(Vec3 target) {
        return LoopingRegistry.isLooping(Compat.level(this.entity))
                ? SeamPositions.foldToward(Compat.level(this.entity), target, this.entity.getY()) : target;
    }

    @WrapOperation(method = "interpolate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$ringPredictedDelta(Vec3 current, Vec3 previous, Operation<Vec3> original) {
        return original.call(current, LoopingRegistry.isLooping(Compat.level(this.entity))
                ? SeamPositions.foldToward(Compat.level(this.entity), previous, current.y) : previous);
    }
}
