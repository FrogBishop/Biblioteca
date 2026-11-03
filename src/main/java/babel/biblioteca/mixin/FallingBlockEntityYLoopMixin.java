package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import babel.biblioteca.Compat;
import org.spongepowered.asm.mixin.injection.At;

/** Removes false out-of-world checks at the seam while retaining the native age timeout. */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityYLoopMixin {

//? if >=1.17 {
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinY()I"))
    private int biblioteca$noFloor(Level level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MIN_VALUE : original.call(level);
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxY()I"))
    private int biblioteca$noCeiling(Level level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MAX_VALUE : original.call(level);
    }

//?} else {
/*    // In 1.16 tick reads BlockPos.getY only for the two out-of-world fallback comparisons.
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getY()I"), require = 2)
    private int biblioteca$legacyBounds(int y) {
        return LoopingRegistry.isLooping(Compat.level((FallingBlockEntity) (Object) this)) ? 1 : y;
    }

*///?}
}
