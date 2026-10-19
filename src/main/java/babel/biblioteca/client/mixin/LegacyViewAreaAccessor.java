package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the classic ViewArea section lookup, which is protected. */
@Mixin(ViewArea.class)
public interface LegacyViewAreaAccessor {

    @Invoker("getRenderSectionAt")
    SectionRenderDispatcher.RenderSection biblioteca$getRenderSectionAt(BlockPos pos);
}
