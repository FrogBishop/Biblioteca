package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Makes the whole Level accessor family treat the Y axis as periodic.
 *
 * Level accessors reject rows outside [minY, minY + height). Fold positions before their bounds
 * checks so reads and writes agree about the boundary, including fluid and block-entity access.
 *
 * Blocks with bespoke rules reach for these accessors directly, which is why a partially covered
 * family shows up as unrelated sounding bugs:
 *
 *   getBlockState / setBlock / removeBlock   pistons, redstone, chests, rails, ...
 *   getFluidState                            waterlogged blocks, fluid mixing, swimming
 *   getBlockEntity / removeBlockEntity       pistons moving block entities, hoppers,
 *                                            anything that must not orphan a block entity
 *   isLoaded                                 neighbour updates (an update addressed at a
 *                                            row that reads as unloaded is dropped)
 *   loadedAndEntityCanStandOnFace            entity support checks
 *
 * setBlockEntity(BlockEntity) is deliberately absent: it derives its position from the block
 * entity, so there is no argument to fold. Block entities created through setBlock are placed at
 * the folded position by the chunk itself.
 *
 * Every hook below is mandatory: the mixin config sets defaultRequire to 1 so an unmatched
 * injection fails during loading instead of silently leaving part of the accessor family unpatched.
 */
@Mixin(Level.class)
public abstract class LevelBlockAccessMixin {

    private BlockPos biblioteca$fold(BlockPos pos) {
        Level self = (Level) (Object) this;
        return LoopingRegistry.isLooping(self) ? SeamPositions.foldBlockPos(self, pos) : pos;
    }

    @ModifyVariable(method = "getBlockState", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapGetBlockState(BlockPos pos) {
        return biblioteca$fold(pos);
    }

    // MC 26.3: Level.setBlock has only the 4-argument form (BlockPos, BlockState, int, int)
    @ModifyVariable(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$wrapSetBlock(BlockPos pos) {
        return biblioteca$fold(pos);
    }

    // MC 26.3: Level.removeBlock(BlockPos, boolean)
    @ModifyVariable(
            method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$wrapRemoveBlock(BlockPos pos) {
        return biblioteca$fold(pos);
    }

    @ModifyVariable(method = "getFluidState", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapGetFluidState(BlockPos pos) {
        return biblioteca$fold(pos);
    }

    @ModifyVariable(method = "getBlockEntity", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapGetBlockEntity(BlockPos pos) {
        return biblioteca$fold(pos);
    }

    @ModifyVariable(method = "removeBlockEntity", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapRemoveBlockEntity(BlockPos pos) {
        return biblioteca$fold(pos);
    }

    @ModifyVariable(method = "isLoaded", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapIsLoaded(BlockPos pos) {
        return biblioteca$fold(pos);
    }

    @ModifyVariable(method = "loadedAndEntityCanStandOnFace", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$wrapLoadedAndEntityCanStandOnFace(BlockPos pos) {
        return biblioteca$fold(pos);
    }
}
