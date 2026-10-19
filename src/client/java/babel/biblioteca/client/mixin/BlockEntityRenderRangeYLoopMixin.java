package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Measures the distance to a block entity around the ring instead of straight through the world.
 *
 * Before a block entity is ever extracted, BlockEntityRenderDispatcher asks its renderer whether
 * it is close enough: the default BlockEntityRenderer.shouldRender compares the straight-line
 * distance against getViewDistance, which is 64 blocks. A chest on the far side of the seam is a
 * whole world height away by that measure even though the ring puts it next to the camera, so it
 * is dropped before a render state exists - the same gate EntityShouldRenderYLoopMixin removes
 * for entities.
 *
 * The check is wrapped at the call site rather than inside shouldRender, because renderers can
 * override that method with rules of their own (a beacon measures horizontally only, which is
 * already right for a ring). Wrapping the call covers every renderer without changing any of
 * their rules: the camera's row is moved to the ring layer nearest the block entity, so whatever
 * straight-line distance the renderer computes becomes the distance around the ring.
 *
 * Only the position handed to the check is moved. The render state keeps the real block position,
 * and LevelRendererYLoopMixin folds that when the block entity is finally drawn.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderRangeYLoopMixin {

    @WrapOperation(
            method = "tryExtractRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;"
                            + "shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;"
                            + "Lnet/minecraft/world/phys/Vec3;)Z"))
    private boolean biblioteca$ringViewDistance(
            BlockEntityRenderer<?, ?> renderer, BlockEntity blockEntity, Vec3 cameraPos,
            Operation<Boolean> original) {

        if (original.call(renderer, blockEntity, cameraPos)) return true;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return false;
        if (!LoopingRegistry.isLooping(minecraft.level)) return false;

        int period = minecraft.level.getHeight();
        if (period <= 0) return false;

        double offset = blockEntity.getBlockPos().getY() - cameraPos.y;
        double shift  = (double) Math.round(offset / period) * period;
        if (shift == 0.0) return false;

        return original.call(renderer, blockEntity,
                new Vec3(cameraPos.x, cameraPos.y + shift, cameraPos.z));
    }
}
