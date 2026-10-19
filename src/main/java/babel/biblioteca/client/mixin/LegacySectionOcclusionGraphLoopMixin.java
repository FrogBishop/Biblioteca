package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Adds the source sections whose periodic copies are visible to the classic section graph.
 *
 * The 1.21.1 graph has no ring awareness; its octree stops at the world's vertical edge. This
 * pass runs after the vanilla frustum walk and asks the opposite question for every source
 * section: is any periodic copy inside the frustum? Source sections are only 16 blocks tall,
 * so a short world needs many copies and a nearest-image test is not enough.
 */
@Mixin(SectionOcclusionGraph.class)
public abstract class LegacySectionOcclusionGraphLoopMixin {

    @Shadow private ViewArea viewArea;

    @Inject(method = "addSectionsInFrustum", at = @At("TAIL"))
    private void biblioteca$addFarSeamSources(
            Frustum frustum,
            List<SectionRenderDispatcher.RenderSection> visibleSections,
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
        double viewDistanceBlocks = (double) radius * 16.0;

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        double camY = cameraPos.y;
        int cameraSectionX = SectionPos.blockToSectionCoord(cameraPos.x);
        int cameraSectionZ = SectionPos.blockToSectionCoord(cameraPos.z);

        LongOpenHashSet present = new LongOpenHashSet(visibleSections.size() * 2);
        for (SectionRenderDispatcher.RenderSection section : visibleSections) {
            present.add(SectionPos.asLong(
                    section.getOrigin().getX() >> 4,
                    section.getOrigin().getY() >> 4,
                    section.getOrigin().getZ() >> 4));
        }

        LegacyViewAreaAccessor accessor = (LegacyViewAreaAccessor) this.viewArea;

        for (int sourceSectionY = minSectionY;
             sourceSectionY < minSectionY + sectionCount;
             sourceSectionY++) {

            double sourceBaseY = (double) sourceSectionY * 16.0;
            double sourceCentreY = sourceBaseY + 8.0;
            int lowestStep = (int) Math.ceil((camY - viewDistanceBlocks - sourceCentreY) / period);
            int highestStep = (int) Math.floor((camY + viewDistanceBlocks - sourceCentreY) / period);

            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                int sectionX = cameraSectionX + offsetX;
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    int sectionZ = cameraSectionZ + offsetZ;
                    long node = SectionPos.asLong(sectionX, sourceSectionY, sectionZ);
                    if (present.contains(node)) continue;

                    SectionRenderDispatcher.RenderSection section =
                            accessor.biblioteca$getRenderSectionAt(
                                    new BlockPos(sectionX << 4, sourceSectionY << 4, sectionZ << 4));
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
