package babel.biblioteca.client.mixin;

import babel.biblioteca.client.CloudFold;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * 1.21.1 draws the single cloud layer at DimensionSpecialEffects.getCloudHeight().
 * Folded here so the layer appears at the periodic copy nearest the player, the
 * same way CloudRendererLoopMixin does on 26.3.
 */
@Mixin(LevelRenderer.class)
public abstract class LegacyClouds1211Mixin {

    @ModifyExpressionValue(
            method = "renderClouds",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"))
    private float biblioteca$nearestCloudLayer(float cloudHeight) {
        return CloudFold.nearest(cloudHeight);
    }
}
