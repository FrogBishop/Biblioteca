package biblioteca.periodicprobe.mixin;

import biblioteca.periodicprobe.PeriodicVisualChecks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class VisualExtractionObserver {
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void begin(Camera camera, Frustum frustum, DeltaTracker delta, LevelRenderState output, CallbackInfo ci) {
        PeriodicVisualChecks.begin();
    }
    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void finish(Camera camera, Frustum frustum, DeltaTracker delta, LevelRenderState output, CallbackInfo ci) {
        PeriodicVisualChecks.entities(camera, output.entityRenderStates);
    }
}
