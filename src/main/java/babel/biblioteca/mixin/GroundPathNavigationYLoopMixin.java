package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Finds the walking surface for a destination that was named on the far side of the seam.
 *
 * <p>Ground navigation turns a destination into the nearest standable row before pathfinding. That
 * search reads the destination's chunk directly, which answers air for rows outside the world and
 * walks a row past the top row all the way down to the bottom before giving up. Folding the
 * destination first makes the search run in the stored period, where the chunk has the terrain the
 * ring is made of.
 */
@Mixin(GroundPathNavigation.class)
public abstract class GroundPathNavigationYLoopMixin {

    @ModifyVariable(
            method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldDestination(BlockPos pos) {
        Level level = ((PathNavigationAccessor) this).biblioteca$level();
        if (level == null || !LoopingRegistry.isLooping(level)) return pos;

        return SeamPositions.foldBlockPos(level, pos);
    }
}