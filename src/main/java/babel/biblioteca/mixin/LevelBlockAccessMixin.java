package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Wraps BlockPos Y coordinates in Level.getBlockState, Level.setBlock, and
 * Level.removeBlock so that positions outside [minY, minY+height) fold back
 * into the valid build range on both read and write.
 *
 * Works on both ServerLevel and ClientLevel since both extend Level.
 */
@Mixin(Level.class)
public abstract class LevelBlockAccessMixin {

    private static BlockPos biblioteca$wrap(BlockPos pos, Level level) {
        int y      = pos.getY();
        int minY   = level.getMinY();
        int height = level.getHeight();
        if (y >= minY && y < minY + height) return pos;
        return new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
    }

    @ModifyVariable(method = "getBlockState", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapGetBlockState(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }

    // MC 26.3: Level.setBlock has only the 4-argument form (BlockPos, BlockState, int, int)
    @ModifyVariable(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private BlockPos biblioteca$wrapSetBlock(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }

    // MC 26.3: Level.removeBlock(BlockPos, boolean)
    @ModifyVariable(
            method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private BlockPos biblioteca$wrapRemoveBlock(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }
}
