package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets pistons move blocks across the seam of a Y-looping dimension.
 *
 * Two separate pieces of vanilla block code assume the world has an edge:
 *
 * 1. PistonBaseBlock.isPushable refuses any row outside [minY, maxY], and refuses to
 *    push down from the bottom row / up from the top row. In a ring none of those rows
 *    is special, so a piston at the bottom row simply would not push anything, which is
 *    why slime blocks could never be pushed across the border. The seam cases are
 *    answered by asking the same question about the same block one period away, where
 *    those guards do not fire; the method only inspects the pushed block itself, so the
 *    answer is identical.
 *
 * 2. moveBlocks places the moving-piston placeholder at the *target* position, which is
 *    out of range at the seam. Level.setBlock folds that position, but the block entity
 *    built by MovingPistonBlock.newMovingBlockEntity carries the raw one, and
 *    Level.setBlockEntity drops it without a word because the row is outside the world.
 *    The result is a Moving Piston block at the folded row with no block entity, so the
 *    move never completes and the pushed block silently disappears. Folding the position
 *    handed to the factory keeps the block and its block entity in the same row.
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockYLoopMixin {

    private static BlockPos biblioteca$wrap(BlockPos pos, Level level) {
        int y      = pos.getY();
        int minY   = level.getMinY();
        int height = level.getHeight();
        if (y >= minY && y < minY + height) return pos;
        return new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void biblioteca$allowSeamRows(
            BlockState state, Level level, BlockPos pos, Direction direction,
            boolean allowDestroyable, Direction connectionDirection,
            CallbackInfoReturnable<Boolean> cir) {

        if (!LoopingRegistry.isLooping(level)) return;

        int minY   = level.getMinY();
        int height = level.getHeight();
        int maxY   = level.getMaxY();
        int y      = pos.getY();

        if (y < minY || y > maxY) {
            cir.setReturnValue(PistonBaseBlock.isPushable(
                    state, level, biblioteca$wrap(pos, level), direction, allowDestroyable, connectionDirection));
            return;
        }

        // Bottom row pushed down ends up in the top row, and the other way round.
        if (direction == Direction.DOWN && y == minY) {
            cir.setReturnValue(PistonBaseBlock.isPushable(
                    state, level, pos.above(height - 1), direction, allowDestroyable, connectionDirection));
        } else if (direction == Direction.UP && y == maxY) {
            cir.setReturnValue(PistonBaseBlock.isPushable(
                    state, level, pos.below(height - 1), direction, allowDestroyable, connectionDirection));
        }
    }

    @WrapOperation(
            method = "moveBlocks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/piston/MovingPistonBlock;"
                            + "newMovingBlockEntity(Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/core/Direction;ZZ)"
                            + "Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private static BlockEntity biblioteca$foldMovingBlockEntity(
            BlockPos position, BlockState blockState, BlockState movedState, Direction direction,
            boolean extending, boolean isSourcePiston, Operation<BlockEntity> original,
            @Local(argsOnly = true) Level level) {

        BlockPos target = position;
        if (LoopingRegistry.isLooping(level)) {
            target = biblioteca$wrap(position, level);
        }
        return original.call(target, blockState, movedState, direction, extending, isSourcePiston);
    }
}
