package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer", "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer"}, remap = false)
public abstract class FacesMixin {
    @Inject(method = "getVisibleFaces", at = @At("HEAD"), cancellable = true)
    private static void biblioteca$allImageFaces(CallbackInfoReturnable<Integer> cir) {
        // Cached batches are shared by images on both sides of the camera. Raster culling remains enabled.
        if (SodiumBridge.looping()) cir.setReturnValue(127);
    }
}
