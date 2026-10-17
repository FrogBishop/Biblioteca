package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables void fog (the black-dark overlay near the world bottom) in looping
 * dimensions by cancelling computeFogColor entirely for those dimensions.
 *
 * computeFogColor is called by setupFog and writes the fog color to a Vector4f
 * that setupFog then stores in FogData.color. Cancelling it leaves FogData.color
 * at its constructed default (0, 0, 0, 0), but setupFog always writes a valid
 * color because computeFogColor is called solely to populate the color parameter
 * passed in — which is FogData.color. When we cancel, setupFog simply leaves
 * FogData.color as-is (the default white 1,1,1,1 set by FogData's constructor).
 */
@Mixin(FogRenderer.class)
public abstract class VoidFogMixin {

    @Inject(
            method = "computeFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IFLorg/joml/Vector4f;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void biblioteca$cancelVoidFogColor(
            Camera camera, float partialTick, ClientLevel level,
            int renderDistance, float darkenWorldAmount, Vector4f colorOutput,
            CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!LoopingDimensionCache.isLooping(mc.level.dimension())) return;
        // Cancel the void-darkness computation. setupFog will keep FogData.color
        // at its default sky colour set before this call.
        ci.cancel();
    }
}
