package babel.biblioteca.client.mixin;

import babel.biblioteca.SeamPositions;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Older render snapshots contain whole columns, so folding the lookup selects the wrapped section. */
@Mixin(targets = "net.minecraft.client.renderer.chunk.RenderChunk")
public abstract class LegacyRenderChunkYLoopMixin {
    @Shadow @Final private LevelChunk wrapped;

    @ModifyVariable(method = {"getBlockState", "getBlockEntity"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$wrappedRow(BlockPos position) {
        return LoopingRegistry.isLooping(wrapped.getLevel()) ? SeamPositions.foldBlockPos(wrapped.getLevel(), position) : position;
    }
}
