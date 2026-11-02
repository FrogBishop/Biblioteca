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
@Mixin(targets = {"me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer"}, remap = false)
public abstract class WorldRendererVisibilityMixin {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void biblioteca$periodicEntities(CallbackInfoReturnable<Boolean> cir) {
        if (SodiumBridge.looping(this)) cir.setReturnValue(true);
    }
}
