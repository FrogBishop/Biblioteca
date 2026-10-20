package babel.biblioteca.client.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the 1.21.1 copy path swap the visible-section list for one periodic layer.
 *
 * renderSectionLayer reads LevelRenderer.visibleSections directly, so filtering a copy means
 * replacing that list for the duration of one call. The field is final, which is why this is an
 * accessor rather than a plain @Shadow write.
 */
@Mixin(LevelRenderer.class)
public interface LegacyLevelRendererAccessor {

    @Accessor("visibleSections")
    void biblioteca$setVisibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> sections);
}
