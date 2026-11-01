package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.EntityMirrors;
import babel.biblioteca.SeamPositions;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
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

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/entity/Entity;)D", at = @At("HEAD"), cancellable = true)
    private void biblioteca$ringDistance(Entity entity, CallbackInfoReturnable<Double> cir) {
        Camera camera = this.camera;
        if (camera == null || entity == null) return;

        Level level = Compat.level(entity);
        if (level == null || !LoopingRegistry.isLooping(level)) return;

        int period = Compat.height(level);
        if (period <= 0) return;

        Vec3 cameraPosition = ClientCompat.position(camera);
        if (EntityMirrors.isImage(entity)) {
            cir.setReturnValue(EntityMirrors.distanceToSqr(entity, cameraPosition));
            return;
        }
        double y = SeamPositions.foldToward(entity.getY(), cameraPosition.y, period);
        cir.setReturnValue(cameraPosition.distanceToSqr(entity.getX(), y, entity.getZ()));
    }

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void biblioteca$visibleImages(Entity entity, Frustum frustum, double x, double y, double z,
            //? if >=26.3 {
            float partial,
            //?}
            CallbackInfoReturnable<Boolean> cir) {
        if (EntityMirrors.isCulling()) return;
        EntityMirrors.remember(camera, frustum);
        if (cir.getReturnValue() || !EntityMirrors.enabled(Compat.level(entity))) return;
        //? if <26.3 {
        /*float partial = 0;
        *///?}
        if (EntityMirrors.anyVisible((EntityRenderDispatcher) (Object) this, entity, frustum, x, y, z, partial)) cir.setReturnValue(true);
    }
}
