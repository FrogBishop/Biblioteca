package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes ViewArea's section lookup by section node.
 *
 * SectionOcclusionGraphLoopMixin scans every section of the view area to find the ones on the
 * far side of the seam. ViewArea only offers the BlockPos form publicly, and the node form
 * takes a long, which keeps that scan allocation free.
 */
@Mixin(ViewArea.class)
public interface ViewAreaAccessor {

    @Invoker("getRenderSection")
    SectionRenderDispatcher.RenderSection biblioteca$getRenderSection(long sectionNode);
}
