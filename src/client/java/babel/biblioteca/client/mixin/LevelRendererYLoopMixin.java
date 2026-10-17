package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the terrain that a Y-looping dimension repeats above and below itself.
 *
 * extractSectionDrawGroups turns the visible sections into one ChunkSectionInfo plus a
 * set of draw commands per layer, with the section index carried in the draw's instance
 * base. A copy of a section is therefore just one more ChunkSectionInfo at the periodic
 * height plus duplicated draws pointing at it, which is what this adds.
 *
 * One copy in each direction is enough: the next copy is a whole world height further
 * away and can never be in front of the camera. A copy is emitted when its box is inside the
 * camera frustum and its centre is within the render distance. The frustum test alone is
 * not enough, because FrustumYLoopMixin answers for a box's periodic twin as well, which
 * would let copies far beyond anything that is loaded through.
 *
 * The source sections are the ones SectionOcclusionGraphLoopMixin put in
 * LevelRenderer.visibleSections; on the far side of the seam those are sections the vanilla
 * section graph never walks to.
 */
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class LevelRendererYLoopMixin {

    @Shadow private LevelRenderState levelRenderState;

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
        if (!LoopingDimensionCache.isLooping(minecraft.level.dimension())) return;

        int sourceCount = sectionInfos.size();
        if (sourceCount == 0) return;

        int period = minecraft.level.getHeight();
        double cameraY = renderState.cameraRenderState.pos.y;
        double viewDistanceBlocks = minecraft.options.getEffectiveRenderDistance() * 16.0;

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
                                .add(new Object[]{group, draw});
                    }
                }
            }
        }

        for (int i = 0; i < sourceCount; i++) {
            DynamicGpuData.ChunkSectionInfo info =
                    (DynamicGpuData.ChunkSectionInfo) sectionInfos.get(i);
            List<Object[]> entries = drawsBySection.get(i);
            if (entries == null || entries.isEmpty()) continue;

            for (int direction = -1; direction <= 1; direction += 2) {
                int copyY = info.y() + direction * period;

                AABB copyBounds = new AABB(
                        info.x(), copyY, info.z(),
                        info.x() + 16.0, copyY + 16.0, info.z() + 16.0);
                if (!frustum.isVisible(copyBounds)) continue;

                // A copy further away than the render distance cannot be seen; dropping it
                // here keeps the frustum's ring test from duplicating the whole view area.
                double copyCentreY = copyY + 8.0;
                if (Math.abs(copyCentreY - cameraY) > viewDistanceBlocks) continue;

                int copyIndex = sectionInfos.size();
                sectionInfos.add(new DynamicGpuData.ChunkSectionInfo(
                        info.x(), copyY, info.z(), 1.0f));

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
}
