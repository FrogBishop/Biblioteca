package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measures the distance to an entity around the ring.
 *
 * <p>{@code EntityRenderer.extractNameTags} hides the name of anything further away than 64 blocks,
 * and that distance is this method. An entity on the far side of the seam is a whole world height
 * away by that measure even though it is drawn beside the camera - which is why another player's
 * name tag disappears exactly when they cross. The same value also scales entity shadows and is
 * stored on the render state for anything else that needs it, so it is corrected at the source
 * rather than in the name-tag path alone.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherYLoopMixin {

    @Shadow public Camera camera;

    @Inject(method = "distanceToSqr", at = @At("HEAD"), cancellable = true)
    private void biblioteca$ringDistance(Entity entity, CallbackInfoReturnable<Double> cir) {
        Camera camera = this.camera;
        if (camera == null || entity == null) return;

        Level level = entity.level();
        if (level == null || !LoopingRegistry.isLooping(level)) return;

        int period = level.getHeight();
        if (period <= 0) return;

        Vec3 cameraPosition = camera.position();
        double y = SeamPositions.foldToward(entity.getY(), cameraPosition.y, period);
        cir.setReturnValue(cameraPosition.distanceToSqr(entity.getX(), y, entity.getZ()));
    }
}
