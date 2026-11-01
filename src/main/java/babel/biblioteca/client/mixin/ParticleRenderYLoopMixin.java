package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import babel.biblioteca.client.ClientCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Pick a display copy every frame without changing a living particle's age or trajectory. */
@Mixin(SingleQuadParticle.class)
public abstract class ParticleRenderYLoopMixin extends Particle {
    protected ParticleRenderYLoopMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @ModifyExpressionValue(
            //? if >=26.1 {
            method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lnet/minecraft/client/Camera;Lorg/joml/Quaternionf;F)V",
            //?} else if >=1.21.9 {
            /*method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/QuadParticleRenderState;Lnet/minecraft/client/Camera;Lorg/joml/Quaternionf;F)V",
            *///?} else if >=1.21 {
            /*method = "renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;Lorg/joml/Quaternionf;F)V",
            *///?} else {
            /*method = "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
            *///?}
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(DDD)D", ordinal = 1))
    private double biblioteca$nearestLivingParticle(double y, @Local(argsOnly = true) Camera camera) {
        if (this.level == null || !LoopingRegistry.isLooping(this.level)) return y;
        return SeamPositions.foldToward(y, ClientCompat.position(camera).y, Compat.height(this.level));
    }
}
