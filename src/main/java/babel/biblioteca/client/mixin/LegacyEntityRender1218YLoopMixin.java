package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.21.8 entity render offset folding.
 *
 * The 1.21.8 dispatcher dropped the extra float; the Y argument is still the second double and
 * still camera-relative, so folding it by whole periods puts the entity on the nearest layer.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class LegacyEntityRender1218YLoopMixin {

    @ModifyVariable(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDF"
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
