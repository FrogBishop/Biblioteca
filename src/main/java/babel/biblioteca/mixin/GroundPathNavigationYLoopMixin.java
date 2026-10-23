package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Finds the walking surface for a destination that was named on the far side of the seam.
 *
 * <p>Ground navigation turns a destination into the nearest standable row before pathfinding. That
 * search reads the destination's chunk directly, which answers air for rows outside the world; a
 * destination just past the top row therefore falls off the chunk, scans the whole column below
 * and returns the first air row of that column, a whole world away from the ring row it was meant
 * to reach.
 *
 * <p>Folding the destination into the stored period fixes the window, but the search still has to
 * walk in the ring: rows past the bottom continue at the top, so its walking loops are bounded by
 * one world height and every read goes through {@code Level.getBlockState}, which folds. The
 * result is returned as the copy nearest the mob by the path target fold in
 * {@code PathNavigationYLoopMixin}.
 */
@Mixin(GroundPathNavigation.class)
public abstract class GroundPathNavigationYLoopMixin {

    @ModifyVariable(
            method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldDestination(BlockPos pos) {
        Level level = ((PathNavigationAccessor) this).biblioteca$level();
        if (level == null || !LoopingRegistry.isLooping(level)) return pos;

        return SeamPositions.foldBlockPos(level, pos);
    }

    /**
     * {@code findSurfacePosition} over the ring: at most one world height is walked, and every row
     * is read through the level so a row past the bottom continues at the top.
     */
    @Inject(
            method = "findSurfacePosition(Lnet/minecraft/world/level/chunk/LevelChunk;"
                    + "Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/core/BlockPos;",
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$ringSurfacePosition(
            LevelChunk chunk, BlockPos pos, int reachRange, CallbackInfoReturnable<BlockPos> cir) {

        Level level = ((PathNavigationAccessor) this).biblioteca$level();
        if (level == null || !LoopingRegistry.isLooping(level)) return;

        int period = level.getHeight();
        if (period <= 0) return;

        cir.setReturnValue(biblioteca$findSurfacePosition(level, pos, period));
    }

    private static BlockPos biblioteca$findSurfacePosition(Level level, BlockPos pos, int period) {
        if (!level.getBlockState(pos).isAir()) {
            return biblioteca$standingRow(level, pos, period);
        }

        BlockPos.MutableBlockPos columnPos = pos.mutable();
        for (int step = 0; step < period; step++) {
            columnPos.move(Direction.DOWN);
            if (!level.getBlockState(columnPos).isAir()) {
                return columnPos.above().immutable();
            }
        }

        // The whole ring column is open: look up for a row that is not air, as vanilla does.
        columnPos.set(pos.getX(), pos.getY() + 1, pos.getZ());
        for (int step = 0; step < period && level.getBlockState(columnPos).isAir(); step++) {
            columnPos.move(Direction.UP);
        }

        return biblioteca$standingRow(level, columnPos.immutable(), period);
    }

    private static BlockPos biblioteca$standingRow(Level level, BlockPos pos, int period) {
        if (!level.getBlockState(pos).isSolid()) return pos;

        BlockPos.MutableBlockPos columnPos = pos.mutable().move(Direction.UP);
        for (int step = 0; step < period && level.getBlockState(columnPos).isSolid(); step++) {
            columnPos.move(Direction.UP);
        }

        return columnPos.immutable();
    }
}
