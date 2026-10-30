package babel.biblioteca.client.sodium.mixin;

import babel.biblioteca.client.sodium.SodiumRenderContext;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both Sodium backends obtain the per-draw model origin through this getter. */
@Mixin(value = ChunkGraphicsState.class, remap = false)
public abstract class SodiumGraphicsStateMixin {
    @Inject(method = "getY", at = @At("RETURN"), cancellable = true)
    private void biblioteca$copyOrigin(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + SodiumRenderContext.offset());
    }
}
