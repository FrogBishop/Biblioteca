package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SaplingBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removes the world ceiling from the sapling's bone meal check.
 *
 * <p>Bone meal first asks whether the cell the future trunk will occupy is inside the build height,
 * and a sapling on the top row fails that test: the trunk continues in the bottom row. In a ring
 * there is no row that is "outside the world", so there is nothing to reject - the same reasoning
 * as the door, tall plant and vine hooks, all of which answer the height query with "no edge".
 *
 * <p>The random-tick light check is deliberately left alone. It samples the cell above the sapling,
 * which for the top row is the first row past the stored period, and the sky-light channel answers
 * 15 there - the same value a sapling standing in the open on an ordinary row sees. No folding or
 * special case is needed, and folding it to the bottom row would wrongly make an exposed sapling
 * depend on the light of the world's floor.
 *
 * <p>The remaining ceiling on tree growth is in {@code TreeFeature}, which has its own hook.
 */
@Mixin(SaplingBlock.class)
public abstract class SaplingBlockYLoopMixin {

    @WrapOperation(
            method = "isValidBonemealTarget",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelReader;isInsideBuildHeight(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean biblioteca$anyHeight(LevelReader level, BlockPos pos, Operation<Boolean> original) {
        return level instanceof Level realLevel && LoopingRegistry.isLooping(realLevel)
                || original.call(level, pos);
    }
}
