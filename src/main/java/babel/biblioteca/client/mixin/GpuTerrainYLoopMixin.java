package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.GpuTerrainBuffer;
import babel.biblioteca.client.GpuTerrainIndices;
import babel.biblioteca.client.GpuTerrainDraws;
import babel.biblioteca.client.PeriodicTerrainPlan;
import babel.biblioteca.client.QuadCenters;
import babel.biblioteca.client.SeamCull;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass.Draw;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
//? if >=26.1 {
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} else if >=1.21.11 {
/*import net.minecraft.util.Util;
*///?}
//? if >=1.21.11 {
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.TextureAtlas;
//?} else {
/*import org.joml.Vector3f;
import org.joml.Vector4f;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Periodic draw-list backend for 1.21.6–26.2, using each release's native pass executor. */
@Mixin(LevelRenderer.class)
public abstract class GpuTerrainYLoopMixin {
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    //? if >=26.1 {
    @Shadow private SectionRenderDispatcher sectionRenderDispatcher;
    @Shadow @Final private LevelRenderState levelRenderState;
    //?} else {
    /*@Unique private Frustum biblioteca$frustum;

    @Inject(method = "applyFrustum", at = @At("HEAD"))
    private void biblioteca$rememberFrustum(Frustum frustum, CallbackInfo ci) {
        biblioteca$frustum = frustum;
    }
    *///?}
    @Unique private final PeriodicTerrainPlan<GpuTerrainBuffer> biblioteca$alpha = new PeriodicTerrainPlan<>();
    @Unique private final GpuTerrainIndices biblioteca$indices = new GpuTerrainIndices();

    @Inject(method = "prepareChunkRenders", at = @At("HEAD"), cancellable = true)
    private void biblioteca$prepare(Matrix4fc matrix,
            //? if <26.1 {
            /*double cameraX, double cameraY, double cameraZ,
            *///?}
            CallbackInfoReturnable<ChunkSectionsToRender> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) return;
        //? if >=26.1 {
        Vec3 camera = levelRenderState.cameraRenderState.pos;
        Frustum frustum = levelRenderState.cameraRenderState.cullFrustum;
        EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<Draw<GpuBufferSlice[]>>>> groups = new EnumMap<>(ChunkSectionLayer.class);
        //?} else {
        /*Vec3 camera = new Vec3(cameraX, cameraY, cameraZ);
        Frustum frustum = biblioteca$frustum;
        EnumMap<ChunkSectionLayer, List<Draw<GpuBufferSlice[]>>> groups = new EnumMap<>(ChunkSectionLayer.class);
        *///?}
        //? if >=1.21.11 {
        GpuTextureView atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        List<DynamicUniforms.ChunkSectionInfo> uniforms = new ArrayList<>();
        long now = Util.getMillis();
        //?} else {
        /*List<DynamicUniforms.Transform> uniforms = new ArrayList<>();
        Vector4f white = new Vector4f(1, 1, 1, 1);
        Matrix4f textureMatrix = new Matrix4f();
        *///?}
        Map<BlockPos, Integer> uniformIndices = new HashMap<>();
        int largestIndexCount = 0;
        //? if >=26.1 {
        if (sectionRenderDispatcher == null) return;
        sectionRenderDispatcher.lock();
        try {
            //? if <26.2 {
            /*sectionRenderDispatcher.uploadGlobalGeomBuffersToGPU();
            *///?}
        //?}
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            boolean alpha = layer == ChunkSectionLayer.TRANSLUCENT;
            List<PeriodicTerrainPlan.Source<GpuTerrainBuffer>> sources = new ArrayList<>();
            for (SectionRenderDispatcher.RenderSection section : visibleSections) {
                SectionMesh mesh = section.getSectionMesh();
                BlockPos pos = section.getRenderOrigin();
                //? if >=26.1 {
                var draw = mesh.getSectionDraw(layer);
                var slice = sectionRenderDispatcher.getRenderSectionSlice(mesh, layer);
                if (draw == null || slice == null || draw.hasCustomIndexBuffer() && slice.indexBuffer() == null) continue;
                int count = draw.indexCount();
                var type = draw.indexType();
                GpuBuffer indexBuffer = draw.hasCustomIndexBuffer() ? slice.indexBuffer() : null;
                int firstIndex = indexBuffer == null ? 0 : (int) (slice.indexBufferOffset() / type.bytes);
                //? if >=26.2 {
                int stride = layer.pipeline().getVertexFormatBinding(0).getVertexSize();
                //?} else {
                /*int stride = layer.pipeline().getVertexFormat().getVertexSize();
                *///?}
                GpuTerrainBuffer handle = new GpuTerrainBuffer(slice.vertexBuffer(), indexBuffer, type,
                        firstIndex, (int) (slice.vertexBufferOffset() / stride), section.getVisibility(now));
                //?} else {
                /*var buffers = mesh.getBuffers(layer);
                if (buffers == null) continue;
                int count = buffers.getIndexCount();
                var type = buffers.getIndexType();
                GpuBuffer indexBuffer = buffers.getIndexBuffer();
                GpuTerrainBuffer handle = new GpuTerrainBuffer(buffers.getVertexBuffer(), indexBuffer, type, 0, 0,
                        //? if >=1.21.11 {
                        section.getVisibility(now));
                        //?} else {
                        /^1.0f);
                        ^///?}
                *///?}
                if (indexBuffer == null) largestIndexCount = Math.max(largestIndexCount, count);
                MeshData.SortState state = alpha && mesh instanceof CompiledSectionMesh compiled ? compiled.getTransparencyState() : null;
                sources.add(new PeriodicTerrainPlan.Source<>(handle, pos.getX(), pos.getY(), pos.getZ(),
                        count, type.bytes, state == null ? null : QuadCenters.of(state.centroids())));
            }
            var copies = PeriodicTerrainPlan.copies(sources, minecraft.level.getHeight(), camera,
                    minecraft.options.getEffectiveRenderDistance() * 16.0,
                    (x, y, z, xx, yy, zz) -> frustum == null || SeamCull.plainlyVisible(frustum, x, y, z, xx, yy, zz));
            List<PeriodicTerrainPlan.Draw<GpuTerrainBuffer>> planned;
            GpuBuffer sortedIndices = null;
            if (alpha) {
                var sorted = biblioteca$alpha.sort(copies, camera);
                planned = sorted.draws();
                sortedIndices = biblioteca$indices.upload(sorted.indices());
            } else {
                planned = copies.stream().map(copy -> new PeriodicTerrainPlan.Draw<>(copy, 0, copy.source().indexCount(), false)).toList();
            }
            List<Draw<GpuBufferSlice[]>> draws = new ArrayList<>(planned.size());
            for (var draw : planned) {
                var copy = draw.copy();
                GpuTerrainBuffer handle = copy.source().handle();
                BlockPos position = new BlockPos(copy.x(), copy.y(), copy.z());
                int uniformIndex = uniformIndices.computeIfAbsent(position, key -> {
                    int index = uniforms.size();
                    //? if >=1.21.11 {
                    uniforms.add(new DynamicUniforms.ChunkSectionInfo(new Matrix4f(matrix), copy.x(), copy.y(), copy.z(),
                            copy.y() == copy.source().y() ? handle.visibility() : 1.0f, atlas.getWidth(0), atlas.getHeight(0)));
                    //?} else {
                    /*uniforms.add(new DynamicUniforms.Transform(matrix, white,
                            new Vector3f((float) (copy.x() - camera.x), (float) (copy.y() - camera.y), (float) (copy.z() - camera.z)),
                            textureMatrix, 1.0f));
                    *///?}
                    return index;
                });
                draws.add(GpuTerrainDraws.create(draw, sortedIndices, camera, uniformIndex));
            }
            // Native executors reverse translucent lists; store the inverse of our far-to-near stream.
            if (alpha) Collections.reverse(draws);
            //? if >=26.1 {
            Int2ObjectOpenHashMap<List<Draw<GpuBufferSlice[]>>> group = new Int2ObjectOpenHashMap<>();
            // A single group preserves alpha order even when the source spans multiple Uber buffers.
            group.put(0, draws);
            groups.put(layer, group);
            //?} else {
            /*groups.put(layer, draws);
            *///?}
        }
        //? if >=26.1 {
        } finally {
            sectionRenderDispatcher.unlock();
        }
        //?}
        //? if >=1.21.11 {
        GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeChunkSections(uniforms.toArray(new DynamicUniforms.ChunkSectionInfo[0]));
        cir.setReturnValue(new ChunkSectionsToRender(atlas, groups, largestIndexCount, slices));
        //?} else {
        /*GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeTransforms(uniforms.toArray(new DynamicUniforms.Transform[0]));
        cir.setReturnValue(new ChunkSectionsToRender(groups, largestIndexCount, slices));
        *///?}
    }

    @Inject(method = {"close", "allChanged", "setLevel"}, at = @At("HEAD"))
    private void biblioteca$release(CallbackInfo ci) {
        biblioteca$alpha.close();
        biblioteca$indices.close();
    }
}
