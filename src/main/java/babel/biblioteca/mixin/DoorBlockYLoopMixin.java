package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A door is placed as two blocks, and vanilla refuses to place one whose upper half
 * would sit above the world top (getStateForPlacement compares against getMaxY()).
 * In a Y-looping dimension that upper half simply belongs to the bottom row, so the
 * ceiling comparison is answered with "no ceiling" and the folded setBlock puts the
 * upper half where it belongs.
 */
@Mixin(DoorBlock.class)
public abstract class DoorBlockYLoopMixin {

    @WrapOperation(
            method = "getStateForPlacement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxY()I"))
    private int biblioteca$noCeiling(Level level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MAX_VALUE : original.call(level);
    }
}
