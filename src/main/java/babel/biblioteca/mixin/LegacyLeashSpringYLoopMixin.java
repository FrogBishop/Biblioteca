package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Leashable.class)
public interface LegacyLeashSpringYLoopMixin {
    @WrapOperation(method = "legacyElasticRangeLeashBehaviour", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D"), expect = 2)
    private static double biblioteca$nearHolder(Entity queried, Operation<Double> original, @Local(argsOnly = true, ordinal = 0) Entity attached) {
        double y = original.call(queried);
        return queried != attached && LoopingRegistry.isLooping(Compat.level(attached))
                ? SeamPositions.foldForDistance(Compat.level(attached), y, attached.getY()) : y;
    }
}
