package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Calls the private 1.21.8 chunk prepare step again for a periodic layer.
 *
 * The only vanilla call site lives in the synthetic lambda body that addMainPass hands to the
 * frame graph, not in addMainPass itself. A @WrapOperation on addMainPass therefore finds no
 * target and, with the legacy nodes' defaultRequire of 0, silently draws nothing. Wrapping the
 * method itself avoids depending on the synthetic name.
 */
@Mixin(LevelRenderer.class)
public interface MidLevelRendererInvoker {

    @Invoker("prepareChunkRenders")
    ChunkSectionsToRender biblioteca$prepareChunkRenders(Matrix4fc matrix, double x, double y, double z);
}
