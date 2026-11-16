package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;

/** Concrete destination of NeighborUpdater.executeUpdate, retaining its native error handling. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class NeoForge1201NeighborMixin {
    @WrapMethod(method = "neighborChanged")
    private void biblioteca$canonicalNeighbor(Level level, BlockPos pos, Block changed, BlockPos source,
            boolean moved, Operation<Void> original) {
        if (LoopingRegistry.isLooping(level)) {
            int min = Compat.minY(level), period = Compat.height(level);
            if (pos.getY() < min || pos.getY() >= min + period)
                pos = new BlockPos(pos.getX(), Math.floorMod(pos.getY() - min, period) + min, pos.getZ());
        }
        original.call(level, pos, changed, source, moved);
    }
}
