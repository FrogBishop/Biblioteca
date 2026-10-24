package babel.biblioteca.mixin;

import babel.biblioteca.LoopingLevel;
import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Evaluates ground below the stored bottom row while keeping downward column scans finite. */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorYLoopMixin extends NodeEvaluator {
    @WrapOperation(method = "getPathTypeStatic(Lnet/minecraft/world/level/pathfinder/PathfindingContext;"
            + "Lnet/minecraft/core/BlockPos$MutableBlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/CollisionGetter;getMinY()I"))
    private static int biblioteca$allowGroundBelowSeam(CollisionGetter level, Operation<Integer> original) {
        // This is a single support-block test, not a column scan. Both Level and the navigation
        // snapshot fold the queried block; neither has a void below its stored bottom row.
        return level instanceof LoopingLevel looping && looping.biblioteca$isLooping()
                ? Integer.MIN_VALUE : original.call(level);
    }

    @WrapOperation(method = "getStart", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/CollisionGetter;getMinY()I"))
    private int biblioteca$startSearchFloor(CollisionGetter level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(this.mob.level())
                ? this.mob.getBlockY() - this.mob.level().getHeight() : original.call(level);
    }

    @WrapOperation(method = {"tryFindFirstGroundNodeBelow", "tryFindFirstNonWaterBelow"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinY()I"))
    private int biblioteca$descendingSearchFloor(Level level, Operation<Integer> original) {
        // One adjacent copy below the starting mob is enough to inspect the entire ring. Retain
        // vanilla's max-fall-distance check, and do not let an empty/water column loop forever.
        return LoopingRegistry.isLooping(level)
                ? this.mob.getBlockY() - level.getHeight() : original.call(level);
    }
}
