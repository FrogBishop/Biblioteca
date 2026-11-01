package babel.biblioteca.client.mixin;

import babel.biblioteca.client.CloudLayers;
import babel.biblioteca.client.ClientCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexBuffer;
//? if >=1.19.3 {
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix4f;
*///?}
//? if >=1.17 {
import net.minecraft.client.renderer.ShaderInstance;
//?}
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/** Reuses the native cloud VBO for all copies within each native depth/color pass. */
@Mixin(LevelRenderer.class)
public abstract class LegacyClouds1211Mixin {
    @Shadow private boolean generateClouds;
    @Unique private final CloudLayers biblioteca$layers = new CloudLayers();

    @ModifyExpressionValue(
            method = "renderClouds",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"))
    private float biblioteca$nearestCloudLayer(float cloudHeight, @Local(argsOnly = true, ordinal = 1) double cameraY) {
        boolean previous = biblioteca$layers.multiple();
        float height = biblioteca$layers.prepare(cloudHeight, cameraY, ClientCompat.renderDistance());
        if (previous != biblioteca$layers.multiple()) generateClouds = true;
        return height;
    }

    @ModifyConstant(method = "buildClouds", constant = @Constant(floatValue = -5.0f))
    private float biblioteca$bottomFace(float value) { return biblioteca$layers.multiple() ? -Float.MAX_VALUE : value; }

    @ModifyConstant(method = "buildClouds", constant = @Constant(floatValue = 5.0f))
    private float biblioteca$topFace(float value) { return biblioteca$layers.multiple() ? Float.MAX_VALUE : value; }

    @WrapOperation(method = "renderClouds", at = @At(value = "INVOKE",
            //? if >=1.19.3 {
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
            //?} else if >=1.17 {
            /*target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lcom/mojang/math/Matrix4f;Lcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
            *///?} else {
            /*target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw(Lcom/mojang/math/Matrix4f;I)V"))
            *///?}
    private void biblioteca$drawCloudCopies(VertexBuffer buffer, Matrix4f view,
            //? if >=1.17 {
            Matrix4f projection, ShaderInstance shader,
            //?} else {
            /*int mode,
            *///?}
            Operation<Void> original) {
        for (int i = 0; i < biblioteca$layers.count(); i++) {
            //? if >=1.19.3 {
            Matrix4f shifted = new Matrix4f(view).translate(0, biblioteca$layers.shift(i), 0);
            //?} else {
            /*Matrix4f shifted = view.copy();
            shifted.multiply(Matrix4f.createTranslateMatrix(0, biblioteca$layers.shift(i), 0));
            *///?}
            //? if >=1.17 {
            original.call(buffer, shifted, projection, shader);
            //?} else {
            /*original.call(buffer, shifted, mode);
            *///?}
        }
    }
}
