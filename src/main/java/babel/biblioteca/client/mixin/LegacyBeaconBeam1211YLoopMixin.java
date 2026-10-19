package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Repeats a 1.21.1 beacon beam once per period.
 *
 * The block-entity pose has already been folded to the nearest layer. This wraps the private
 * beam call inside BeaconRenderer.render and translates the pose by whole periods, so the beam
 * stops at its own base in every copy instead of being one 2048-block column.
 */
@Mixin(BeaconRenderer.class)
public abstract class LegacyBeaconBeam1211YLoopMixin {

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/BeaconRenderer;"
                            + "renderBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;FJIII)V"))
    private void biblioteca$repeatBeam(
            PoseStack poseStack, MultiBufferSource buffers, float partialTick,
            long gameTime, int y, int height, int color,
            Operation<Void> original) {

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) {
            original.call(poseStack, buffers, partialTick, gameTime, y, height, color);
            return;
        }

        int period = minecraft.level.getHeight();
        if (period <= 0) {
            original.call(poseStack, buffers, partialTick, gameTime, y, height, color);
            return;
        }

        double viewDistanceBlocks = minecraft.options.getEffectiveRenderDistance() * 16.0;
        int maxSteps = Math.min(8, (int) Math.ceil(viewDistanceBlocks / period) + 1);

        for (int step = -maxSteps; step <= maxSteps; step++) {
            poseStack.pushPose();
            poseStack.translate(0.0, (double) step * period, 0.0);
            original.call(poseStack, buffers, partialTick, gameTime, y, height, color);
            poseStack.popPose();
        }
    }
}
