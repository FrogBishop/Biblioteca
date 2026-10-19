package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.SeamCull;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Draws the terrain that a Y-looping dimension repeats above and below itself.
 *
 * extractSectionDrawGroups turns the visible sections into one ChunkSectionInfo plus a set of draw
 * commands per layer, with the section index carried in the draw's instance base. A copy of a
 * section is therefore just one more ChunkSectionInfo at a periodic height plus duplicated draws
 * pointing at it, so showing many stacked layers costs draw commands and nothing else - the compiled
 * mesh is shared.
 *
 * Every periodic row within the render distance is submitted, not only the nearest one: with enough
 * render distance the player should see the world repeating, the way an endless vertical ring would
 * look. The two gates are that the row's box is inside the camera frustum and that its centre is
 * within the render distance; the frustum test alone is not enough, because FrustumYLoopMixin
 * answers for a box's periodic twin as well, which would let copies far beyond anything loaded
 * through.
 *
 * The cost is command count, not mesh memory: every copy is one ChunkSectionInfo plus one draw per
 * section layer. A height-16 world at 32 chunks can produce about 65 copies of every visible
 * section. If profiling shows command submission or GPU vertex work dominates, batch copies into
 * instanced draws; do not grow another cache around the command list.
 *
 * Translucency is not sorted here, and that is deliberate. A compiled section mesh carries one quad
 * order baked for one viewpoint; the same mesh is drawn at several periodic heights, so sorting it
 * for one copy breaks every other copy, and no amount of re-sorting the shared mesh can satisfy
 * them all. ImprovedTransparencyYLoopMixin switches a looping dimension to the order-independent
 * path instead, where the shared mesh is correct at every height. This is what fixes water at the
 * seam: the bottom row's water and the mirrored copy of the top row's water meet there, and the
 * copy's quads were ordered for the source row's camera.
 *
 * A copy also starts at full visibility. ChunkSectionInfo.visibility is the source section's chunk
 * fade-in and feeds terrain.fsh's mix toward FogColor; reusing it tinted a copy for the fade
 * duration and showed up as one fog-coloured patch per 16-block section on mirrored terrain.
 *
 * Entities, block entities and clouds stay single-layer (they are far more expensive per copy, and
 * a mirrored mob is indistinguishable from a mob standing on the mirrored terrain); the entity side
 * lives in EntityRenderYLoopMixin and friends.
 *
 * The source sections are the ones SectionOcclusionGraphLoopMixin put in
 * LevelRenderer.visibleSections; on the far side of the seam those are sections the vanilla section
 * graph never walks to.
 */
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class LevelRendererYLoopMixin {

    // Reused across frames: the copy scan used to build a HashMap and an Object[] per draw, which
    // was pure allocation pressure once a short world produced dozens of periodic rows.
    @Unique private int[] biblioteca$copyIndices = new int[0];
    @Unique private boolean[] biblioteca$copyVisible = new boolean[0];

    @Shadow private LevelRenderState levelRenderState;
    @Shadow @Final private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    /**
     * Draws block entities at the ring layer nearest the camera, the same way
     * EntityRenderYLoopMixin handles entities.
     *
     * A block entity render state carries the absolute block position, and submitBlockEntities
     * translates by exactly that position. A beacon on the far side of the seam therefore draws
     * its beam a whole world height away: the beacon's block overhead is the mirrored terrain, so
     * the block is there, but the beam is drawn relative to the block entity and the block entity
     * itself is never mirrored. That is why the beacon shows up and its beam does not.
     *
     * Folding the translation by whole world heights puts the beam back on top of its block, and
     * only the nearest layer is drawn - one copy, matching the entities, because a second copy of
     * a vertical beam is the same beam again.
     */
    @Inject(method = "submitBlockEntities", at = @At("HEAD"), cancellable = true)
    private void biblioteca$submitBlockEntitiesAtNearestLayer(
            PoseStack poseStack, LevelRenderState renderState, SubmitNodeCollector collector, CallbackInfo ci) {

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (!LoopingRegistry.isLooping(minecraft.level)) return;

        int period = minecraft.level.getHeight();
        if (period <= 0) return;

        Vec3 cameraPos = renderState.cameraRenderState.pos;
        double camX = cameraPos.x();
        double camY = cameraPos.y();
        double camZ = cameraPos.z();

        for (BlockEntityRenderState blockEntityState : renderState.blockEntityRenderStates) {
            BlockPos blockPos = blockEntityState.blockPos;

            double offsetY = blockPos.getY() - camY;

            // A beacon beam spans the whole ring, so it cannot be drawn as one folded copy: it
            // has to be repeated once per period and stop at its own base in each copy. Its
            // renderer does that itself, from the real block position, so it is left where it is.
            if (!(blockEntityState instanceof BeaconRenderState)) {
                offsetY -= (double) Math.round(offsetY / period) * period;
            }

            poseStack.pushPose();
            poseStack.translate(blockPos.getX() - camX, offsetY, blockPos.getZ() - camZ);
            this.blockEntityRenderDispatcher.submit(
                    blockEntityState, poseStack, collector, renderState.cameraRenderState);
            poseStack.popPose();
        }

        ci.cancel();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(
            method = "extractSectionDrawGroups(ZLjava/util/List;Ljava/util/Map;)I",
            at = @At("TAIL"))
    private void biblioteca$addSeamCopies(
            boolean respectTranslucentOrder,
            List sectionInfos,
            Map drawGroups,
            CallbackInfoReturnable<Integer> cir) {

        // respectTranslucentOrder belongs to the vanilla signature; the copy path only duplicates
        // draws and never reorders them. Translucency is handled by ImprovedTransparencyYLoopMixin.
        LevelRenderState renderState = this.levelRenderState;
        if (renderState == null || renderState.cameraRenderState == null) return;
        Frustum frustum = renderState.cameraRenderState.cullFrustum;
        if (frustum == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (!LoopingRegistry.isLooping(minecraft.level)) return;

        int sourceCount = sectionInfos.size();
        if (sourceCount == 0) return;

        int period = minecraft.level.getHeight();
        double cameraY = renderState.cameraRenderState.pos.y;
        double viewDistanceBlocks = minecraft.options.getEffectiveRenderDistance() * 16.0;

        // How many periodic copies can possibly fall inside the render distance. With a long
        // render distance this grows, and that is the point: the world is a ring, so a player with
        // enough range should see the world repeating above and below instead of one layer.
        int maxSteps = (int) Math.ceil(viewDistanceBlocks / period) + 1;
        int stride   = maxSteps * 2 + 1;
        long slotCount = (long) sourceCount * (long) stride;
        if (slotCount > Integer.MAX_VALUE) return;
        int slots = (int) slotCount;

        // Reuse the scan buffers. A height-16 world at 32 chunks creates 67 candidate rows per
        // section; allocating an Object[] per draw and a HashMap per frame was the dominant CPU
        // cost of the copy path before this pass.
        if (this.biblioteca$copyIndices.length < slots) {
            this.biblioteca$copyIndices = new int[slots];
        }
        if (this.biblioteca$copyVisible.length < slots) {
            this.biblioteca$copyVisible = new boolean[slots];
        }
        Arrays.fill(this.biblioteca$copyIndices, 0, slots, -1);
        Arrays.fill(this.biblioteca$copyVisible, 0, slots, false);

        // Pass 1: decide which copies are in view. No section info and no draw is created yet, so
        // the second pass only pays for copies that actually produce geometry.
        for (int i = 0; i < sourceCount; i++) {
            DynamicGpuData.ChunkSectionInfo info =
                    (DynamicGpuData.ChunkSectionInfo) sectionInfos.get(i);
            int base = i * stride;

            for (int step = -maxSteps; step <= maxSteps; step++) {
                if (step == 0) continue; // already submitted by the vanilla pass

                int copyY = info.y() + step * period;
                if (biblioteca$worthDrawing(frustum, info.x(), copyY, info.z(),
                        cameraY, viewDistanceBlocks)) {
                    this.biblioteca$copyVisible[base + step + maxSteps] = true;
                }
            }
        }

        // Pass 2: duplicate each original draw once per visible copy. The original draw count is
        // captured before the append loop: adding copies to the same list must not make this walk
        // the copies again. The first draw of a (section, step) pair creates its ChunkSectionInfo;
        // later layers reuse it through biblioteca$copyIndices.
        ChunkSectionLayer[] layers = ChunkSectionLayer.values();
        for (ChunkSectionLayer layer : layers) {
            List<?> groups = (List<?>) drawGroups.get(layer);
            if (groups == null) continue;

            for (int g = 0; g < groups.size(); g++) {
                ChunkDrawGroupAccessor group = (ChunkDrawGroupAccessor) groups.get(g);
                List<DynamicGpuData.IndexedDraw> draws = group.biblioteca$getDraws();
                int originalDrawCount = draws.size();

                for (int d = 0; d < originalDrawCount; d++) {
                    DynamicGpuData.IndexedDraw original = draws.get(d);
                    int sourceIndex = original.baseInstance();
                    if (sourceIndex < 0 || sourceIndex >= sourceCount) continue;

                    DynamicGpuData.ChunkSectionInfo info =
                            (DynamicGpuData.ChunkSectionInfo) sectionInfos.get(sourceIndex);
                    int base = sourceIndex * stride;

                    for (int step = -maxSteps; step <= maxSteps; step++) {
                        if (step == 0) continue;

                        int slot = base + step + maxSteps;
                        if (!this.biblioteca$copyVisible[slot]) continue;

                        int copyIndex = this.biblioteca$copyIndices[slot];
                        if (copyIndex < 0) {
                            // ChunkSectionInfo.visibility feeds terrain.fsh's mix toward FogColor.
                            // It is the source section's chunk fade-in; a copy has no fade history
                            // of its own, so reusing it tinted the copy for the fade duration and
                            // produced one fog-coloured patch per 16-block section. Copies are
                            // fully present from their first frame.
                            copyIndex = sectionInfos.size();
                            sectionInfos.add(new DynamicGpuData.ChunkSectionInfo(
                                    info.x(), info.y() + step * period, info.z(), 1.0F));
                            this.biblioteca$copyIndices[slot] = copyIndex;
                        }

                        draws.add(new DynamicGpuData.IndexedDraw(
                                original.indexCount(),
                                original.instanceCount(),
                                original.firstIndex(),
                                original.baseVertex(),
                                copyIndex));
                    }
                }
            }
        }
    }

    /**
     * True when this row is inside the frustum and close enough to be seen at all.
     *
     * Both halves matter, and the second is easy to forget: the cull frustum's far plane reaches
     * several times the render distance, so geometry well past the point where fog closes in still
     * tests as inside the frustum. Drawing a periodic copy there puts rows on screen that vanilla
     * would never show - standing at y=70 and looking up, a cave from below reappears forty chunks
     * overhead while the boundary layer that ought to be in front of it is too far away to be drawn
     * at all. The copy path already had this clamp; the path that relocates a section onto its twin
     * did not, and that is what produced the floating caves.
     *
     * The plain frustum test is deliberate: the ring-aware one answers yes whenever the box's twin
     * is in view, and a copy's twin is the section being copied, visible by construction.
     */
    private static boolean biblioteca$worthDrawing(
            Frustum frustum, int x, int y, int z,
            double cameraY, double viewDistanceBlocks) {

        double centreY = y + 8.0;
        if (Math.abs(centreY - cameraY) > viewDistanceBlocks) return false;

        return SeamCull.plainlyVisible(frustum,
                x, y, z, x + 16.0, y + 16.0, z + 16.0);
    }
}
