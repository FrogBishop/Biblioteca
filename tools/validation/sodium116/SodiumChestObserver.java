package biblioteca.sodiumtest.mixin;

import biblioteca.sodiumtest.SodiumProbe;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestRenderer.class)
public abstract class SodiumChestObserver {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("HEAD"))
    private void observe(BlockEntity chest, float delta, PoseStack pose, MultiBufferSource buffers,
                         int light, int overlay, CallbackInfo ci) throws Exception {
        if (!chest.getBlockPos().equals(new net.minecraft.core.BlockPos(90, 0, -52))) return;
        var matrix = pose.last().pose();
        var y = matrix.getClass().getDeclaredField("m13"); y.setAccessible(true);
        SodiumProbe.chestRendered(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y, y.getFloat(matrix));
    }
}
