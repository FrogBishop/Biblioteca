package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Before NeighborUpdater, Level dispatches the individual block notification itself. */
@Mixin(Level.class)
public abstract class LegacyNeighborYLoopMixin {
    @ModifyVariable(method = "neighborChanged", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldRecipient(BlockPos pos) {
        Level level = (Level) (Object) this;
        return LoopingRegistry.isLooping(level) ? SeamPositions.foldBlockPos(level, pos) : pos;
    }
}
