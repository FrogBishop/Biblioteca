package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the 1.21.8 ViewArea section lookup by section node. */
@Mixin(ViewArea.class)
public interface MidViewAreaAccessor {

    @Invoker("getRenderSection")
    SectionRenderDispatcher.RenderSection biblioteca$getRenderSection(long sectionNode);
}
