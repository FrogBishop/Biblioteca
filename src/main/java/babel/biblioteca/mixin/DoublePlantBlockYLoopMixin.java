package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoublePlantBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Same rule as DoorBlock for tall grass, sunflowers and the other two-block plants:
 * the upper half is allowed to wrap into the bottom row of a Y-looping dimension
 * instead of the placement being refused at the world top.
 */
@Mixin(DoublePlantBlock.class)
public abstract class DoublePlantBlockYLoopMixin {

    @WrapOperation(
            method = "getStateForPlacement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxY()I"),
            require = 1)
    private int biblioteca$noCeiling(Level level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MAX_VALUE : original.call(level);
    }
}
