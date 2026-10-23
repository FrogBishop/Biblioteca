package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Folds only the draw translation; source positions stay intact for lighting and beacon copies. */
@Mixin(LevelRenderer.class)
public abstract class BlockEntityPositionYLoopMixin {
    @WrapOperation(method = "submitBlockEntities", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void biblioteca$nearestLayer(PoseStack pose, double x, double y, double z, Operation<Void> original,
            @Local BlockEntityRenderState state) {
        var level = Minecraft.getInstance().level;
        if (!(state instanceof BeaconRenderState) && level != null && LoopingRegistry.isLooping(level)) {
            int period = Compat.height(level);
            if (period > 0) y -= Math.round(y / period) * (double) period;
        }
        original.call(pose, x, y, z);
    }
}
