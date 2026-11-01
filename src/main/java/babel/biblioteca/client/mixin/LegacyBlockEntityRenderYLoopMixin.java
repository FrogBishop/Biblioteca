package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.EntityMirrors;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Repeat the native block-entity draw with independent poses, retaining one source object. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class LegacyBlockEntityRenderYLoopMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V")
    private void biblioteca$blockEntityImages(BlockEntity blockEntity, float partial, PoseStack pose,
            MultiBufferSource buffers, Operation<Void> original) {
        var level = blockEntity.getLevel();
        if (blockEntity instanceof BeaconBlockEntity || level == null || !LoopingRegistry.isLooping(level)) {
            original.call(blockEntity, partial, pose, buffers); return;
        }
        int period = Compat.height(level);
        double sourceY = blockEntity.getBlockPos().getY(), cameraY = ClientCompat.cameraPosition().y;
        long nearest = -Math.round((sourceY - cameraY) / period);
        long first = nearest, last = nearest;
        if (EntityMirrors.enabled(level)) {
            first = Math.min(nearest, EntityMirrors.first(sourceY, cameraY, period));
            last = Math.max(nearest, EntityMirrors.last(sourceY, cameraY, period));
        }
        for (long image = first; image <= last; image++) {
            pose.pushPose();
            try {
                pose.translate(0, image * (double) period, 0);
                original.call(blockEntity, partial, pose, buffers);
            } finally { pose.popPose(); }
        }
    }
}
