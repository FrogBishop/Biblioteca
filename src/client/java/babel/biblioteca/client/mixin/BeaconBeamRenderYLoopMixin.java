package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a beacon beam only as far as its sections reach, in looping dimensions.
 *
 * Vanilla draws every beam section at its recorded height except the last one, which is drawn
 * with a hardcoded 2048 so the beam appears to run off into the sky. That extension is the
 * reason a beam goes straight through the first opaque block above it: the sections stop at the
 * obstruction, but the last one is stretched past it anyway.
 *
 * A ring has no sky to stretch into, so the whole beam is drawn at its scanned length instead,
 * and BeaconBlockEntityYLoopMixin makes that length run around the ring. A beam therefore ends
 * exactly where its column is blocked, and a beam that wraps across the seam ends one world
 * height up, which is the row below the beacon.
 */
@Mixin(BeaconRenderer.class)
public abstract class BeaconBeamRenderYLoopMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void biblioteca$drawFiniteBeam(
            BeaconRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (!LoopingDimensionCache.isLooping(minecraft.level.dimension())) return;

        int beamStart = 0;

        for (int i = 0; i < state.sections.size(); i++) {
            BeaconRenderState.Section section = state.sections.get(i);
            BeaconRenderer.submitBeaconBeam(
                    poseStack,
                    collector,
                    BeaconRenderer.BEAM_LOCATION,
                    1.0F,
                    state.animationTime,
                    beamStart,
                    section.height(),
                    section.color(),
                    BeaconRenderer.SOLID_BEAM_RADIUS * state.beamRadiusScale,
                    BeaconRenderer.BEAM_GLOW_RADIUS * state.beamRadiusScale
            );
            beamStart += section.height();
        }

        ci.cancel();
    }
}
