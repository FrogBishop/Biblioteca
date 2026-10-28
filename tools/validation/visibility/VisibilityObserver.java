package biblioteca.visibility.mixin;

import biblioteca.visibility.VisibilityChecks;
import babel.biblioteca.client.SeamCull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compares all compiled source meshes with the actual solid draw list. */
@Mixin(value = LevelRenderer.class, priority = 100)
public abstract class VisibilityObserver {
    @Shadow private ViewArea viewArea;
    @Shadow private SectionRenderDispatcher sectionRenderDispatcher;
    @Shadow private LevelRenderState levelRenderState;
    @Shadow private boolean usingMultiDrawIndirectForTerrain;

    @Inject(method = "extractSectionDrawGroups", at = @At("TAIL"), order = 2000)
    private void observe(boolean sorted, List<DynamicGpuData.ChunkSectionInfo> infos,
            Map<ChunkSectionLayer, List<LevelRenderer.ChunkDrawGroup>> groups, CallbackInfoReturnable<Integer> ci) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || viewArea == null || levelRenderState.cameraRenderState == null) return;
        var cam = levelRenderState.cameraRenderState;
        int min = mc.level.getMinY(), copyY = min + mc.level.getHeight(), radius = viewArea.getViewDistance();
        Set<Long> submitted = new HashSet<>();
        for (var group : groups.getOrDefault(ChunkSectionLayer.SOLID, List.of())) for (var draw : group.draws()) {
            var info = infos.get(draw.baseInstance());
            if (info.y() == copyY) submitted.add(BlockPos.asLong(info.x(), copyY, info.z()));
        }
        int wanted = 0, available = 0, missing = 0;
        var center = viewArea.getCameraSectionPos();
        StringBuilder missingPositions = new StringBuilder();
        sectionRenderDispatcher.lock();
        try {
            for (int sx = center.x() - radius; sx <= center.x() + radius; sx++)
                for (int sz = center.z() - radius; sz <= center.z() + radius; sz++) {
                    int x = sx * 16, z = sz * 16;
                    if (Math.abs(copyY + 8 - cam.pos.y) > radius * 16.0) continue;
                    if (!SeamCull.plainlyVisible(cam.cullFrustum, x, copyY, z, x + 16, copyY + 16, z + 16)) continue;
                    wanted++;
                    var section = viewArea.getRenderSectionAt(new BlockPos(x, min, z));
                    if (section == null || !section.getRenderOrigin().equals(new BlockPos(x, min, z))) continue;
                    var mesh = section.getSectionMesh();
                    var draw = mesh.getSectionDraw(ChunkSectionLayer.SOLID);
                    var slice = sectionRenderDispatcher.getRenderSectionSlice(mesh, ChunkSectionLayer.SOLID);
                    if (draw == null || slice == null) continue;
                    available++;
                    if (!submitted.contains(BlockPos.asLong(x, copyY, z))) {
                        missing++;
                        if (missing <= 8) missingPositions.append(x).append(',').append(z).append(';');
                    }
                }
        } finally { sectionRenderDispatcher.unlock(); }
        var pose = VisibilityChecks.pose();
        float fov = mc.gameRenderer.mainCamera().getFov();
        VisibilityChecks.poseMatches = pose != null && Math.abs(cam.xRot - pose.pitch()) < 0.01
                && Math.abs(cam.yRot - pose.yaw()) < 0.01 && Math.abs(cam.pos.y - pose.y() - 1.62) < 0.01
                && Math.abs(fov - pose.fov()) < 0.01;
        VisibilityChecks.missing = missing;
        VisibilityChecks.frame = "eyeY=" + cam.pos.y + " pitch=" + cam.xRot + " yaw=" + cam.yRot + " actualFov=" + fov
                + " indirect=" + usingMultiDrawIndirectForTerrain + " wanted=" + wanted + " available=" + available
                + " copies=" + submitted.size() + " missing=" + missing + " poseMatches=" + VisibilityChecks.poseMatches
                + " missingPositions=" + missingPositions;
    }
}
