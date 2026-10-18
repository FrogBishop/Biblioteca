package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Suppresses the void-darkening term of the fog colour, and nothing else.
 *
 * The previous version cancelled computeFogColor at HEAD. That method is the only writer of
 * FogData.color, and FogData.color starts as a bare new Vector4f() - (0,0,0,0), not the white
 * the old comment claimed. With the method cancelled the colour stayed black, and that colour is
 * the clear colour LevelRenderer paints the frame with: the sky's upper disc is still drawn in
 * SKY_COLOR, so the sky read as blue above the horizon and black below it. It also zeroed the
 * alpha that apply_fog multiplies its blend factor by, which switched off every atmospheric fog
 * in the dimension, not just the void darkening.
 *
 * The darkness term is the only part of the calculation that cares about the world floor:
 * darkness = clamp((voidDarknessOnsetRange + minY - cameraY) / voidDarknessOnsetRange, 0, 1).
 * Forcing that one clamp to zero keeps the fog colour, the cloud colour and the fog ranges, and
 * leaves the Darkness/Blindness effects untouched because they merge with Math.max afterwards.
 */
@Mixin(FogRenderer.class)
public abstract class VoidFogMixin {

    @ModifyExpressionValue(
            method = "computeFogColor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private float biblioteca$noVoidDarkness(
            float original, @Local(argsOnly = true) ClientLevel level) {
        return LoopingRegistry.isLooping(level) ? 0.0F : original;
    }
}
