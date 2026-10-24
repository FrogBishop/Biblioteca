package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.breeze.Shoot;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Shoot.class)
public abstract class BreezeAimYLoopMixin {
    @ModifyVariable(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/breeze/Breeze;J)V",
            at = @At("STORE"), ordinal = 1)
    private double biblioteca$nearWindCharge(double deltaY, @Local(argsOnly = true) ServerLevel level) {
        return LoopingRegistry.isLooping(level) ? SeamPositions.foldForDistance(level, deltaY, 0) : deltaY;
    }

    @WrapOperation(method = "isTargetWithinRange", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double biblioteca$periodicRange(Vec3 from, Vec3 to, Operation<Double> original,
            @Local(argsOnly = true) Breeze breeze) {
        return LoopingRegistry.isLooping(breeze.level()) ? SeamPositions.distanceToSqr(breeze.level(), from, to) : original.call(from, to);
    }
}
