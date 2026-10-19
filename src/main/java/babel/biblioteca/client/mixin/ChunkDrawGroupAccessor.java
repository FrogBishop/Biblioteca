package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.DynamicGpuData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes the mutable draws list inside the package-private
 * LevelRenderer$ChunkDrawGroup record so that LevelRendererYLoopMixin
 * can append mirror draw commands to it.
 */
@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer$ChunkDrawGroup")
public interface ChunkDrawGroupAccessor {
    @Accessor("draws")
    List<DynamicGpuData.IndexedDraw> biblioteca$getDraws();
}
