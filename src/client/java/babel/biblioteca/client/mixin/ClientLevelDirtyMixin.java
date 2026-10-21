package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps client-side block prediction and section invalidation aware of the seam.
 *
 * <p>1. {@code ClientLevel.setBlock} is folded at HEAD so that
 * BlockStatePredictionHandler.retainKnownServerState records the folded position (for example
 * y = -64) rather than the out-of-bounds one (y = 320). Otherwise the server's confirmation packet
 * (also at y = -64) does not match the pending prediction keyed to y = 320, the prediction times
 * out, and the placed block is rolled back visually.
 *
 * <p>2. A section at either end of the world is not compiled on its own: face culling reads one row
 * past its edge, and in a ring that row belongs to the section at the opposite end. Breaking a
 * block on the bottom row can therefore reveal a face in the top section, and the other way round.
 * Vanilla only dirties the neighbours inside the build height, so the section on the far side of
 * the seam keeps its old mesh and the newly exposed face stays invisible until something else
 * happens to rebuild it. The opposite end's section is dirtied explicitly here, using the block's
 * own column.
 *
 * <p>The previous version computed the opposite row as {@code y +/- height}, which lands outside the
 * world and is discarded by {@code SectionUpdateTracker}, so the dependency was never invalidated.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelDirtyMixin {

    @ModifyVariable(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldSetBlockPos(BlockPos pos) {
        ClientLevel self = (ClientLevel) (Object) this;
        return LoopingRegistry.isLooping(self) ? SeamPositions.foldBlockPos(self, pos) : pos;
    }

    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void biblioteca$mirrorSeamSectionDirty(
            BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {

        ClientLevel self = (ClientLevel) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return;

        int y = pos.getY();
        int minY = self.getMinY();
        int maxY = self.getMaxY();

        // Only the two rows that touch a seam have a face compiled against the other end.
        int oppositeSectionY;
        if (y == minY) {
            oppositeSectionY = self.getMaxSectionY();
        } else if (y == maxY) {
            oppositeSectionY = self.getMinSectionY();
        } else {
            return;
        }

        self.setSectionDirtyWithNeighbors(
                SectionPos.blockToSectionCoord(pos.getX()),
                oppositeSectionY,
                SectionPos.blockToSectionCoord(pos.getZ()));
    }
}