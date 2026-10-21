package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removes only the void-darkening term of the fog colour, not the colour itself.
 *
 * computeFogColor is the only writer of FogData.color, which starts at (0,0,0,0). Cancelling the
 * whole method therefore clears the frame to black - the sky's lower half is the cleared
 * background, so it reads as blue above the horizon and black below - and zeroes the alpha that
 * apply_fog multiplies its blend factor by, disabling every atmospheric fog in the dimension.
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
        // false (the default) keeps vanilla void fog; true removes its darkening term.
        return LoopingRegistry.isLooping(level) && LoopingRegistry.hasNoVoidFog(level)
                ? 0.0F : original;
    }
}
