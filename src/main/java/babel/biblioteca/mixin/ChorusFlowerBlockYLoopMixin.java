package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A chorus flower stops growing when the block above it is above the world top. In a
 * Y-looping dimension that is the bottom row of the ring, so growth is allowed to
 * continue through the seam.
 */
@Mixin(ChorusFlowerBlock.class)
public abstract class ChorusFlowerBlockYLoopMixin {

    @WrapOperation(
            method = "randomTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMaxY()I"),
            require = 1)
    private int biblioteca$noCeiling(ServerLevel level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MAX_VALUE : original.call(level);
    }
}
