package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps vanilla's "dark disc" out of a Y-looping dimension.
 *
 * A ring has no below-the-horizon side: every row is an ordinary row. Vanilla still paints the
 * lower half of the sky hard-coded black whenever the player's eyes are below
 * LevelData.getHorizonHeight(), and that method returns a fixed 63.0 for every non-flat world.
 * At the bottom of a looping dimension that is exactly where the player stands, so the black
 * disc would cover the lower sky even after the fog colour is fixed.
 *
 * The disc is drawn by SkyRenderer.renderDarkDisc with colour (0,0,0,1); suppressing the test
 * that schedules it leaves the lower hemisphere to the clear/fog colour, which is the same
 * colour the horizon fades into.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererYLoopMixin {

    @Inject(method = "shouldRenderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void biblioteca$noDarkDisc(
            float partialTicks, ClientLevel level, CallbackInfoReturnable<Boolean> cir) {
        if (LoopingRegistry.isLooping(level)) {
            cir.setReturnValue(false);
        }
    }
}
