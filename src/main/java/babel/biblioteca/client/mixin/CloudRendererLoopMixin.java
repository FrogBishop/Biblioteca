package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Selects the nearest cloud layer using the native render entry point of each release. */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererLoopMixin {

    @ModifyVariable(
            //? if >=26.3 {
            method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            //?} else if >=26.1 {
            /*method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            *///?} else if >=1.21.11 {
            /*method = "render(ILnet/minecraft/client/CloudStatus;FLnet/minecraft/world/phys/Vec3;JF)V",
            *///?} else if >=1.21.5 {
            /*method = "render(ILnet/minecraft/client/CloudStatus;FLnet/minecraft/world/phys/Vec3;F)V",
            *///?} else {
            /*method = "render(ILnet/minecraft/client/CloudStatus;FLorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;F)V",
            *///?}

            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private float biblioteca$foldCloudHeight(float cloudHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return cloudHeight;
        if (!LoopingRegistry.isLooping(mc.level)) return cloudHeight;

        int height = Compat.height(mc.level);
        double camY = mc.player.getEyeY();
        int n = (int) Math.round((camY - cloudHeight) / height);
        return cloudHeight + n * height;
    }
}
