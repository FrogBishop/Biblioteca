package babel.biblioteca.client.mixin;

import babel.biblioteca.client.ClientCompat;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.LoopingTranslucency;
import babel.biblioteca.client.SeamCull;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.MeshData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
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
 * section is therefore one more ChunkSectionInfo at a periodic height plus draws pointing at it.
 * The compiled vertex mesh is shared; the sorted transparency path gives each copy its own indices.
 *
 * Every periodic row within the render distance is submitted, not only the nearest one: with enough
 * render distance the player should see the world repeating, the way an endless vertical ring would
 * look. The two gates are that the row's box is inside the camera frustum and that its centre is
 * within the render distance; the frustum test alone is not enough, because FrustumYLoopMixin
 * answers for a box's periodic twin as well, which would let copies far beyond anything loaded
 * through.
 *
 * Opaque terrain and OIT add one draw per section layer per copy. Ordered transparency also needs
 * index storage and may split a section into multiple draws when its faces interleave with other
 * sections. A height-16 world at 32 chunks can produce about 65 copies of every visible section.
 *
 * OIT follows the vanilla Improved Transparency setting. When it is off, LoopingTranslucency builds
 * an independent quad order for each visible copy and merges all sections' orders back to front.
 * Changing one copy's indices therefore cannot corrupt another copy's order. Both the direct and
 * indirect vanilla draw paths consume the same sorted groups.
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
    @Unique private final LoopingTranslucency biblioteca$translucency = new LoopingTranslucency();
    @Unique private final Long2ObjectMap<MeshData.SortState> biblioteca$translucentSources = new Long2ObjectOpenHashMap<>();
    @Unique private boolean biblioteca$sortTranslucency;

    @Shadow private LevelRenderState levelRenderState;

    @Inject(method = "extractSectionDrawGroups", at = @At("HEAD"))
    private void biblioteca$prepareTranslucency(
            boolean respectTranslucentOrder, List<DynamicGpuData.ChunkSectionInfo> sectionInfos,
            Map<ChunkSectionLayer, List<LevelRenderer.ChunkDrawGroup>> drawGroups,
            CallbackInfoReturnable<Integer> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        this.biblioteca$sortTranslucency = respectTranslucentOrder && minecraft.level != null
                && LoopingRegistry.isLooping(minecraft.level);
        this.biblioteca$translucentSources.clear();
        if (!this.biblioteca$sortTranslucency) this.biblioteca$translucency.close();
    }

    /** Capture the exact mesh used for each draw while vanilla holds the dispatcher's buffer lock. */
    @WrapOperation(method = "extractSectionDrawGroups", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getSectionMesh()Lnet/minecraft/client/renderer/chunk/SectionMesh;"))
    private SectionMesh biblioteca$captureTranslucentSource(
            SectionRenderDispatcher.RenderSection section, Operation<SectionMesh> original) {
        SectionMesh mesh = original.call(section);
        if (this.biblioteca$sortTranslucency && mesh instanceof CompiledSectionMesh compiled
                && compiled.getTransparencyState() != null) {
            this.biblioteca$translucentSources.put(section.getSectionNode(), compiled.getTransparencyState());
        }
        return mesh;
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    private void biblioteca$finishTranslucencyFrame(CallbackInfo ci) {
        this.biblioteca$translucency.endFrame();
    }

    @Inject(method = {"resetLevelRenderData", "invalidateCompiledGeometry"}, at = @At("HEAD"))
    private void biblioteca$releaseTranslucency(CallbackInfo ci) {
        this.biblioteca$translucency.close();
        this.biblioteca$translucentSources.clear();
    }

    @Inject(
            method = "extractSectionDrawGroups(ZLjava/util/List;Ljava/util/Map;)I",
            at = @At("TAIL"))
    private void biblioteca$addSeamCopies(
            boolean respectTranslucentOrder,
            List<DynamicGpuData.ChunkSectionInfo> sectionInfos,
            Map<ChunkSectionLayer, List<LevelRenderer.ChunkDrawGroup>> drawGroups,
            CallbackInfoReturnable<Integer> cir) {

        // Start with shared-mesh copies. The ordered path replaces their translucent indices below.
        LevelRenderState renderState = this.levelRenderState;
        if (renderState == null || renderState.cameraRenderState == null) return;
        Frustum frustum = renderState.cameraRenderState.cullFrustum;
        if (frustum == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (!LoopingRegistry.isLooping(minecraft.level)) return;

        int sourceCount = sectionInfos.size();
        if (sourceCount == 0) return;

        int period = Compat.height(minecraft.level);
        if (period <= 0) return;
        double cameraY = renderState.cameraRenderState.pos.y;
        double viewDistanceBlocks = ClientCompat.renderDistance() * 16.0;

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
            DynamicGpuData.ChunkSectionInfo info = sectionInfos.get(i);
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
            List<LevelRenderer.ChunkDrawGroup> groups = drawGroups.get(layer);
            if (groups == null) continue;

            for (int g = 0; g < groups.size(); g++) {
                List<DynamicGpuData.IndexedDraw> draws = groups.get(g).draws();
                int originalDrawCount = draws.size();

                for (int d = 0; d < originalDrawCount; d++) {
                    DynamicGpuData.IndexedDraw original = draws.get(d);
                    int sourceIndex = original.baseInstance();
                    if (sourceIndex < 0 || sourceIndex >= sourceCount) continue;

                    DynamicGpuData.ChunkSectionInfo info = sectionInfos.get(sourceIndex);
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

        if (this.biblioteca$sortTranslucency) {
            List<LevelRenderer.ChunkDrawGroup> translucent = drawGroups.get(ChunkSectionLayer.TRANSLUCENT);
            if (translucent != null) {
                this.biblioteca$translucency.rebuild(sectionInfos, translucent, this.biblioteca$translucentSources,
                        Compat.minY(minecraft.level), period, renderState.cameraRenderState.pos,
                        info -> biblioteca$worthDrawing(frustum, info.x(), info.y(), info.z(), cameraY, viewDistanceBlocks));
            }
        }
    }

    /**
     * True when this row is inside the frustum and close enough to be seen at all.
     *
     * The frustum can extend beyond the configured terrain range, so its result also needs the
     * vertical distance limit. Test this copy's actual box: a ring-aware test could accept it just
     * because a different periodic copy is visible.
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
