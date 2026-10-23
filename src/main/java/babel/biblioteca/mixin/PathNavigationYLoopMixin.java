package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

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
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.LinkedHashSet;
import java.util.Set;

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
 *
 * <p>The destination is moved the same way before the search starts. A path target is named by
 * {@code entity.blockPosition()}, which is always written in the stored period: a player standing
 * just across the seam is reported a whole world height below or above the mob. The search area
 * is a box around the mob, so a target outside it is never reached and the search returns the
 * best node it found, usually the mob's own row. Folding each target towards the mob puts the
 * destination back beside it, where the region and the node evaluator can reach it.
 */
@Mixin(PathNavigation.class)
public abstract class PathNavigationYLoopMixin {

    @Shadow @Final protected Mob mob;

    @WrapOperation(
            method = "followThePath",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/pathfinder/Path;getNextNodePos()"
                            + "Lnet/minecraft/core/BlockPos;"))
    private BlockPos biblioteca$foldFollowedNode(Path path, Operation<BlockPos> original) {
        return biblioteca$foldNode(original.call(path));
    }

    @WrapOperation(
            method = "doStuckDetection",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/pathfinder/Path;getNextNodePos()"
                            + "Lnet/minecraft/core/BlockPos;"))
    private BlockPos biblioteca$foldTimedNode(Path path, Operation<BlockPos> original) {
        return biblioteca$foldNode(original.call(path));
    }

    @ModifyVariable(
//? if >=1.17 {
            method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
//?} else {
/*            method = "createPath(Ljava/util/Set;IZI)Lnet/minecraft/world/level/pathfinder/Path;",
*///?}

            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Set<BlockPos> biblioteca$foldTargets(Set<BlockPos> targets) {
        Level level = Compat.level(this.mob);
        if (targets == null || targets.isEmpty()) return targets;
        if (!LoopingRegistry.isLooping(level)) return targets;

        int period = Compat.height(level);
        if (period <= 0) return targets;

        double mobY = this.mob.getY();
        boolean folded = false;
        for (BlockPos target : targets) {
            if (SeamPositions.layer(target.getY(), mobY, period) != 0L) {
                folded = true;
                break;
            }
        }

        if (!folded) return targets;

        Set<BlockPos> result = new LinkedHashSet<>(targets.size() * 2);
        for (BlockPos target : targets) {
            long layers = SeamPositions.layer(target.getY(), mobY, period);
            result.add(layers == 0L ? target : target.offset(0, (int) (layers * period), 0));
        }

        return result;
    }

    private BlockPos biblioteca$foldNode(BlockPos node) {
        Level level = Compat.level(this.mob);
        if (node == null || !LoopingRegistry.isLooping(level)) return node;

        int period = Compat.height(level);
        if (period <= 0) return node;

        int y = (int) SeamPositions.foldToward(node.getY(), this.mob.getY(), period);
        return y == node.getY() ? node : new BlockPos(node.getX(), y, node.getZ());
    }
}
