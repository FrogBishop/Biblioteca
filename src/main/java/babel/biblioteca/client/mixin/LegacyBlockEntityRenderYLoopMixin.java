package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a block entity at the periodic layer nearest the camera.
 *
 * The caller has already translated the pose to blockPos - camera. Folding that translation by
 * whole periods puts the block entity on the layer the player is looking at. The caller pushes
 * and pops the pose around this call, so translating back at RETURN is only needed to keep the
 * pose clean for the next block entity.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class LegacyBlockEntityRenderYLoopMixin {

    @Inject(
            method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;F"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"))
    private void biblioteca$foldBlockEntityPose(
            BlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, CallbackInfo ci) {

        double shift = biblioteca$shift(blockEntity);
        if (shift != 0.0) poseStack.translate(0.0, shift, 0.0);
    }

    @Inject(
            method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;F"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("RETURN"))
    private void biblioteca$restoreBlockEntityPose(
            BlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, CallbackInfo ci) {

        double shift = biblioteca$shift(blockEntity);
        if (shift != 0.0) poseStack.translate(0.0, -shift, 0.0);
    }

    private static double biblioteca$shift(BlockEntity blockEntity) {
        if (blockEntity instanceof BeaconBlockEntity) return 0.0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) return 0.0;

        int period = minecraft.level.getHeight();
        if (period <= 0) return 0.0;

        double cameraY = ClientCompat.cameraPosition().y;
        double offsetY = blockEntity.getBlockPos().getY() - cameraY;
        return -(double) Math.round(offsetY / period) * period;
    }
}
