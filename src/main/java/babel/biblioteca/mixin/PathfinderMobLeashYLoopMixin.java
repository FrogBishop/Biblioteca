package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The slack leash's walking direction must agree with its periodic length. */
@Mixin(PathfinderMob.class)
public abstract class PathfinderMobLeashYLoopMixin {
    @WrapOperation(method = "closeRangeLeashBehaviour", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getY()D"))
    private double biblioteca$nearLeashHolder(Entity holder, Operation<Double> original) {
        Entity self = (Entity) (Object) this;
        double y = original.call(holder);
        return self.level() == holder.level() && LoopingRegistry.isLooping(self.level())
                ? SeamPositions.foldToward(y, self.getY(), self.level().getHeight()) : y;
    }
}
