package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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
 * <p>2. After {@code setBlocksDirty} notifies LevelRenderer about the real block change, the mirror
 * sections one world height away are marked dirty as well, so the looping renderer rebuilds the
 * copies. Only indices inside the level's section range are dirtied: an out-of-range index would
 * throw inside vanilla's section array lookup and stop the renderer from even hearing about the
 * original change.
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
    private void biblioteca$mirrorSectionDirty(
            BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {

        ClientLevel self = (ClientLevel) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return;

        int height   = self.getHeight();
        int y        = pos.getY();
        int sectionX = pos.getX() >> 4;
        int sectionZ = pos.getZ() >> 4;

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
