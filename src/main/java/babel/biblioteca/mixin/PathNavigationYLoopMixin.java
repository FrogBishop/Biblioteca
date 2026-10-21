package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes the path follower compare the mob with the node beside it, not with a row 384 blocks away.
 *
 * <p>{@code followThePath} reads the next node's absolute row and compares it with the mob's row.
 * Both are correct on their own, but after a seam crossing they are the same ring row expressed in
 * two different periods, and the difference is a whole world height. The follower then never
 * considers itself close enough to advance, and the stuck timeout eventually cancels the path.
 *
 * <p>Only the node row is moved here, onto the copy nearest the mob. The horizontal comparison -
 * which is what actually decides whether the mob reached the node - is unchanged.
 */
@Mixin(PathNavigation.class)
public abstract class PathNavigationYLoopMixin {

    @Shadow @Final protected Mob mob;

    @WrapOperation(
            method = "followThePath",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/pathfinder/Path;getNextNodePos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos biblioteca$foldFollowedNode(Path path, Operation<BlockPos> original) {
        return biblioteca$foldNode(original.call(path));
    }

    @WrapOperation(
            method = "doStuckDetection",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/pathfinder/Path;getNextNodePos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos biblioteca$foldTimedNode(Path path, Operation<BlockPos> original) {
        return biblioteca$foldNode(original.call(path));
    }

    private BlockPos biblioteca$foldNode(BlockPos node) {
        Level level = this.mob.level();
        if (node == null || !LoopingRegistry.isLooping(level)) return node;

        int period = level.getHeight();
        if (period <= 0) return node;

        int y = (int) SeamPositions.foldToward(node.getY(), this.mob.getY(), period);
        return y == node.getY() ? node : new BlockPos(node.getX(), y, node.getZ());
    }
}