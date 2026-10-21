package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LegacyVertexData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The layer is known before an upload is queued, so opaque meshes need no centroid copy. */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class LegacySectionBufferMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void biblioteca$tagTranslucentBuffer(RenderType layer, CallbackInfoReturnable<VertexBuffer> cir) {
        ((LegacyVertexData) cir.getReturnValue()).biblioteca$trackQuads(
                layer == RenderType.translucent() || layer == RenderType.tripwire());
    }
}
