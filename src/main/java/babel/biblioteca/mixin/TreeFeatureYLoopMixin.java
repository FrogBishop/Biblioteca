package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Allows a tree to grow through the top of a looping world.
 *
 * <p>Before a tree is placed its height is checked against the build height, and a tree whose crown
 * would pass the world top is rejected outright - which is why a sapling on the top row never grows
 * a trunk even when its growth check passed. In a ring the crown continues in the bottom row, and
 * the block writes are folded there already, so the two edges are answered with "no edge".
 *
 * <p>The bounds are returned one short of the integer limits because vanilla adds one to each
 * before comparing; returning {@code Integer.MAX_VALUE} would overflow that comparison.
 */
@Mixin(TreeFeature.class)
public abstract class TreeFeatureYLoopMixin {

    @WrapOperation(
            method = "doPlace",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;getMinY()I"))
    private int biblioteca$noFloor(WorldGenLevel level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MIN_VALUE + 1 : original.call(level);
    }

    @WrapOperation(
            method = "doPlace",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;getMaxY()I"))
    private int biblioteca$noCeiling(WorldGenLevel level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MAX_VALUE - 1 : original.call(level);
    }
}
