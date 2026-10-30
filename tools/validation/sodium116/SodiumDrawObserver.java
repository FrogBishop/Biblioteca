package biblioteca.sodiumtest.mixin;

import biblioteca.sodiumtest.SodiumProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumDrawObserver {
    @Inject(method = "drawChunkLayer", at = @At("HEAD"))
    private void count(CallbackInfo ci) { SodiumProbe.layers++; }
}
