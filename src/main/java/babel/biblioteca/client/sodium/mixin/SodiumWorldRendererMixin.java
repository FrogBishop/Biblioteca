package babel.biblioteca.client.sodium.mixin;

import babel.biblioteca.client.sodium.SodiumRenderContext;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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

    @ModifyArg(method = "renderTileEntities", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", remap = true), index = 1)
    private double biblioteca$nearestBlockEntity(double y) {
        return world != null && LoopingRegistry.isLooping(world) ? SeamPositions.foldForDistance(world, y, 0) : y;
    }
}
