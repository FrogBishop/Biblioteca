package babel.biblioteca.client.mixin;

import babel.biblioteca.client.CloudFold;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import java.util.Optional;

/**
 * 1.21.8 draws the single cloud layer at the dimension type's cloudHeight.
 * Folded here so the layer appears at the periodic copy nearest the player, the
 * same way CloudRendererLoopMixin does on 26.3.
 */
@Mixin(LevelRenderer.class)
public abstract class LegacyClouds1218Mixin {

    @ModifyExpressionValue(
            method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "L" + "net/minecraft/world/level/dimension/DimensionType;"
                            + "cloudHeight()Ljava/util/Optional;"))
    private Optional<Integer> biblioteca$nearestCloudLayer(Optional<Integer> cloudHeight) {
        return cloudHeight.map(CloudFold::nearest);
    }
}
