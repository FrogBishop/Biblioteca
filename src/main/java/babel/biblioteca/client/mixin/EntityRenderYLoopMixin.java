package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Renders every entity at the ring layer nearest the camera, and only at that one layer.
 *
 * A render state carries the entity's absolute row, and the renderer draws it at exactly that
 * row. In a Y-looping dimension that is the wrong row whenever the entity is on the far side of
 * the seam: an entity standing at the bottom of the world is drawn 384 blocks below a player at
 * the top, where the ring puts it a few blocks above their head. Terrain does not have this
 * problem because LevelRendererYLoopMixin draws an extra copy, but an entity has only one body
 * and drawing several copies of it would be worse than drawing the wrong one.
 *
 * So the row is moved instead of duplicated: the entity is shifted by whole world heights to the
 * copy closest to the camera, which is the copy the player is actually looking at. The shift is
 * zero for anything within half a world height (that is, everything in the ordinary case), so
 * nothing changes for a dimension that does not loop or for an entity near the camera.
 *
 * Only the render state is moved. The entity's real position, collision, tracking and packets
 * are untouched, and entity tracking in this game is horizontal only, so an entity in the same
 * chunk column is already sent to the client no matter which row it stands on.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderYLoopMixin {

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void biblioteca$renderAtNearestRingLayer(
            Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {

        EntityRenderState state = cir.getReturnValue();
        if (state == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (!LoopingRegistry.isLooping(minecraft.level)) return;

        int period = minecraft.level.getHeight();
        if (period <= 0) return;

        double camY  = minecraft.gameRenderer.mainCamera().position().y;
        long   layer = Math.round((camY - state.y) / period);
        if (layer == 0) return;

        state.y += (double) layer * period;
    }
}
