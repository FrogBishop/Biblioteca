package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
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
 * 2. A block at either boundary is a neighbour of the opposite edge's mesh.
 *    Mark that real section dirty so its face culling, fluid shape and lighting
 *    are rebuilt. Periodic draws share these source meshes.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelDirtyMixin {

    //? if >=1.19 {
    @ModifyVariable(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldSetBlockPos(BlockPos pos) {
        ClientLevel self = (ClientLevel) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        int y = pos.getY();
        int minY = Compat.minY(self);
        int height = Compat.height(self);
        if (y >= minY && y < minY + height) return pos;
        return new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
    }

    //?}

    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void biblioteca$mirrorSectionDirty(
            BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {

        ClientLevel self = (ClientLevel) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return;

        int height   = Compat.height(self);
        int y        = pos.getY();
        int sectionX = pos.getX() >> 4;
        int sectionZ = pos.getZ() >> 4;

        int minSection = Compat.minY(self) >> 4;
        int maxSection = minSection + (Compat.height(self) >> 4); // exclusive upper bound
        if (y == Compat.minY(self)) {
            self.setSectionDirtyWithNeighbors(sectionX, maxSection - 1, sectionZ);
        }
        if (y == Compat.minY(self) + height - 1) {
            self.setSectionDirtyWithNeighbors(sectionX, minSection, sectionZ);
        }
    }
}
