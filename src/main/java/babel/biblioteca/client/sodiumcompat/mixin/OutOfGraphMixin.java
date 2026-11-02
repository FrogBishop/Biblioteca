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
@Mixin(targets = {"me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager"}, remap = false)
public abstract class OutOfGraphMixin {
    @Inject(method = "isOutOfGraph", at = @At("HEAD"), cancellable = true)
    private void biblioteca$usePeriodicGraph(CallbackInfoReturnable<Boolean> cir) {
        if (SodiumBridge.looping(this)) cir.setReturnValue(false);
    }
}
