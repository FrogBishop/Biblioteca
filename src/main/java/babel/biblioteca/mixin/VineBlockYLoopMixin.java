package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.VineBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vines stop spreading up at the world top and down at the world bottom, because the
 * random tick compares against getMaxY() and getMinY(). In a Y-looping dimension the row
 * past either edge is an ordinary row of the ring, so both comparisons are answered with
 * "no edge" and the folded setBlock puts the new vine there.
 */
@Mixin(VineBlock.class)
public abstract class VineBlockYLoopMixin {

    @WrapOperation(
            method = "randomTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMaxY()I"),
            require = 1)
    private int biblioteca$noCeiling(ServerLevel level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MAX_VALUE : original.call(level);
    }

    @WrapOperation(
            method = "randomTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMinY()I"),
            require = 1)
    private int biblioteca$noFloor(ServerLevel level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MIN_VALUE : original.call(level);
    }
}
