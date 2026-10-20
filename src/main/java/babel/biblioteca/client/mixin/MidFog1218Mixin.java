package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removes only the void-darkening term of the 1.21.8 fog colour.
 *
 * computeFogColor computes darkness = clamp((voidDarknessOnsetRange + minY - cameraY) /
 * voidDarknessOnsetRange, 0, 1) and multiplies the fog colour by (1 - darkness)^2. This is the
 * same term 26.3's VoidFogMixin suppresses; only the package moved. A Y-looping dimension has no
 * floor to fade into, and the fog colour is also the frame's clear colour, so the darkened value
 * shows up as a black lower half of the sky at low Y.
 *
 * Forcing the clamp to zero keeps the fog colour, the cloud colour, the fog ranges and the
 * Darkness/Blindness effects (which merge with Math.max afterwards).
 */
@Mixin(FogRenderer.class)
public abstract class MidFog1218Mixin {

    @ModifyExpressionValue(
            method = "computeFogColor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private float biblioteca$noVoidDarkness(float original, @Local(argsOnly = true) ClientLevel level) {
        return LoopingRegistry.isLooping(level) ? 0.0F : original;
    }
}
