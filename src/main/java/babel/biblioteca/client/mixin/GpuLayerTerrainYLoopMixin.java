package babel.biblioteca.client.mixin;

import babel.biblioteca.client.ClientCompat;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.GpuTerrainIndices;
import babel.biblioteca.client.GpuTerrainBuffer;
import babel.biblioteca.client.GpuTerrainDraws;
import babel.biblioteca.client.PeriodicTerrainPlan;
import babel.biblioteca.client.QuadCenters;
import babel.biblioteca.client.SeamCull;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.MeshData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** 1.21.5 keeps per-layer passes but submits GPU draw records instead of OpenGL calls. */
@Mixin(LevelRenderer.class)
public abstract class GpuLayerTerrainYLoopMixin {
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Shadow private Frustum cullingFrustum;
    @Shadow private Frustum capturedFrustum;
    @Unique private final PeriodicTerrainPlan<GpuTerrainBuffer> biblioteca$alpha = new PeriodicTerrainPlan<>();
    @Unique private final GpuTerrainIndices biblioteca$indices = new GpuTerrainIndices();

    @ModifyVariable(method = "renderSectionLayer", ordinal = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
    private ArrayList<RenderPass.Draw> biblioteca$periodicDraws(ArrayList<RenderPass.Draw> original,
            @Local(argsOnly = true) RenderType layer,
            @Local(argsOnly = true, ordinal = 0) double cameraX,
            @Local(argsOnly = true, ordinal = 1) double cameraY,
            @Local(argsOnly = true, ordinal = 2) double cameraZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) return original;
        Vec3 camera = new Vec3(cameraX, cameraY, cameraZ);
        boolean alpha = layer == RenderType.translucent();
        List<PeriodicTerrainPlan.Source<GpuTerrainBuffer>> sources = new ArrayList<>();
        for (SectionRenderDispatcher.RenderSection section : visibleSections) {
            var buffers = section.getBuffers(layer);
            if (section.getCompiled().isEmpty(layer) || buffers == null) continue;
            BlockPos pos = section.getRenderOrigin();
            MeshData.SortState state = alpha
                    ? ((GpuCompiledSectionAccessor) section.getCompiled()).biblioteca$getTransparencyState() : null;
            var handle = new GpuTerrainBuffer(buffers.getVertexBuffer(), buffers.getIndexBuffer(), buffers.getIndexType(), 0, 0, 1.0f);
            sources.add(new PeriodicTerrainPlan.Source<>(handle, pos.getX(), pos.getY(), pos.getZ(),
                    buffers.getIndexCount(), buffers.getIndexType().bytes,
                    state == null ? null : QuadCenters.of(state.centroids())));
        }
        Frustum frustum = capturedFrustum == null ? cullingFrustum : capturedFrustum;
        var copies = PeriodicTerrainPlan.copies(sources, Compat.height(minecraft.level), camera,
                ClientCompat.renderDistance() * 16.0,
                (x, y, z, xx, yy, zz) -> frustum == null || SeamCull.plainlyVisible(frustum, x, y, z, xx, yy, zz));
        List<PeriodicTerrainPlan.Draw<GpuTerrainBuffer>> draws;
        GpuBuffer sortedIndices = null;
        if (alpha) {
            var sorted = biblioteca$alpha.sort(copies, camera);
            draws = sorted.draws();
            sortedIndices = biblioteca$indices.upload(sorted.indices());
        } else {
            draws = copies.stream().map(copy -> new PeriodicTerrainPlan.Draw<>(copy, 0, copy.source().indexCount(), false)).toList();
        }
        ArrayList<RenderPass.Draw> result = new ArrayList<>(draws.size());
        for (var draw : draws) {
            result.add(GpuTerrainDraws.create(draw, sortedIndices, camera, 0));
        }
        return result;
    }

    @Inject(method = {"close", "allChanged", "setLevel"}, at = @At("HEAD"))
    private void biblioteca$release(CallbackInfo ci) {
        biblioteca$alpha.close();
        biblioteca$indices.close();
    }
}
