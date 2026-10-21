package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops a falling block from being turned into an item just because it reached the bottom row.
 *
 * <p>Vanilla discards a falling block that has been falling for a while and is outside the build
 * height, and leaves the block as an item. In a looping dimension nothing can leave the build
 * height: the position is folded, and the bottom row {@code y = minY} is an ordinary row rather
 * than "below the world". The test nonetheless reads that row as out of bounds
 * ({@code y <= getMinY()}), so a block that crosses the seam and lands on the bottom row is
 * dropped as an item instead of being placed.
 *
 * <p>Answering the two edge queries with "no edge" keeps only the vanilla fallback that ends a
 * block which never found ground, which is still wanted for a world whose bottom is open air.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityYLoopMixin {

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
}
