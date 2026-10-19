package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Adds the source sections whose periodic copies are visible to the 1.21.8 section graph.
 *
 * Same source-major scan as the 26.3 renderer: every source row is only one period tall, so
 * testing all period offsets that can reach the camera covers short worlds without assuming a
 * nearest image. This node has no ring-aware frustum mixin; the plain isVisible check is enough
 * for the terrain source set.
 */
@Mixin(SectionOcclusionGraph.class)
public abstract class MidSectionOcclusionGraphLoopMixin {

    @Shadow private ViewArea viewArea;

    @Inject(method = "addSectionsInFrustum", at = @At("TAIL"))
    private void biblioteca$addFarSeamSources(
            Frustum frustum,
            List<SectionRenderDispatcher.RenderSection> visibleSections,
            List<SectionRenderDispatcher.RenderSection> nearbyVisibleSections,
            CallbackInfo ci) {

        if (this.viewArea == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) return;

        LevelHeightAccessor height = this.viewArea.getLevelHeightAccessor();
        int minSectionY = height.getMinY() >> 4;
        int sectionCount = height.getHeight() >> 4;
        if (sectionCount <= 0) return;

        int radius = this.viewArea.getViewDistance();
        double period = (double) sectionCount * 16.0;
        double search = (double) radius * 16.0;
        double camY = frustum.getCamY();

        SectionPos camera = this.viewArea.getCameraSectionPos();
        MidViewAreaAccessor accessor = (MidViewAreaAccessor) this.viewArea;

        LongOpenHashSet present = new LongOpenHashSet(visibleSections.size() * 2);
        for (SectionRenderDispatcher.RenderSection section : visibleSections) {
            present.add(section.getSectionNode());
        }

        for (int sourceSectionY = minSectionY;
             sourceSectionY < minSectionY + sectionCount;
             sourceSectionY++) {

            double sourceBaseY = (double) sourceSectionY * 16.0;
            double sourceCentreY = sourceBaseY + 8.0;
            int lowestStep = (int) Math.ceil((camY - search - sourceCentreY) / period);
            int highestStep = (int) Math.floor((camY + search - sourceCentreY) / period);

            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                int sectionX = camera.x() + offsetX;
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    int sectionZ = camera.z() + offsetZ;
                    long node = SectionPos.asLong(sectionX, sourceSectionY, sectionZ);
                    if (present.contains(node)) continue;

                    SectionRenderDispatcher.RenderSection section =
                            accessor.biblioteca$getRenderSection(node);
                    if (section == null) continue;

                    for (int step = lowestStep; step <= highestStep; step++) {
                        double minY = sourceBaseY + (double) step * period;
                        AABB copyBounds = new AABB(
                                sectionX << 4, minY, sectionZ << 4,
                                (sectionX << 4) + 16.0, minY + 16.0, (sectionZ << 4) + 16.0);
                        if (!frustum.isVisible(copyBounds)) continue;

                        visibleSections.add(section);
                        present.add(node);
                        break;
                    }
                }
            }
        }
    }
}
