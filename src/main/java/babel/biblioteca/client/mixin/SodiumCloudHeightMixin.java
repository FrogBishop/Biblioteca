package babel.biblioteca.client.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumClouds;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionSpecialEffects.class)
public abstract class SodiumCloudHeightMixin {
    @Inject(method = "getCloudHeight", at = @At("RETURN"), cancellable = true)
    private void biblioteca$sodiumCloudHeight(CallbackInfoReturnable<Float> cir) {
        Float height = SodiumClouds.height();
        if (height != null) cir.setReturnValue(height);
    }
}
