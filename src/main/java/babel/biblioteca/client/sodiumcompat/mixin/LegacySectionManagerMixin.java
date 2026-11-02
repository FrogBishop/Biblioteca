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
public abstract class LegacySectionManagerMixin {
    @Inject(method = "iterateChunks", at = @At("HEAD"), cancellable = true)
    private void biblioteca$collect(Camera camera, @Coerce Object frustum, int frame, boolean spectator, CallbackInfo ci) {
        if (SodiumBridge.looping(this)) { SodiumBridge.collectLegacy(this, camera, frustum, frame); ci.cancel(); }
    }
}
