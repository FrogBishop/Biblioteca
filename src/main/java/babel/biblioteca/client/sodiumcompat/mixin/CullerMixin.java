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
@Mixin(targets = {"me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller", "net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller"}, remap = false)
public abstract class CullerMixin {
    @Inject(method = "findVisible", at = @At("HEAD"), cancellable = true)
    private void biblioteca$collect(@Coerce Object visitor, @Coerce Object viewport, float distance, boolean occlusion, int frame, CallbackInfo ci) {
        if (SodiumBridge.looping(this)) { SodiumBridge.collect(this, visitor, viewport, distance, frame); ci.cancel(); }
    }
}
