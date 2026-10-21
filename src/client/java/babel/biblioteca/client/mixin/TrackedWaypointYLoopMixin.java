package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Places the locator bar's waypoint dots at their ring distance and direction.
 *
 * <p>The bar draws one dot per tracked player, sized from the waypoint's distance to the camera.
 * A waypoint stores an absolute block position, so a player on the other side of the seam measures
 * as a whole world height away: the dot is drawn at the wrong size (the sprite resolution is chosen
 * by distance) and the up/down arrow points the wrong way. Both use the copy of the waypoint
 * nearest the viewer, which is the copy the dot represents.
 */
@Mixin(targets = "net.minecraft.world.waypoints.TrackedWaypoint$Vec3iWaypoint")
public abstract class TrackedWaypointYLoopMixin {

    @Shadow private Vec3i vector;

    @Inject(method = "distanceSquared", at = @At("HEAD"), cancellable = true)
    private void biblioteca$ringDistance(Entity fromEntity, CallbackInfoReturnable<Double> cir) {
        Level level = fromEntity.level();
        if (!LoopingRegistry.isLooping(level)) return;

        int period = level.getHeight();
        if (period <= 0) return;

        Vec3 centre = Vec3.atCenterOf(this.vector);
        cir.setReturnValue(SeamPositions.distanceToSqr(level, fromEntity.position(), centre));
    }

    @ModifyExpressionValue(
            method = "pitchDirectionToCamera",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/waypoints/TrackedWaypoint$Vec3iWaypoint;"
                            + "position(Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/world/waypoints/PartialTickSupplier;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$foldPitchPosition(Vec3 position) {
        Minecraft minecraft = Minecraft.getInstance();
        if (position == null || minecraft.level == null) return position;

        Level level = minecraft.level;
        if (!LoopingRegistry.isLooping(level)) return position;

        return SeamPositions.foldToward(level, position, minecraft.gameRenderer.mainCamera().position().y);
    }
}
