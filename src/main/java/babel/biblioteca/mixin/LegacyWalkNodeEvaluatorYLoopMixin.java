package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingLevel;
import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

/** Older walking evaluators keep the same support and downward-scan checks in fewer methods. */
@Mixin(WalkNodeEvaluator.class)
public abstract class LegacyWalkNodeEvaluatorYLoopMixin extends NodeEvaluator {
    //? if >=1.17 {
    @WrapOperation(method = "getBlockPathTypeStatic", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockGetter;getMinY()I"))
    private static int biblioteca$wrappedSupport(BlockGetter level, Operation<Integer> original) {
        return level instanceof LoopingLevel looping && looping.biblioteca$isLooping() ? Integer.MIN_VALUE : original.call(level);
    }
    @WrapOperation(method = {"getStart", "findAcceptedNode"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinY()I"), expect = 3)
    private int biblioteca$boundedDescent(Level level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? this.mob.blockPosition().getY() - Compat.height(level) : original.call(level);
    }
    //?} else {
    /*@ModifyConstant(method = "getBlockPathTypeStatic", constant = @Constant(intValue = 1, ordinal = 0))
    private static int biblioteca$wrappedSupport(int minimum, @Local(argsOnly = true) BlockGetter level) {
        return level instanceof LoopingLevel looping && looping.biblioteca$isLooping() ? Integer.MIN_VALUE : minimum;
    }
    @ModifyExpressionValue(method = "getStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 0))
    private int biblioteca$startFloor(int y) {
        return LoopingRegistry.isLooping(Compat.level(this.mob)) ? y - biblioteca$floor() : y;
    }
    @ModifyConstant(method = "getLandNode", constant = @Constant(intValue = 0, expandZeroConditions = Constant.Condition.GREATER_THAN_ZERO),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/pathfinder/WalkNodeEvaluator;canFloat()Z"),
                    to = @At(value = "FIELD", target = "Lnet/minecraft/world/level/pathfinder/BlockPathTypes;OPEN:Lnet/minecraft/world/level/pathfinder/BlockPathTypes;", ordinal = 1)))
    private int biblioteca$waterFloor(int zero) { return LoopingRegistry.isLooping(Compat.level(this.mob)) ? biblioteca$floor() : zero; }
    @ModifyConstant(method = "getLandNode", constant = @Constant(intValue = 0, expandZeroConditions = Constant.Condition.LESS_THAN_ZERO),
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/world/level/pathfinder/BlockPathTypes;OPEN:Lnet/minecraft/world/level/pathfinder/BlockPathTypes;", ordinal = 2),
                    to = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getMaxFallDistance()I")))
    private int biblioteca$fallFloor(int zero) { return LoopingRegistry.isLooping(Compat.level(this.mob)) ? biblioteca$floor() : zero; }
    private int biblioteca$floor() { return this.mob.blockPosition().getY() - Compat.height(Compat.level(this.mob)); }
    *///?}
}
