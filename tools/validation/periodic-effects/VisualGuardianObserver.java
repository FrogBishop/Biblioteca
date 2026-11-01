package biblioteca.periodicprobe.mixin;

import biblioteca.periodicprobe.PeriodicVisualChecks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.GuardianRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuardianRenderer.class)
public abstract class VisualGuardianObserver {
    @Inject(method = "renderBeam", at = @At("HEAD"))
    private static void probe(PoseStack pose, SubmitNodeCollector collector, Vec3 vector, float time, float scale, float offset, CallbackInfo ci) {
        PeriodicVisualChecks.guardianBeam(vector);
    }
}
