package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.EntityMirrors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else {
/*import net.minecraft.client.renderer.state.CameraRenderState;
*///?}
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Folds only the draw translation; source positions stay intact for lighting and beacon copies. */
@Mixin(LevelRenderer.class)
public abstract class BlockEntityPositionYLoopMixin {
    @WrapOperation(method = "submitBlockEntities", at = @At(value = "INVOKE",
            //? if >=26.1 {
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
            //?} else {
            /*target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"))
            *///?}
    private void biblioteca$blockEntityImages(BlockEntityRenderDispatcher dispatcher, BlockEntityRenderState state,
            PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        var level = Minecraft.getInstance().level;
        if (!EntityMirrors.enabled(level) || state instanceof BeaconRenderState) {
            original.call(dispatcher, state, pose, collector, camera); return;
        }
        int period = Compat.height(level);
        double sourceY = state.blockPos.getY();
        long nearest = -Math.round((sourceY - camera.pos.y) / period);
        for (long image = Math.min(nearest, EntityMirrors.first(sourceY, camera.pos.y, period)),
                  last = Math.max(nearest, EntityMirrors.last(sourceY, camera.pos.y, period)); image <= last; image++) {
            pose.pushPose();
            try {
                // The enclosing translation already chose the nearest image. Submission copies
                // the pose; the immutable source state keeps canonical light and block data.
                pose.translate(0, (image - nearest) * (double) period, 0);
                original.call(dispatcher, state, pose, collector, camera);
            } finally { pose.popPose(); }
        }
    }

    @WrapOperation(method = "submitBlockEntities", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void biblioteca$nearestLayer(PoseStack pose, double x, double y, double z, Operation<Void> original,
            @Local BlockEntityRenderState state) {
        var level = Minecraft.getInstance().level;
        if (!(state instanceof BeaconRenderState) && level != null && LoopingRegistry.isLooping(level)) {
            int period = Compat.height(level);
            if (period > 0) y -= Math.round(y / period) * (double) period;
        }
        original.call(pose, x, y, z);
    }
}
