package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * In looping dimensions, folds cloudHeight to the nearest periodic equivalent
 * relative to the player's current Y so the cloud layer is always visible near
 * the player, regardless of which "cycle" they are in.
 *
 * Example: cloudHeight=192, playerY=319, H=384
 *   → n = round((319-192)/384) = round(0.33) = 0 → no change (base layer visible)
 * Example: cloudHeight=192, playerY=-64, H=384
 *   → n = round((-64-192)/384) = round(-0.67) = -1 → 192 + (-1)*384 = -192
 *   (clouds appear 128 blocks below player, visible when looking down)
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return cloudHeight;
        if (!LoopingDimensionCache.isLooping(mc.level.dimension())) return cloudHeight;

        int height = mc.level.getHeight();
        double camY = mc.player.getEyeY();
        int n = (int) Math.round((camY - cloudHeight) / height);
        return cloudHeight + n * height;
    }
}
