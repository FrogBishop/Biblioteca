package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.Blaze$BlazeAttackGoal")
public abstract class BlazeAimYLoopMixin {
    @Shadow @Final private Blaze blaze;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY(D)D"))
    private double biblioteca$nearFireballTarget(LivingEntity target, double fraction, Operation<Double> original) {
        double y = original.call(target, fraction);
        return LoopingRegistry.isLooping(Compat.level(blaze))
                ? SeamPositions.foldForDistance(Compat.level(blaze), y, blaze.getY(0.5)) : y;
    }
}
