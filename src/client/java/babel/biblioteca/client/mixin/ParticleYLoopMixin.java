package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Moves a particle spawn onto the ring layer nearest the camera.
 *
 * Client-side particles - potion swirls, totem, firework, block and ambient effects - are spawned
 * at the entity's or block's real row. In a looping dimension that row can be a whole world height
 * away from the camera, and ClientLevel.doAddParticle drops anything further than 32 blocks from
 * the camera before it is created. An effect on a player on the other side of the seam was
 * therefore discarded while the player themselves was drawn at the near layer by
 * EntityRenderYLoopMixin, so the body was visible and its particles were not.
 *
 * Folding by whole periods is a no-op for anything within half a world height, which covers
 * ordinary particles and the server broadcasts that SeamEffects already folded for this listener.
 */
@Mixin(ClientLevel.class)
public abstract class ParticleYLoopMixin {

    @ModifyVariable(method = "doAddParticle", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double biblioteca$nearestParticleLayer(double y) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return y;
        if (!LoopingRegistry.isLooping(minecraft.level)) return y;

        int period = minecraft.level.getHeight();
        if (period <= 0) return y;

        double cameraY = minecraft.gameRenderer.mainCamera().position().y;
        long layer = Math.round((cameraY - y) / period);
        if (layer == 0L) return y;
        return y + (double) layer * period;
    }
}
