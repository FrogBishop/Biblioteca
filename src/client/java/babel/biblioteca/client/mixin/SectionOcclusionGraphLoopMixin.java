package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
 * Teaches the section BFS that the Y axis wraps around, so it reaches the sections on the
 * far side of the seam.
 *
 * Vanilla walks the section graph one neighbour at a time and stops at the edges of the
 * world. Two places need help:
 *
 * 1. Neighbour lookup. getRelativeFrom() answers null for a vertical neighbour outside the
 *    world, so the walk simply ends at the first/last section. The wrapped section is the
 *    real neighbour in a ring, so it is substituted when vanilla returns null. Note that
 *    minSectionY()/maxSectionY() are both inclusive section indices.
 *
 * 2. The line of sight test used for sections further than 60 blocks away. It walks a ray
 *    from the far section towards the camera and gives up as soon as the ray leaves the
 *    world vertically, treating everything past the seam as unreachable. In a ring that ray
 *    continues through the seam, so the vertical bounds check is defused and the section
 *    looked up at each step is wrapped as well.
 *
 * Whether such a section then counts as visible is decided by FrustumYLoopMixin, which
 * culls by ring position.
 */
@Mixin(value = SectionOcclusionGraph.class, priority = 900)
public abstract class SectionOcclusionGraphLoopMixin {

    @Shadow private ViewArea viewArea;

    private static boolean biblioteca$looping() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && LoopingDimensionCache.isLooping(minecraft.level.dimension());
    }

    @Inject(
            method = "getRelativeFrom(JLnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;Lnet/minecraft/core/Direction;)"
                    + "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;",
            at = @At("RETURN"),
            cancellable = true,
            require = 1)
    private void biblioteca$wrapNeighbour(
            long cameraSectionNode,
            SectionRenderDispatcher.RenderSection section,
            Direction direction,
            CallbackInfoReturnable<SectionRenderDispatcher.RenderSection> cir) {

        if (cir.getReturnValue() != null) return;
        if (direction != Direction.UP && direction != Direction.DOWN) return;
        if (!biblioteca$looping()) return;
        if (this.viewArea == null) return;

        int minSectionY = this.viewArea.minSectionY();
        int maxSectionY = this.viewArea.maxSectionY();
        int sections = maxSectionY - minSectionY + 1;

        BlockPos origin = section.getRenderOrigin();
        int sectionX = origin.getX() >> 4;
        int sectionY = origin.getY() >> 4;
        int sectionZ = origin.getZ() >> 4;

        int neighbourY = direction == Direction.UP ? sectionY + 1 : sectionY - 1;

        // Vanilla also answers null for a neighbour that is only out of view distance;
        // those are not ours to fix.
        if (neighbourY >= minSectionY && neighbourY <= maxSectionY) return;

        int wrappedY = Math.floorMod(neighbourY - minSectionY, sections) + minSectionY;
        if (wrappedY == sectionY) return;

        SectionRenderDispatcher.RenderSection wrapped = this.viewArea.getRenderSectionAt(
                new BlockPos(sectionX << 4, wrappedY << 4, sectionZ << 4));
        if (wrapped == null) return;

        cir.setReturnValue(wrapped);
    }

    /** The line of sight ray may pass through the seam instead of ending at the world edge. */
    @WrapOperation(
            method = "runUpdates",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;maxY()I"),
            require = 1)
    private int biblioteca$noCeiling(ViewArea viewArea, Operation<Integer> original) {
        return biblioteca$looping() ? Integer.MAX_VALUE : original.call(viewArea);
    }

    @WrapOperation(
            method = "runUpdates",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;minY()I"),
            require = 1)
    private int biblioteca$noFloor(ViewArea viewArea, Operation<Integer> original) {
        return biblioteca$looping() ? Integer.MIN_VALUE : original.call(viewArea);
    }

    @WrapOperation(
            method = "runUpdates",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ViewArea;getRenderSectionAt(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;"),
            require = 1)
    private SectionRenderDispatcher.RenderSection biblioteca$wrapRayLookup(
            ViewArea viewArea, BlockPos pos, Operation<SectionRenderDispatcher.RenderSection> original) {

        if (!biblioteca$looping()) {
            return original.call(viewArea, pos);
        }

        int minY   = viewArea.minY();
        int height = viewArea.maxY() - minY + 1;
        int y      = pos.getY();

        BlockPos wrapped = (y >= minY && y < minY + height)
                ? pos
                : new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
        return original.call(viewArea, wrapped);
    }
}