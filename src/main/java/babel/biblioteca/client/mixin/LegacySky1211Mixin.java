package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps vanilla's hard-coded black sky disc out of a Y-looping dimension on 1.21.1.
 *
 * Before 1.21.8 the lower sky disc is not a separate method. renderSky compares the eye height
 * with LevelData.getHorizonHeight() and, when the eye is below it, draws a black disc of radius
 * 512 at +12 above the camera. getHorizonHeight() answers a fixed 63.0 for every non-flat world,
 * so at the bottom of a looping dimension - where every row is an ordinary row and there is no
 * below-the-horizon side - that disc paints the lower half of the screen black.
 *
 * Folding the horizon far below the world makes the comparison fail, which is the same result as
 * the 26.3 SkyRendererYLoopMixin gets by answering shouldRenderDarkDisc() with false. Only the
 * comparison is affected; the horizon value is not used anywhere else in renderSky.
 */
@Mixin(LevelRenderer.class)
public abstract class LegacySky1211Mixin {

    @ModifyExpressionValue(
            method = "renderSky",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel$ClientLevelData;"
                            + "getHorizonHeight(Lnet/minecraft/world/level/LevelHeightAccessor;)D"))
    private double biblioteca$noDarkDisc(double original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) {
            return original;
        }
        return -1.0E30;
    }
}
