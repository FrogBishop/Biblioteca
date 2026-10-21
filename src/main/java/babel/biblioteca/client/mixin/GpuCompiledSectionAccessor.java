package babel.biblioteca.client.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The 1.21.5 sort state is package-private; later releases expose it on CompiledSectionMesh. */
@Mixin(SectionRenderDispatcher.CompiledSection.class)
public interface GpuCompiledSectionAccessor {
    @Accessor("transparencyState")
    MeshData.SortState biblioteca$getTransparencyState();
}
