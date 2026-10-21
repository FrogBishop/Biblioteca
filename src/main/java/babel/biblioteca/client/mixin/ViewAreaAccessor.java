package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The block-position lookup exists across all three terrain renderer generations. */
@Mixin(ViewArea.class)
public interface ViewAreaAccessor {
    @Invoker("getRenderSectionAt")
    SectionRenderDispatcher.RenderSection biblioteca$getRenderSectionAt(BlockPos position);
}
