package biblioteca.periodicprobe.mixin;

import biblioteca.periodicprobe.PeriodicEffectsChecks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class PeriodicParticleObserver {
    @Inject(method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V", at = @At("HEAD"))
    private void observed(QuadParticleRenderState state, Quaternionf rotation, float x, float y, float z, float partial, CallbackInfo ci) {
        PeriodicEffectsChecks.rendered((Particle) (Object) this, y);
    }
}
