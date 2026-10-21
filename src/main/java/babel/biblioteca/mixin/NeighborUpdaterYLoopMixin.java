package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
//? if >=1.21.2 {
import net.minecraft.world.level.redstone.Orientation;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Delivers neighbour updates at the row they belong to when the notified block is past a world
 * edge, so that a block on one side of the seam hears about changes on the other.
 *
 * Every per-block dispatch in this version funnels through NeighborUpdater.executeUpdate, whichever
 * updater queued it, so folding the position here fixes the whole family: pistons, redstone wire,
 * observers, rails, doors, anything that reacts to a neighbour.
 *
 * 1.21.2 replaced the source BlockPos with an Orientation; the older signature carries both the
 * changed block and its position.
 */
@Mixin(NeighborUpdater.class)
public interface NeighborUpdaterYLoopMixin {

    @Inject(method = "executeUpdate", at = @At("HEAD"), cancellable = true)
    private static void biblioteca$foldUpdatedPosition(
            Level level, BlockState state, BlockPos pos, Block changedBlock,
            //? if >=1.21.2 {
            Orientation orientation,
            //?} else {
            /*BlockPos sourcePos,
            *///?}
            boolean movedByPiston, CallbackInfo ci) {

        if (!LoopingRegistry.isLooping(level)) return;

        int minY   = level.getMinY();
        int height = level.getHeight();
        int y      = pos.getY();

        // Fast path: updates inside the world are by far the common case.
        if (y >= minY && y < minY + height) return;

        // The state was looked up through a folded getBlockState, so it is already the state of
        // the row this position folds to; only the position has to be brought back in range.
        BlockPos folded = new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
        //? if >=1.21.2 {
        NeighborUpdater.executeUpdate(level, state, folded, changedBlock, orientation, movedByPiston);
        //?} else {
        /*NeighborUpdater.executeUpdate(level, state, folded, changedBlock, sourcePos, movedByPiston);
        *///?}
        ci.cancel();
    }
}
