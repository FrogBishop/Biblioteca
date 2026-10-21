package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
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
 * Draws a beacon beam as the ring-wide column it is.
 *
 * Vanilla draws every beam section at its recorded height except the last one, which it stretches
 * to a hardcoded 2048 so the beam seems to run off into the sky. A ring has no sky to run into:
 * the column above a beacon wraps around and arrives under itself, so the beam that leaves the
 * top of the world comes back up under the player, crosses them, and ends once per world height
 * where it meets its own base. A beam blocked a few blocks up must also stop there instead of
 * being stretched past the obstruction.
 *
 * The scanned length stops at the first obstruction or after one period. Copies are selected
 * using the vertical render range, subject to the total copy cap below.
 *
 * LevelRendererYLoopMixin deliberately leaves beacon render states unfolded so that these copies
 * are counted from the real block position: no other code has to agree on a fold offset.
 */
@Mixin(BeaconRenderer.class)
public abstract class BeaconBeamRenderYLoopMixin {

    /** Total cap for the inclusive range of beam copies, including the source when it is in range. */
    private static final int MAX_COPIES = 9;

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void biblioteca$drawPeriodicBeam(
            BeaconRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (!LoopingRegistry.isLooping(minecraft.level)) return;

        int period = minecraft.level.getHeight();
        if (period <= 0) return;

        int beamHeight = 0;
        for (BeaconRenderState.Section section : state.sections) {
            beamHeight += section.height();
        }

        if (beamHeight <= 0) {
            // Nothing to draw, and the 2048 stretch must not happen either.
            ci.cancel();
            return;
        }

        double camY    = camera.pos.y;
        double reach   = minecraft.options.getEffectiveRenderDistance() * 16.0;
        int    originY = state.blockPos.getY();
        double beamTop = (double) originY + beamHeight;

        // Round outward to cover the vertical view range, then cap the inclusive copy count.
        int lowest  = (int) Math.floor((camY - reach - beamTop) / period);
        int highest = (int) Math.ceil((camY + reach - originY) / period);

        lowest = Math.max(lowest, highest - MAX_COPIES + 1);

        for (int copy = lowest; copy <= highest; copy++) {
            poseStack.pushPose();
            poseStack.translate(0.0, (double) copy * period, 0.0);
            biblioteca$submitSections(state, poseStack, collector);
            poseStack.popPose();
        }

        ci.cancel();
    }

    private static void biblioteca$submitSections(
            BeaconRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {

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
    }
}
