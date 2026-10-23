package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.21.1 entity render offset folding.
 *
 * The 1.21.1 dispatcher still takes the entity position minus the camera position, but its
 * signature has an extra float after the partial tick. The Y argument is the second double.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class LegacyEntityRender1211YLoopMixin {

    @ModifyVariable(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1)
    private double biblioteca$foldRenderY(double y) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) return y;

        int period = Compat.height(minecraft.level);
        if (period <= 0) return y;

        return y - (double) Math.round(y / period) * period;
    }
}
