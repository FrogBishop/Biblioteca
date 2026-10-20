package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses vanilla's black sky disc on 1.21.8.
 *
 * 1.21.8 split the disc out of renderSky: shouldRenderDarkDisc() compares the eye height with
 * LevelData.getHorizonHeight() (a fixed 63.0 for non-flat worlds) and renderDarkDisc() paints the
 * lower hemisphere black. A looping dimension has no below-the-horizon side, so the disc is never
 * correct there. Answering the test with false leaves the lower half to the clear/fog colour,
 * which is the colour the horizon fades into.
 */
@Mixin(LevelRenderer.class)
public abstract class MidSky1218Mixin {

    @Inject(method = "shouldRenderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void biblioteca$noDarkDisc(float partialTick, CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && LoopingRegistry.isLooping(minecraft.level)) {
            cir.setReturnValue(false);
        }
    }
}
