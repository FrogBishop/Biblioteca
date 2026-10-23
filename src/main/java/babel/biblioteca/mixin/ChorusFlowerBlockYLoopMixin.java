package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import babel.biblioteca.Compat;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A chorus flower stops growing when the block above it is above the world top. In a
 * Y-looping dimension that is the bottom row of the ring, so growth is allowed to
 * continue through the seam.
 */
@Mixin(ChorusFlowerBlock.class)
public abstract class ChorusFlowerBlockYLoopMixin {

//? if >=1.17 {
    @WrapOperation(
            method = "randomTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMaxY()I"))
    private int biblioteca$noCeiling(ServerLevel level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MAX_VALUE : original.call(level);
    }

//?} else {
/*    @ModifyConstant(method = "randomTick", constant = @Constant(intValue = 256))
    private int biblioteca$legacyCeiling(int bound, @Local(argsOnly = true) ServerLevel level) {
        return LoopingRegistry.isLooping(level) ? Integer.MAX_VALUE : bound;
    }

*///?}
}
