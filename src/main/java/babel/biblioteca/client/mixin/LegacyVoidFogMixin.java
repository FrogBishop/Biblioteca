package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Suppresses only the old floor-relative fog term when the no_void_fog attribute is enabled. */
@Mixin(FogRenderer.class)
public abstract class LegacyVoidFogMixin {
//? if >=1.17 {
    @ModifyExpressionValue(
            //? if >=1.21.2 {
            method = "computeFogColor",
            //?} else {
            /*method = "setupColor",
            *///?}
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinY()I"))
    private static int biblioteca$noVoidDarkness(int original, @Local(argsOnly = true) ClientLevel level) {
        return LoopingRegistry.isLooping(level) && LoopingRegistry.hasNoVoidFog(level) ? Integer.MIN_VALUE : original;
    }

//?} else {
/*    @ModifyVariable(method = "setupColor", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private static double biblioteca$legacyVoidFactor(double factor, @Local(argsOnly = true) ClientLevel level) {
        // Only the first stored double is the height factor; later blindness adjustments remain vanilla.
        return LoopingRegistry.isLooping(level) && LoopingRegistry.hasNoVoidFog(level) ? 1.0 : factor;
    }

*///?}
}
