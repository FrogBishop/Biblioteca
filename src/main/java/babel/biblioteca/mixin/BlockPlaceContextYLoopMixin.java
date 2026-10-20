package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes the row just outside the world top / bottom placeable in a Y-looping dimension.
 *
 * Level.getBlockState folds an out-of-range position to its periodic twin, so when a player
 * standing at the top of the world clicks the top face of the highest block, canPlace() asks for
 * the state at y = maxY + 1, receives the block at minY (bedrock in a normal world) and refuses
 * the placement. The block would have been folded to minY by Level.setBlock, which is exactly the
 * ring's next row; only this check stands in the way.
 *
 * A real out-of-range position can only be one block past an edge here: the relative position is
 * derived from a hit on a real block. Reporting air for it lets BlockItem.place continue, and the
 * subsequent setBlock folds the position onto the ring. Every other placement check keeps using
 * the folded world, so normal building is untouched.
 */
@Mixin(BlockPlaceContext.class)
public abstract class BlockPlaceContextYLoopMixin {

    @WrapOperation(
            method = "canPlace",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;"
                            + "getBlockState(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState biblioteca$openSeamRow(
            Level level, BlockPos pos, Operation<BlockState> original) {

        if (LoopingRegistry.isLooping(level) && level.isOutsideBuildHeight(pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return original.call(level, pos);
    }
}
