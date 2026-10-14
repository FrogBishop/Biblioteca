package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two responsibilities:
 *
 * 1. Fold the BlockPos in ClientLevel.setBlock at HEAD so that
 *    BlockStatePredictionHandler.retainKnownServerState records the folded
 *    position (e.g. y=-64) rather than the out-of-bounds one (e.g. y=320).
 *    Without this, the server's confirmation packet (also at y=-64) does not
 *    match the pending prediction (keyed to y=320), the prediction times out,
 *    and the placed block is rolled back visually.
 *
 * 2. After setBlocksDirty notifies LevelRenderer about the real block change,
 *    also mark the mirror chunk sections (±height) dirty so the looping
 *    renderer can rebuild them too.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelDirtyMixin {

    @ModifyVariable(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldSetBlockPos(BlockPos pos) {
        ClientLevel self = (ClientLevel) (Object) this;
        if (!LoopingDimensionCache.isLooping(self.dimension())) return pos;
        int y = pos.getY();
        int minY = self.getMinY();
        int height = self.getHeight();
        if (y >= minY && y < minY + height) return pos;
        return new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
    }

    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void biblioteca$mirrorSectionDirty(
            BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {

        ClientLevel self = (ClientLevel) (Object) this;
        if (!LoopingDimensionCache.isLooping(self.dimension())) return;

        int height   = self.getHeight();
        int y        = pos.getY();
        int sectionX = pos.getX() >> 4;
        int sectionZ = pos.getZ() >> 4;

        // Mark mirror sections dirty, but only when the computed section index
        // is within the level's actual section range.  Out-of-bounds indices
        // throw ArrayIndexOutOfBoundsException inside vanilla's section array
        // lookup, which would propagate through setBlocksDirty and prevent the
        // renderer from being notified even for the original block change.
        // Derive section range from the methods we know exist in MC 26.3.
        int minSection = self.getMinY() >> 4;
        int maxSection = minSection + (self.getHeight() >> 4); // exclusive upper bound

        int upSection   = (y + height) >> 4;
        int downSection = (y - height) >> 4;

        if (upSection >= minSection && upSection < maxSection) {
            self.setSectionDirtyWithNeighbors(sectionX, upSection, sectionZ);
        }
        if (downSection >= minSection && downSection < maxSection) {
            self.setSectionDirtyWithNeighbors(sectionX, downSection, sectionZ);
        }
    }
}
