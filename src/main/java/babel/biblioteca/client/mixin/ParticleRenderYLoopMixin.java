package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Descriptors;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pick a display copy every frame without changing a living particle's age or trajectory. */
// Sodium's overwrites and merged fast callbacks run at priority 1000.
@Mixin(value = SingleQuadParticle.class, priority = 1100)
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
            /*method = {"renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;Lorg/joml/Quaternionf;F)V", "@Desc(sodiumFast)", "@Desc(sodiumRotatedFast)"},
            *///?} else {
            /*method = {"render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V", "@Desc(sodiumFast)"},
            *///?}
            //? if >=1.21.11 {
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;position()Lnet/minecraft/world/phys/Vec3;"))
            //?} else {
            /*at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
            *///?}
    @Descriptors({
            @Desc(id = "sodiumFast", value = "", args = {VertexConsumer.class, Camera.class, float.class, CallbackInfo.class}, max = 1),
            //? if >=1.21 {
            @Desc(id = "sodiumRotatedFast", value = "", args = {VertexConsumer.class, Camera.class, org.joml.Quaternionf.class, float.class, CallbackInfo.class}, max = 1)
            //?}
    })
    private Vec3 biblioteca$nearestLivingParticleCamera(Vec3 camera, @Local(argsOnly = true) float partialTick) {
        if (this.level == null || !LoopingRegistry.isLooping(this.level)) return camera;
        // Select the equivalent camera beside this particle. Sodium may do its interpolation in
        // a private helper after this call, so the simulation fields and the real Camera stay intact.
        double particleY = Mth.lerp(partialTick, this.yo, this.y);
        double y = camera.y + particleY - SeamPositions.foldToward(particleY, camera.y, Compat.height(this.level));
        return y == camera.y ? camera : new Vec3(camera.x, y, camera.z);
    }
}
