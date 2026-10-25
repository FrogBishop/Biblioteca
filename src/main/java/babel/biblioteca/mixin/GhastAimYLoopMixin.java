package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$GhastShootFireballGoal")
public abstract class GhastAimYLoopMixin {
    @Shadow @Final private Ghast ghast;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY(D)D"))
    private double biblioteca$nearFireballTarget(LivingEntity target, double fraction, Operation<Double> original) {
        double y = original.call(target, fraction);
        return LoopingRegistry.isLooping(Compat.level(ghast))
                ? SeamPositions.foldForDistance(Compat.level(ghast), y, ghast.getY(0.5) + 0.5) : y;
    }
}
