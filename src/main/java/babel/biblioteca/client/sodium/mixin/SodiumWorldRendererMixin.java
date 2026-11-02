package babel.biblioteca.client.sodium.mixin;

import babel.biblioteca.client.sodium.SodiumRenderContext;

import babel.biblioteca.LoopingRegistry;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererMixin {
    @Shadow private ClientLevel world;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void biblioteca$useSodiumTerrain(CallbackInfo ci) { SodiumRenderContext.activate(); }

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void biblioteca$periodicEntities(CallbackInfoReturnable<Boolean> cir) {
        // Sodium's ordinary section graph does not know about the periodic images.
        if (world != null && LoopingRegistry.isLooping(world)) cir.setReturnValue(true);
    }

    // BlockEntityRenderDispatcher now supplies the nearest and additional images for every
    // renderer. Keep Sodium's input pose canonical so that offset is applied exactly once.
}
