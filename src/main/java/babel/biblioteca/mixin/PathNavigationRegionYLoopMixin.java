package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets the pathfinder see across the seam.
 *
 * <p>Pathfinding never reads the level directly. It reads a {@code PathNavigationRegion}: a
 * snapshot of the chunks around the mob that answers AIR for every row outside the build height,
 * without ever reaching {@code Level.getBlockState} and therefore without the folding that makes
 * the level periodic. The row above the world top is exactly where the ring continues below the
 * world bottom, so to the pathfinder the ring ends at a wall of air: no walkable node, no path.
 *
 * <p>Folding the queried row before the region's own bounds check keeps the read inside the
 * snapshot - the folded row is a real row of the same chunk column - so no chunk is loaded here and
 * the snapshot still decides what the pathfinder sees.
 */
@Mixin(PathNavigationRegion.class)
public abstract class PathNavigationRegionYLoopMixin {

    @Shadow @Final protected Level level;

    @ModifyVariable(method = "getBlockState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldBlockStateQuery(BlockPos pos) {
        return biblioteca$fold(pos);
    }

    @ModifyVariable(method = "getFluidState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldFluidStateQuery(BlockPos pos) {
        return biblioteca$fold(pos);
    }

    private BlockPos biblioteca$fold(BlockPos pos) {
        if (pos == null || !LoopingRegistry.isLooping(this.level)) return pos;

        // Always folded rather than "folded when outside": a ring has no row that is outside it,
        // and foldBlockPos returns the same instance for a row it does not have to move.
        return SeamPositions.foldBlockPos(this.level, pos);
    }
}
