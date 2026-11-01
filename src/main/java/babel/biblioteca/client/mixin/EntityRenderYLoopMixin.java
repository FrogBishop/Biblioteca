package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.Compat;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.EntityMirrors;
import babel.biblioteca.client.EntityCopyState;
import babel.biblioteca.SeamPositions;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps each extracted state in its requested display frame. Ordinary callers use the nearest
 * image; world mirror extraction requests independent states with explicit period offsets.
 * Entity storage, collision and tracking stay canonical, and leash endpoint differences remain
 * periodic in every displayed copy.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderYLoopMixin {

    @Shadow public Camera camera;

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void biblioteca$renderAtNearestRingLayer(
            Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {

        EntityRenderState state = cir.getReturnValue();
        if (state == null) return;

        Level level = Compat.level(entity);
        if (level == null || !LoopingRegistry.isLooping(level)) return;

        int period = Compat.height(level);
        if (period <= 0) return;
        ((EntityCopyState) state).biblioteca$sourceY(state.y);

        // Leashes are drawn from their endpoint difference relative to the rendered body. Keep
        // both endpoints in the same period, including when the body already needs no camera shift.
        if (state.leashStates != null) {
            for (EntityRenderState.LeashState leash : state.leashStates) {
                leash.end = SeamPositions.foldToward(level, leash.end, leash.start.y);
            }
        }

        if (EntityMirrors.isImage(entity)) {
            state.y += EntityMirrors.imageShift();
            return;
        }
        if (this.camera == null) return;
        double camY  = ClientCompat.position(this.camera).y;
        long   layer = Math.round((camY - state.y) / period);
        if (layer == 0) return;

        state.y += (double) layer * period;
    }
}
