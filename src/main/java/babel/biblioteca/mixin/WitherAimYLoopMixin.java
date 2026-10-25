package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WitherBoss.class)
public abstract class WitherAimYLoopMixin {
    @WrapOperation(method = "performRangedAttack(IDDDZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;getHeadY(I)D"))
    private double biblioteca$nearSkullOrigin(WitherBoss wither, int head, Operation<Double> original,
            @Local(argsOnly = true, ordinal = 1) double targetY) {
        double y = original.call(wither, head);
        return LoopingRegistry.isLooping(Compat.level(wither)) ? SeamPositions.foldForDistance(Compat.level(wither), y, targetY) : y;
    }
}
