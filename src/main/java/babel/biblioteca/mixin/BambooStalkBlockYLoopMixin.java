package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BambooStalkBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bone meal growth of bamboo stops when the new stalk would leave the world
 * (isOutsideBuildHeight on the growth position). In a Y-looping dimension that row is
 * the bottom of the ring, so the growth is allowed and the folded setBlock lands the
 * new stalk there.
 */
@Mixin(BambooStalkBlock.class)
public abstract class BambooStalkBlockYLoopMixin {

    @WrapOperation(
            method = "performBonemeal",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;isOutsideBuildHeight(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean biblioteca$growPastTheTop(ServerLevel level, BlockPos pos, Operation<Boolean> original) {
        return LoopingRegistry.isLooping(level) ? false : original.call(level, pos);
    }
}
