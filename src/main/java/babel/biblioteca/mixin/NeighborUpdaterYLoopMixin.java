package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Delivers neighbour updates at the row they belong to when the notified block is past a world
 * edge, so that a block on one side of the seam hears about changes on the other.
 *
 * The piston case: a piston on the top row is powered by a redstone block on the bottom row, which
 * the ring puts directly above it. Extending works because reading the signal goes through
 * Level.getBlockState, which folds. Retracting does not, because breaking the redstone block sends
 * updates to its *neighbours in space* - the row below the bottom row, which is past the edge - and
 * that notification arrives carrying the out-of-range position. The state lookup that resolves it
 * folds, so the right block state is found, but the position handed to the block is still outside
 * the world: the piston re-checks power around the wrong row and the block event it queues is
 * addressed at a row the chunk does not have, so the retraction is silently dropped. The piston
 * stays extended and its head is stranded.
 *
 * Every per-block dispatch in this version funnels through NeighborUpdater.executeUpdate, whichever
 * updater queued it, so folding the position here fixes the whole family: pistons, redstone wire,
 * observers, rails, doors, anything that reacts to a neighbour.
 */
@Mixin(NeighborUpdater.class)
public interface NeighborUpdaterYLoopMixin {

    @Inject(method = "executeUpdate", at = @At("HEAD"), cancellable = true)
    private static void biblioteca$foldUpdatedPosition(
            Level level, BlockState state, BlockPos pos, Block changedBlock,
            Orientation orientation, boolean movedByPiston, CallbackInfo ci) {

        if (!LoopingRegistry.isLooping(level)) return;

        // Fast path: updates inside the world are by far the common case.
        if (SeamPositions.isInside(level, pos.getY())) return;

        // The state was looked up through a folded getBlockState, so it is already the state of
        // the row this position folds to; only the position has to be brought back in range.
        NeighborUpdater.executeUpdate(
                level, state, SeamPositions.foldBlockPos(level, pos), changedBlock, orientation, movedByPiston);
        ci.cancel();
    }
}
