package babel.biblioteca.client.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets prepareChunkRenders see only the sections a periodic copy can actually show.
 *
 * The 1.21.8 prepare step walks LevelRenderer.visibleSections and bakes one transform per
 * section. Filtering a copy therefore means replacing the list around that call; the field is
 * final, so the write goes through this accessor.
 */
@Mixin(LevelRenderer.class)
public interface MidLevelRendererAccessor {

    @Accessor("visibleSections")
    void biblioteca$setVisibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> sections);
}
