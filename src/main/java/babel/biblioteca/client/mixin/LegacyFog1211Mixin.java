package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removes only the void-darkening factor from the 1.21.1 fog colour.
 *
 * setupColor computes u = (cameraY - minBuildHeight) * clearColorScale, clamps it to [0,1] and
 * multiplies the fog colour by u * u. The term exists so the fog fades to black as the camera
 * approaches the world floor. A Y-looping dimension has no floor - the row below is an ordinary
 * row - so the darkened colour is wrong there. It is also the clear colour, because levelFogColor
 * writes it before RenderSystem.clear(); that is what turns the lower half of the sky black at
 * low Y even after the dark disc is suppressed.
 *
 * Reporting the floor far below the camera keeps u above 1 and leaves the rest of setupColor
 * untouched: biome fog, sunrise, rain, thunder and water fog all still apply. Cancelling the
 * whole method would zero the clear colour and every fog in the dimension instead.
 *
 * 1.21.8 uses a different formula (voidDarknessOnsetRange); MidFog1218Mixin patches that one.
 */
@Mixin(FogRenderer.class)
public abstract class LegacyFog1211Mixin {

    @ModifyExpressionValue(
            method = "setupColor",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinBuildHeight()I"))
    private static int biblioteca$noVoidDarkness(int original, @Local(argsOnly = true) ClientLevel level) {
        return LoopingRegistry.isLooping(level) ? Integer.MIN_VALUE : original;
    }
}
