package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Repeats the classic terrain pass at whole world-height offsets.
 *
 * Before 26.3 the renderer walks LevelRenderer.visibleSections and calls
 * renderSectionLayer(type, cameraX, cameraY, cameraZ, ...) once per layer. The camera Y is
 * only used to build the per-section CHUNK_OFFSET uniform, so calling the same method again
 * with y + step * height draws the same compiled section buffers one period higher or lower.
 * No mesh is copied and no draw list is rebuilt.
 *
 * Only the terrain render types are repeated. Lines, debug overlays and the other passes that
 * also go through renderSectionLayer stay single-layer.
 */
@Mixin(LevelRenderer.class)
public abstract class LegacyLevelRendererYLoopMixin {

    @WrapOperation(
            method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;"
                            + "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDD"
                            + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void biblioteca$repeatTerrainLayer(
            LevelRenderer renderer, RenderType type, double x, double y, double z,
            Matrix4f projection, Matrix4f frustum,
            Operation<Void> original) {

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) {
            original.call(renderer, type, x, y, z, projection, frustum);
            return;
        }

        if (!biblioteca$isTerrain(type)) {
            original.call(renderer, type, x, y, z, projection, frustum);
            return;
        }

        int period = minecraft.level.getHeight();
        if (period <= 0) {
            original.call(renderer, type, x, y, z, projection, frustum);
            return;
        }

        double viewDistanceBlocks = minecraft.options.getEffectiveRenderDistance() * 16.0;
        int maxSteps = (int) Math.ceil(viewDistanceBlocks / period) + 1;

        for (int step = -maxSteps; step <= maxSteps; step++) {
            original.call(renderer, type, x, y + (double) step * period, z, projection, frustum);
        }
    }

    private static boolean biblioteca$isTerrain(RenderType type) {
        return type == RenderType.solid()
                || type == RenderType.cutoutMipped()
                || type == RenderType.cutout()
                || type == RenderType.translucent()
                || type == RenderType.tripwire();
    }
}
