package babel.biblioteca.client.mixin;

import babel.biblioteca.client.CloudLayers;
import babel.biblioteca.client.ClientCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//? if >=1.21.6 {
import com.mojang.blaze3d.systems.RenderSystem;
//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
//?} else {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
*///?}
//?} else if >=1.21.5 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/** Prepare one two-sided mesh and one CloudInfo; copies only allocate independent transforms. */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererLoopMixin {
    @Unique private final CloudLayers biblioteca$layers = new CloudLayers();

    @ModifyVariable(
            //? if >=26.3 {
            method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            //?} else if >=26.1 {
            /*method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            *///?} else if >=1.21.11 {
            /*method = "render(ILnet/minecraft/client/CloudStatus;FLnet/minecraft/world/phys/Vec3;JF)V",
            *///?} else if >=1.21.5 {
            /*method = "render(ILnet/minecraft/client/CloudStatus;FLnet/minecraft/world/phys/Vec3;F)V",
            *///?} else {
            /*method = "render(ILnet/minecraft/client/CloudStatus;FLorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;F)V",
            *///?}
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float biblioteca$planLayers(float height, @Local(argsOnly = true) Vec3 camera
            //? if >=26.1 {
            , @Local(argsOnly = true, ordinal = 1) int range
            //?}
    ) {
        //? if <26.1 {
        /*int range = ClientCompat.renderDistance();
        *///?}
        return biblioteca$layers.prepare(height, camera.y, range);
    }

    @ModifyExpressionValue(
            //? if >=26.3 {
            method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            //?} else {
            /*method = "render",
            *///?}
            at = {
                @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/CloudRenderer$RelativeCameraPos;ABOVE_CLOUDS:Lnet/minecraft/client/renderer/CloudRenderer$RelativeCameraPos;"),
                @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/CloudRenderer$RelativeCameraPos;BELOW_CLOUDS:Lnet/minecraft/client/renderer/CloudRenderer$RelativeCameraPos;")
            }, expect = 2)
    @Coerce private Object biblioteca$bothCloudFaces(@Coerce Object side) {
        // The native INSIDE mesh includes both faces and fits its native capacity calculation.
        return biblioteca$layers.multiple() ? side.getClass().getEnumConstants()[1] : side;
    }

    //? if >=1.21.6 {
    @Unique private final GpuBufferSlice[] biblioteca$transforms = new GpuBufferSlice[CloudLayers.MAX_LAYERS];

    @ModifyExpressionValue(
            //? if >=26.3 {
            method = "render(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
            //?} else {
            /*method = "render",
            *///?}
            at = @At(value = "INVOKE",
            //? if >=26.3 {
            target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
            //?} else if >=26.2 {
            /*target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
            *///?} else if >=1.21.11 {
            /*target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
            *///?} else {
            /*target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;F)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
            *///?}
    private GpuBufferSlice biblioteca$copyTransforms(GpuBufferSlice base) {
        for (int i = 0; i < biblioteca$layers.count(); i++) {
            float shift = biblioteca$layers.shift(i);
            if (shift == 0) { biblioteca$transforms[i] = base; continue; }
            //? if >=26.2 {
            Matrix4f matrix = RenderSystem.getModelViewMatrixCopy().translate(0, shift, 0);
            biblioteca$transforms[i] = RenderSystem.getDynamicUniforms().writeTransform(matrix);
            //?} else {
            /*Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewMatrix()).translate(0, shift, 0);
            biblioteca$transforms[i] = RenderSystem.getDynamicUniforms().writeTransform(matrix,
                    new org.joml.Vector4f(1, 1, 1, 1), new org.joml.Vector3f(), new Matrix4f()
                    //? if <1.21.11 {
                    , 0.0f
                    //?}
            );
            *///?}
        }
        return base;
    }

    @WrapOperation(
            //? if >=26.3 {
            method = "render(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
            //?} else {
            /*method = "render",
            *///?}
            at = @At(value = "INVOKE",
            //? if >=26.3 {
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V"))
            //?} else if >=26.2 {
            /*target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIIII)V"))
            *///?} else {
            /*target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V"))
            *///?}
    private void biblioteca$drawCloudCopies(RenderPass pass, int a, int b, int c, int d,
            //? if >=26.2 {
            int e,
            //?}
            Operation<Void> original) {
        for (int i = 0; i < biblioteca$layers.count(); i++) {
            pass.setUniform("DynamicTransforms", biblioteca$transforms[i]);
            //? if >=26.2 {
            original.call(pass, a, b, c, d, e);
            //?} else {
            /*original.call(pass, a, b, c, d);
            *///?}
        }
    }
    //?} else if >=1.21.5 {
    /*@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CloudRenderer;draw(Lcom/mojang/blaze3d/pipeline/RenderPipeline;FFF)V"))
    private void biblioteca$drawCloudCopies(CloudRenderer renderer, RenderPipeline pipeline, float x, float y, float z, Operation<Void> original) {
        for (int i = 0; i < biblioteca$layers.count(); i++) original.call(renderer, pipeline, x, y + biblioteca$layers.shift(i), z);
    }
    *///?} else {
    /*@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CloudRenderer;drawWithRenderType(Lnet/minecraft/client/renderer/RenderType;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FFF)V"))
    private void biblioteca$drawCloudCopies(CloudRenderer renderer, RenderType type, Matrix4f view, Matrix4f projection, float x, float y, float z, Operation<Void> original) {
        for (int i = 0; i < biblioteca$layers.count(); i++) original.call(renderer, type, view, projection, x, y + biblioteca$layers.shift(i), z);
    }
    *///?}
}
