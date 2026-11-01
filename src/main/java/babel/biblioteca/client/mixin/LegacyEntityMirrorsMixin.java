package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.client.EntityMirrors;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LegacyEntityMirrorsMixin {
    @ModifyExpressionValue(
            //? if >=1.21.2 {
            method = "collectVisibleEntities",
            //?} else {
            /*method = "renderLevel",
            *///?}
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    private boolean biblioteca$includeCameraEntity(boolean detached) {
        return detached || EntityMirrors.enabled(Minecraft.getInstance().level);
    }

    @WrapOperation(method = "renderEntity", at = @At(value = "INVOKE",
            //? if >=1.21.2 {
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
            //?} else {
            /*target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
            *///?}
    private void biblioteca$renderImages(EntityRenderDispatcher dispatcher, Entity entity, double x, double y, double z,
            //? if <1.21.2 {
            /*float yaw,
            *///?}
            float partial, PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original,
            @Local(argsOnly = true, ordinal = 0) double camX, @Local(argsOnly = true, ordinal = 1) double camY,
            @Local(argsOnly = true, ordinal = 2) double camZ) {
        if (!EntityMirrors.enabled(Compat.level(entity))) {
            //? if >=1.21.2 {
            original.call(dispatcher, entity, x, y, z, partial, pose, buffers, light);
            //?} else {
            /*original.call(dispatcher, entity, x, y, z, yaw, partial, pose, buffers, light);
            *///?}
            return;
        }
        int period = Compat.height(Compat.level(entity));
        long nearest = -Math.round(y / period);
        boolean hideNearest = EntityMirrors.hiddenCameraBody(entity, EntityMirrors.camera());
        for (long image = Math.min(nearest, EntityMirrors.first(y + camY, camY, period)),
                  last = Math.max(nearest, EntityMirrors.last(y + camY, camY, period)); image <= last; image++) {
            if (image == nearest && hideNearest) continue;
            double shift = image * (double) period;
            if (image != nearest && !EntityMirrors.visible(dispatcher, entity, EntityMirrors.frustum(), camX, camY, camZ, partial, shift)) continue;
            EntityMirrors.image(entity, shift, () -> {
                //? if >=1.21.2 {
                original.call(dispatcher, entity, x, y + shift, z, partial, pose, buffers, light);
                //?} else {
                /*original.call(dispatcher, entity, x, y + shift, z, yaw, partial, pose, buffers, light);
                *///?}
                return null;
            });
        }
    }
}
