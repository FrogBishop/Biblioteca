package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Makes the whole Level accessor family treat the Y axis as periodic.
 *
 * Every out-of-range lookup in Level funnels through one guard, isInValidBounds(),
 * which answers "outside the world" for rows above minY + height and below minY.
 * Folding the BlockPos before that guard is what lets the seam behave like the ring
 * it is supposed to be, and it has to be done per accessor: a block that asks for
 * something other than a BlockState would still see the void.
 *
 * Blocks with bespoke rules reach for these accessors directly, which is why a
 * partially covered family shows up as unrelated sounding bugs:
 *
 *   getBlockState / setBlock / removeBlock   pistons, redstone, chests, rails, ...
 *   getFluidState                            waterlogged blocks, fluid mixing, swimming
 *   getBlockEntity / removeBlockEntity       pistons moving block entities, hoppers,
 *                                            anything that must not orphan a block entity
 *   isLoaded                                 neighbour updates (an update addressed at a
 *                                            row that reads as unloaded is dropped)
 *   loadedAndEntityCanStandOnFace            entity support checks
 *
 * setBlockEntity(BlockEntity) is deliberately absent: it derives its position from the
 * block entity, so there is no argument to fold. Block entities created through
 * setBlock are placed at the folded position by the chunk itself.
 *
 * Every hook below is meant to be mandatory. The filtered builds relax
 * defaultRequire to 0 (26.3 included) so a partially ported node still boots,
 * which also means a hook that loses its target fails silently: check the
 * transformed class instead of trusting a clean startup.
 */
@Mixin(Level.class)
public abstract class LevelBlockAccessMixin {

    private static BlockPos biblioteca$wrap(BlockPos pos, Level level) {
        int y      = pos.getY();
        int minY   = level.getMinY();
        int height = level.getHeight();
        if (y >= minY && y < minY + height) return pos;
        return new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
    }

    @ModifyVariable(method = "getBlockState", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapGetBlockState(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }

    // MC 26.3: Level.setBlock has only the 4-argument form (BlockPos, BlockState, int, int)
    @ModifyVariable(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$wrapSetBlock(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }

    // MC 26.3: Level.removeBlock(BlockPos, boolean)
    @ModifyVariable(
            method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$wrapRemoveBlock(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }

    @ModifyVariable(method = "getFluidState", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapGetFluidState(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }

    @ModifyVariable(method = "getBlockEntity", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapGetBlockEntity(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }

    @ModifyVariable(method = "removeBlockEntity", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapRemoveBlockEntity(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }

    @ModifyVariable(method = "isLoaded", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrapIsLoaded(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }

    @ModifyVariable(method = "loadedAndEntityCanStandOnFace", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$wrapLoadedAndEntityCanStandOnFace(BlockPos pos) {
        Level self = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(self)) return pos;
        return biblioteca$wrap(pos, self);
    }
}
