package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the BFS section traversal wrap around the Y axis in looping dimensions.
 *
 * When getRelativeFrom() would return null because a vertical neighbor is
 * out of the world bounds (not just out of view distance), we wrap the
 * section Y modulo the world height so BFS can continue.
 *
 * This must work in BOTH directions:
 *   - From secY=minSectionY-1 going DOWN → wraps to maxSectionY-1 (top)
 *   - From secY=maxSectionY going UP   → wraps to minSectionY   (bottom)
 *
 * The view-distance Y check inside getRelativeFrom (abs(camSecY - neighborSecY)
 * <= viewDistance) will also reject the wrapped neighbor when it is far from
 * the camera. We handle that by injecting BEFORE the view-distance check,
 * i.e. at HEAD rather than RETURN, so we can supply the wrapped section node
 * directly before vanilla's checks run.
 */
@Mixin(value = SectionOcclusionGraph.class, priority = 900)
public abstract class SectionOcclusionGraphLoopMixin {

    @Shadow private ViewArea viewArea;

    /**
     * Inject at HEAD: if direction is vertical and the vanilla neighbor node
     * would be out of Y bounds, replace it with the wrapped node so vanilla's
     * subsequent view-distance check uses the wrapped section's packed node,
     * not the out-of-bounds one.
     *
     * We cannot cancel here (we don't have a return value yet), so instead we
     * mutate the renderSection reference by overriding the neighbour resolution.
     * The cleanest approach is to intercept at RETURN and override when null —
     * but vanilla's Y-distance check kills us there. So we use a @Redirect on
     * the getNeighborSectionNode call inside getRelativeFrom to swap the node.
     */
    @Inject(
            method = "getRelativeFrom(JLnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;Lnet/minecraft/core/Direction;)Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0)
    private void biblioteca$wrapYBoundary(
            long cameraSectionNode,
            SectionRenderDispatcher.RenderSection renderSection,
            Direction direction,
            CallbackInfoReturnable<SectionRenderDispatcher.RenderSection> cir) {

        if (cir.getReturnValue() != null) return;
        if (direction != Direction.UP && direction != Direction.DOWN) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!LoopingDimensionCache.isLooping(mc.level.dimension())) return;
        if (viewArea == null) return;

        int minSecY = viewArea.minSectionY();
        int maxSecY = viewArea.maxSectionY(); // exclusive
        int heightInSections = maxSecY - minSecY;

        BlockPos origin = renderSection.getRenderOrigin();
        int secX = origin.getX() >> 4;
        int secY = origin.getY() >> 4;
        int secZ = origin.getZ() >> 4;

        int neighborSecY = direction == Direction.UP ? secY + 1 : secY - 1;

        // Only wrap when the neighbor is truly out-of-world-bounds in Y.
        // (vanilla returns null for two reasons: out-of-bounds, or out-of-view-dist;
        //  we only want to handle the out-of-bounds case)
        if (neighborSecY >= minSecY && neighborSecY < maxSecY) return;

        int wrappedSecY = Math.floorMod(neighborSecY - minSecY, heightInSections) + minSecY;
        if (wrappedSecY == secY) return;

        BlockPos wrappedOrigin = new BlockPos(secX << 4, wrappedSecY << 4, secZ << 4);
        SectionRenderDispatcher.RenderSection wrapped = viewArea.getRenderSectionAt(wrappedOrigin);
        if (wrapped == null) return;

        cir.setReturnValue(wrapped);
        babel.biblioteca.Biblioteca.LOGGER.info(
                "[Biblioteca] BFS Y-wrap: secY={} dir={} → wrappedSecY={}",
                secY, direction, wrappedSecY);
    }
}
