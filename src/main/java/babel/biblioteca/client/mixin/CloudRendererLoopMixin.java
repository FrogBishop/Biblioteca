package babel.biblioteca.client.mixin;

import babel.biblioteca.client.CloudFold;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 26.3 hands CloudRenderer.prepare the absolute cloud height and builds the mesh
 * relative to the camera, so folding that float onto the periodic copy nearest
 * the player is all the cloud pass needs.
 *
 * Signature: prepare(int cloudColor, CloudStatus, float cloudHeight,
 *                    int cloudRange, Vec3 cameraPos, long gameTime, float partialTick)
 * cloudHeight is the first float parameter (ordinal = 0 among float args).
 */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererLoopMixin {

    @ModifyVariable(
            method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private float biblioteca$foldCloudHeight(float cloudHeight) {
        return CloudFold.nearest(cloudHeight);
    }
}
