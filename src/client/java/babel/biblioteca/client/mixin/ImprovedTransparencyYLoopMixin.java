package babel.biblioteca.client.mixin;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Renders translucency with the order-independent path in Y-looping dimensions.
 *
 * Water is where this became visible: the bottom row's water and the mirrored copy of the top
 * row's water meet at the seam, and the copy's quads were ordered for the source row's camera.
 * Vanilla's sorted translucency bakes one quad order into the mesh, for one viewpoint; the same
 * mesh is drawn at several periodic heights, so sorting it for one copy breaks every other copy.
 * Re-sorting the shared mesh cannot solve that - one mesh, one baked order, many viewpoints.
 *
 * The improved-transparency path (OIT) does not care about draw or quad order, so the shared mesh
 * is correct at every height and the seam water blends normally. It is forced only while the
 * current dimension loops, only when the GPU is not on the warn list, and never in the wireframe
 * debug view - which vanilla also excludes from its own improved-transparency check.
 */
@Mixin(GameRenderer.class)
public abstract class ImprovedTransparencyYLoopMixin {

    @Unique private static boolean biblioteca$oitLogged;

    @Inject(method = "useImprovedTransparency", at = @At("HEAD"), cancellable = true)
    private void biblioteca$useOitInLoopingDimensions(CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) return;
        if (minecraft.getGpuWarnlistManager().hasWarnings()) return;

        GameRenderer self = (GameRenderer) (Object) this;
        if (self.gameRenderState().levelRenderState.renderWireframeTerrain) return;

        if (!biblioteca$oitLogged) {
            biblioteca$oitLogged = true;
            Biblioteca.LOGGER.info("[Biblioteca] Using order-independent transparency in looping dimensions.");
        }

        cir.setReturnValue(true);
    }
}
