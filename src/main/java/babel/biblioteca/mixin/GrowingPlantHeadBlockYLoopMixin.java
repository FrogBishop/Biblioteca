package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The head of a growing plant (such as kelp, twisting vines or cave vines) refuses to
 * extend once the next cell is outside the build height. In a Y-looping dimension that
 * cell is the bottom row of the ring, so the extension is allowed there.
 */
@Mixin(GrowingPlantHeadBlock.class)
public abstract class GrowingPlantHeadBlockYLoopMixin {

    @WrapOperation(
            method = "performBonemeal",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;isOutsideBuildHeight(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean biblioteca$growPastTheTop(ServerLevel level, BlockPos pos, Operation<Boolean> original) {
        return LoopingRegistry.isLooping(level) ? false : original.call(level, pos);
    }
}
