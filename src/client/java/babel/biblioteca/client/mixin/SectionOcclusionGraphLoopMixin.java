package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingClientSettings;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.SeamCull;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Teaches the section graph that the Y axis wraps around, so the terrain on the far side of
 * the seam is compiled, uploaded and handed to the renderer instead of being dropped as out
 * of sight.
 *
 * A section only reaches the screen if it ends up in LevelRenderer.visibleSections, because
 * that list is what extractSectionDrawGroups walks. Vanilla fills it from an octree that is
 * populated by a BFS over neighbouring sections, and the BFS refuses to cross the world edge
 * three separate times:
 *
 * 1. Neighbour lookup. getRelativeFrom() answers null for a vertical neighbour outside the
 *    world, so the walk ends at the first and last section. The wrapped section is the real
 *    neighbour in a ring, so it is substituted when vanilla returns null. Note that
 *    minSectionY()/maxSectionY() are both inclusive section indices.
 *
 * 2. The line of sight test used for sections further than 60 blocks away. It walks a ray
 *    from the far section towards the camera and gives up as soon as the ray leaves the
 *    world vertically, treating everything past the seam as unreachable. In a ring that ray
 *    continues through the seam, so the vertical bounds check is defused and the section
 *    looked up at each step is wrapped as well.
 *
 * 3. The distance the walk is allowed to cover. getRelativeFrom() also answers null once a
 *    vertical step is further from the camera than the render distance, and the smart
 *    occlusion pass only steps in a direction the source section's geometry can be seen
 *    through - both correct for a world with a floor and a ceiling, both wrong here. The
 *    bottom of the world is neither within the render distance in sections of the top row
 *    nor visible through the rock in between, so no amount of neighbour substitution makes
 *    the BFS reach it.
 *
 * The first two are patched directly. The third is not worth fighting: the BFS is an
 * occlusion heuristic, and the whole point of a mirror copy is that the thing being drawn
 * is *not* occluded at its own position. addSectionsInFrustum therefore appends the far-side
 * sections the walk missed, which is what biblioteca$includeFarSeamSources does.
 *
 * That append is exact, not a nearest-row heuristic: for every source section it tests all the
 * period offsets that can fall inside the render distance. A height-16 world therefore gets all
 * the copies that fit, not just the two nearest ones. The section graph's own FrustumYLoopMixin
 * still answers for the nearest ring image; that is enough for the octree and for entities, but
 * not for enumerating a short world's many visible layers.
 */
@Mixin(value = SectionOcclusionGraph.class, priority = 900)
public abstract class SectionOcclusionGraphLoopMixin {

    @Shadow private ViewArea viewArea;

    // Reused between frustum updates: the far-seam scan sees every section of every candidate
    // row, so a fresh set per call was avoidable churn.
    @Unique private LongOpenHashSet biblioteca$presentScratch;

    private static boolean biblioteca$looping() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && LoopingRegistry.isLooping(minecraft.level);
    }

    @Inject(
            method = "getRelativeFrom(JLnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;Lnet/minecraft/core/Direction;)"
                    + "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;",
            at = @At("RETURN"),
            cancellable = true)
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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;maxY()I"))
    private int biblioteca$noCeiling(ViewArea viewArea, Operation<Integer> original) {
        return biblioteca$looping() ? Integer.MAX_VALUE : original.call(viewArea);
    }

    @WrapOperation(
            method = "runUpdates",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;minY()I"))
    private int biblioteca$noFloor(ViewArea viewArea, Operation<Integer> original) {
        return biblioteca$looping() ? Integer.MIN_VALUE : original.call(viewArea);
    }

    @WrapOperation(
            method = "runUpdates",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ViewArea;getRenderSectionAt(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;"))
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

    /**
     * Appends the sections on the far side of the seam to the visible set.
     *
     * Only a section that made it into visibleSections gets its geometry extracted, and only
     * extracted geometry gets a mirror draw. The BFS cannot deliver the far side (see the
     * class comment), so the set is completed here instead, from the sections the camera can
     * actually see through the ring.
     *
     * "Can see" is asked with the ordinary frustum test, which FrustumYLoopMixin answers for
     * a box's periodic twin as well as for the box itself. The same question that keeps a
     * row near the camera therefore also keeps the row on the other end of the ring, which is
     * exactly the source set the mirror needs.
     *
     * Only rows whose copy could reach the frustum are scanned: a copy is drawn at some whole
     * multiple of the world height away, and the scan band is limited to the render distance
     * (plus the preload slack) around the camera, so a row outside that band cannot produce
     * anything visible however many periods it is shifted by.
     */
    @Inject(method = "addSectionsInFrustum", at = @At("TAIL"))
    private void biblioteca$includeFarSeamSources(
            Frustum frustum,
            List<SectionRenderDispatcher.RenderSection> visibleSections,
            List<SectionRenderDispatcher.RenderSection> nearbyVisibleSections,
            CallbackInfo ci) {
        biblioteca$addFarSeamSources(frustum, visibleSections);
    }

    /**
     * Adds the source sections whose periodic copies are visible.
     *
     * There is exactly one source section per world row; every row above or below it is a copy.
     * For each source section, the copies that can be within reach are a contiguous range of
     * period offsets. Testing that range directly is exact for any world height and render
     * distance: a height-16 world gets every copy that fits, not just the two nearest ones.
     *
     * Source sections that are already visible are skipped entirely. That matters most for short
     * worlds: once the one source row is in the set, the draw path will repeat it everywhere,
     * and there is no reason to scan its copy rows again.
     *
     * The XZ loop is bounded by the view area's radius: a copy outside the loaded columns cannot
     * be rendered even if its Y row is in view. The frustum test is allocation-free because this
     * runs for every loaded section whenever the section graph updates.
     */
    private void biblioteca$addFarSeamSources(
            Frustum frustum, List<SectionRenderDispatcher.RenderSection> visibleSections) {

        if (this.viewArea == null) return;
        if (!biblioteca$looping()) return;

        int sectionCount = this.viewArea.sectionCount();
        if (sectionCount <= 0) return;

        int radius        = this.viewArea.getViewDistance();
        int minSectionY   = this.viewArea.minSectionY();
        double period     = (double) sectionCount * 16.0;
        double reach      = (double) radius * 16.0;
        double camY       = frustum.getCamY();

        // Preload: widen the test by the configured number of blocks. Sections in that shell are
        // compiled ahead of time instead of the moment they come into view. Zero means "only what
        // is actually in view".
        double slack  = LoopingClientSettings.preloadBlocks();
        double search = reach + slack;

        LongOpenHashSet present = this.biblioteca$presentScratch;
        if (present == null) {
            present = new LongOpenHashSet();
            this.biblioteca$presentScratch = present;
        } else {
            present.clear();
        }
        for (SectionRenderDispatcher.RenderSection section : visibleSections) {
            present.add(section.getSectionNode());
        }

        SectionPos camera = this.viewArea.getCameraSectionPos();
        ViewAreaAccessor accessor = (ViewAreaAccessor) this.viewArea;

        for (int sourceSectionY = minSectionY;
             sourceSectionY < minSectionY + sectionCount;
             sourceSectionY++) {

            double sourceBaseY = (double) sourceSectionY * 16.0;
            double sourceCentreY = sourceBaseY + 8.0;

            // copyCentreY = sourceCentreY + step * period must stay within search of the camera.
            int lowestStep  = (int) Math.ceil((camY - search - sourceCentreY) / period);
            int highestStep = (int) Math.floor((camY + search - sourceCentreY) / period);

            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                int sectionX = camera.x() + offsetX;
                double minX = (double) sectionX * 16.0;
                double maxX = minX + 16.0;

                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    int sectionZ = camera.z() + offsetZ;
                    long node = SectionPos.asLong(sectionX, sourceSectionY, sectionZ);
                    if (present.contains(node)) continue;

                    SectionRenderDispatcher.RenderSection section =
                            accessor.biblioteca$getRenderSection(node);
                    if (section == null) continue;

                    double minZ = (double) sectionZ * 16.0;
                    double maxZ = minZ + 16.0;

                    for (int step = lowestStep; step <= highestStep; step++) {
                        double minY = sourceBaseY + (double) step * period;
                        double maxY = minY + 16.0;

                        if (!SeamCull.plainlyVisible(frustum,
                                minX - slack, minY - slack, minZ - slack,
                                maxX + slack, maxY + slack, maxZ + slack)) continue;

                        visibleSections.add(section);
                        present.add(node);
                        break;
                    }
                }
            }
        }
    }
}
