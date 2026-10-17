package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import babel.biblioteca.client.LoopingRenderSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

/**
 * Implements Y-loop terrain rendering for looping dimensions.
 *
 * TAIL of extractSectionDrawGroups:
 *   For each section already in sectionInfos (placed there by vanilla from
 *   visibleSections, which SectionOcclusionGraphLoopMixin populates), create
 *   mirrored ChunkSectionInfo entries at y ± n*height and duplicate the
 *   corresponding IndexedDraw entries so the GPU renders each copy.
 *
 *   Mirror-source injection (getting the y=-64 sections into visibleSections
 *   when the player is at y=300) is handled by SectionOcclusionGraphLoopMixin,
 *   which fires earlier in the frame.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class LevelRendererYLoopMixin {

    @Shadow private LevelRenderState levelRenderState;

    @Inject(
            method = "extractSectionDrawGroups(ZLjava/util/List;Ljava/util/Map;)I",
            at = @At("TAIL"),
            require = 0)
    private void biblioteca$addYLoopMirrors(
            boolean sortTranslucent,
            List sectionInfos,
            Map drawGroups,
            CallbackInfoReturnable<Integer> cir) {

        LevelRenderState lrs = this.levelRenderState;
        if (lrs == null || lrs.cameraRenderState == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!LoopingDimensionCache.isLooping(mc.level.dimension())) return;

        int originalCount = sectionInfos.size();
        if (originalCount == 0) return;

        int    height = mc.level.getHeight();
        double camY   = lrs.cameraRenderState.pos.y;
        int    range  = LoopingRenderSettings.get();

        // Build index → draw entries map from the draw groups snapshot.
        Map<Integer, List<Object[]>> indexToEntries = new HashMap<>();
        for (Object layerObj : ChunkSectionLayer.values()) {
            ChunkSectionLayer layer = (ChunkSectionLayer) layerObj;
            List<?> groups = (List<?>) drawGroups.get(layer);
            if (groups == null) continue;
            for (int g = 0, gc = groups.size(); g < gc; g++) {
                ChunkDrawGroupAccessor acc = (ChunkDrawGroupAccessor) groups.get(g);
                List<DynamicGpuData.IndexedDraw> draws = acc.biblioteca$getDraws();
                for (int d = 0, dc = draws.size(); d < dc; d++) {
                    DynamicGpuData.IndexedDraw draw = draws.get(d);
                    int idx = draw.baseInstance();
                    if (idx >= 0 && idx < originalCount) {
                        indexToEntries
                                .computeIfAbsent(idx, k -> new ArrayList<>())
                                .add(new Object[]{acc, draw});
                    }
                }
            }
        }

        for (int i = 0; i < originalCount; i++) {
            DynamicGpuData.ChunkSectionInfo info =
                    (DynamicGpuData.ChunkSectionInfo) sectionInfos.get(i);
            List<Object[]> entries = indexToEntries.get(i);
            if (entries == null || entries.isEmpty()) continue;

            for (int dir = -1; dir <= 1; dir += 2) {
                for (int n = 1; n <= 64; n++) {
                    int    mirrorY   = info.y() + dir * n * height;

                    // Range is measured as absolute distance from camera to mirror.
                    // Always render the nearest mirror (n==1 in the direction that
                    // puts the mirror closest to the camera), and render further
                    // mirrors only while they still fall within range.
                    double nearEdge = Math.min(
                            Math.abs(camY - mirrorY),
                            Math.abs(camY - (mirrorY + 16)));
                    if (n > 1 && nearEdge > range) break;
                    // For n==1 we always render (nearest mirror in this direction).

                    int newIndex = sectionInfos.size();
                    sectionInfos.add(new DynamicGpuData.ChunkSectionInfo(
                            info.x(), mirrorY, info.z(), 1.0f));

                    for (Object[] entry : entries) {
                        ChunkDrawGroupAccessor acc      = (ChunkDrawGroupAccessor) entry[0];
                        DynamicGpuData.IndexedDraw orig = (DynamicGpuData.IndexedDraw) entry[1];
                        acc.biblioteca$getDraws().add(new DynamicGpuData.IndexedDraw(
                                orig.indexCount(),
                                orig.instanceCount(),
                                orig.firstIndex(),
                                orig.baseVertex(),
                                newIndex));
                    }
                }
            }
        }
    }
}
