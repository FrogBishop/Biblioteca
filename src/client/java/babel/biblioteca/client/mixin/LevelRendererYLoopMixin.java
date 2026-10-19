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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
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
 * Translucent sorting is deliberately not handled here. The previous implementation (SeamSortOrder,
 * SectionCompileSortYLoopMixin, SectionResortYLoopMixin and LevelRendererInvoker) has been removed;
 * mirrored sections currently keep whatever ordering vanilla produced for the source section, and a
 * fresh implementation will replace it once the actual artefact is pinned down.
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

        // Which draw commands belong to which section index.
        Map<Integer, List<Object[]>> drawsBySection = new HashMap<>();
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            List<?> groups = (List<?>) drawGroups.get(layer);
            if (groups == null) continue;

            for (int g = 0; g < groups.size(); g++) {
                ChunkDrawGroupAccessor group = (ChunkDrawGroupAccessor) groups.get(g);
                List<DynamicGpuData.IndexedDraw> draws = group.biblioteca$getDraws();

                for (int d = 0; d < draws.size(); d++) {
                    DynamicGpuData.IndexedDraw draw = draws.get(d);
                    int sectionIndex = draw.baseInstance();
                    if (sectionIndex >= 0 && sectionIndex < sourceCount) {
                        drawsBySection
                                .computeIfAbsent(sectionIndex, key -> new ArrayList<>())
                                .add(new Object[]{group, draw, layer});
                    }
                }
            }
        }

        for (int i = 0; i < sourceCount; i++) {
            DynamicGpuData.ChunkSectionInfo info =
                    (DynamicGpuData.ChunkSectionInfo) sectionInfos.get(i);

            List<Object[]> entries = drawsBySection.get(i);
            if (entries == null || entries.isEmpty()) continue;

            // Mirror into every periodic row that is worth drawing. No extra memory: each copy is one
            // more draw command pointing at the same compiled mesh.
            for (int step = -maxSteps; step <= maxSteps; step++) {
                if (step == 0) continue;   // already submitted by the vanilla pass

                int copyY = info.y() + step * period;

                AABB copyBounds = biblioteca$bounds(info, copyY);
                if (!biblioteca$worthDrawing(frustum, copyBounds, cameraY, viewDistanceBlocks)) continue;

                int copyIndex = sectionInfos.size();
                sectionInfos.add(new DynamicGpuData.ChunkSectionInfo(
                        info.x(), copyY, info.z(), info.visibility()));

                for (Object[] entry : entries) {
                    ChunkDrawGroupAccessor group         = (ChunkDrawGroupAccessor) entry[0];
                    DynamicGpuData.IndexedDraw original  = (DynamicGpuData.IndexedDraw) entry[1];
                    group.biblioteca$getDraws().add(new DynamicGpuData.IndexedDraw(
                            original.indexCount(),
                            original.instanceCount(),
                            original.firstIndex(),
                            original.baseVertex(),
                            copyIndex));
                }
            }
        }
    }

    private static AABB biblioteca$bounds(DynamicGpuData.ChunkSectionInfo info, int y) {
        return new AABB(info.x(), y, info.z(), info.x() + 16.0, y + 16.0, info.z() + 16.0);
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
            Frustum frustum, AABB box, double cameraY, double viewDistanceBlocks) {

        double centreY = (box.minY + box.maxY) * 0.5;
        if (Math.abs(centreY - cameraY) > viewDistanceBlocks) return false;

        return SeamCull.plainlyVisible(frustum, box);
    }
}
