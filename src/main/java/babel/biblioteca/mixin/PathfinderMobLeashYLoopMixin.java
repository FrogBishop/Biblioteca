package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

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
    @WrapOperation(
            //? if >=1.21 {
            method = "closeRangeLeashBehaviour",
            //?} else {
            /*method = "tickLeash", expect = 2,
            *///?}
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getY()D"))
    private double biblioteca$nearLeashHolder(Entity holder, Operation<Double> original) {
        Entity self = (Entity) (Object) this;
        double y = original.call(holder);
        return Compat.level(self) == Compat.level(holder) && LoopingRegistry.isLooping(Compat.level(self))
                ? SeamPositions.foldToward(y, self.getY(), Compat.height(Compat.level(self))) : y;
    }

    //? if <1.21 {
    /*@WrapOperation(method = "tickLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private net.minecraft.core.BlockPos biblioteca$nearRestriction(Entity holder, Operation<net.minecraft.core.BlockPos> original) {
        var pos = original.call(holder);
        PathfinderMob self = (PathfinderMob)(Object)this;
        if (!LoopingRegistry.isLooping(Compat.level(self))) return pos;
        int height = Compat.height(Compat.level(self));
        return pos.offset(0, (int)(SeamPositions.layer(pos.getY(), self.getY(), height) * height), 0);
    }
    *///?}
}
