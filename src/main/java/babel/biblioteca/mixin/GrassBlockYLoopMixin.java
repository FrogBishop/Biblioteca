package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.GrassBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bone meal on grass skips the row above when it is outside the build height. In a
 * Y-looping dimension the row above the top row is the bottom row of the ring, so the
 * effect (grass spread and flowers) is allowed to appear there.
 */
@Mixin(GrassBlock.class)
public abstract class GrassBlockYLoopMixin {

    @WrapOperation(
            method = "placeBonemealEffect",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;isOutsideBuildHeight(Lnet/minecraft/core/BlockPos;)Z"),
            require = 1)
    private static boolean biblioteca$growPastTheTop(ServerLevel level, BlockPos pos, Operation<Boolean> original) {
        return LoopingRegistry.isLooping(level) ? false : original.call(level, pos);
    }
}
