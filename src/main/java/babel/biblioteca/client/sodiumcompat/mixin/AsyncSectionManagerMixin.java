package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Camera;

@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager"}, remap = false)
public abstract class AsyncSectionManagerMixin {
    @Inject(method = "prepareRenderTrees", at = @At("HEAD"), cancellable = true)
    private void biblioteca$finishReadPhase(CallbackInfo ci) {
        if (SodiumBridge.looping(this)) { SodiumBridge.waitForCulling(this); ci.cancel(); }
    }
    @Inject(method = "finalizeRenderLists", at = @At("HEAD"), cancellable = true)
    private void biblioteca$collect(Camera camera, @Coerce Object viewport, @Coerce Object fog, boolean immediate, CallbackInfo ci) {
        if (SodiumBridge.looping(this)) { SodiumBridge.collectAsync(this, viewport, fog); ci.cancel(); }
    }
}
