package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SpeleothemBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Pointed dripstone searches up or down for the block it grows from and gives up as soon
 * as the search leaves the build height, which is what stops a stalactite at the bottom
 * row from reaching the top row. In a Y-looping dimension the scan may continue through
 * the seam.
 */
@Mixin(SpeleothemBlock.class)
public abstract class DripstoneYLoopMixin {

    @WrapOperation(
            method = "findBlockVertical",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;isOutsideBuildHeight(I)Z"))
    private static boolean biblioteca$searchThroughTheSeam(
            LevelAccessor level, int y, Operation<Boolean> original) {
        return LoopingRegistry.isLooping(level) ? false : original.call(level, y);
    }
}
