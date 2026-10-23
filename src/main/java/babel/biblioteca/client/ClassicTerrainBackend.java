package babel.biblioteca.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL15;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** OpenGL submission for 1.20.4–1.21.4, sharing source VBOs and owning only sorted index storage. */
public final class ClassicTerrainBackend implements AutoCloseable {
    private final Map<RenderType, PeriodicTerrainPlan<VertexBuffer>> plans = new IdentityHashMap<>();
    private int indexBuffer = -1;

    public void drawSorted(List<SectionRenderDispatcher.RenderSection> sections, RenderType layer, Uniform offset,
                           int period, Vec3 camera, double distance, PeriodicTerrainPlan.Visibility visible) {
        List<PeriodicTerrainPlan.Source<VertexBuffer>> sources = new ArrayList<>();
        for (SectionRenderDispatcher.RenderSection section : sections) {
            if (ClientCompat.empty(section, layer)) continue;
            VertexBuffer buffer = section.getBuffer(layer);
            LegacyVertexData data = (LegacyVertexData) buffer;
            if (!data.biblioteca$valid() || data.biblioteca$indexCount() <= 0) continue;
            BlockPos origin = ClientCompat.origin(section);
            sources.add(new PeriodicTerrainPlan.Source<>(buffer, origin.getX(), origin.getY(), origin.getZ(),
                    data.biblioteca$indexCount(), data.biblioteca$indexBytes(), data.biblioteca$centers()));
        }
        var copies = PeriodicTerrainPlan.copies(sources, period, camera, distance, visible);
        var result = plans.computeIfAbsent(layer, key -> new PeriodicTerrainPlan<>()).sort(copies, camera);
        if (result.indices().hasRemaining()) {
            if (this.indexBuffer < 0) this.indexBuffer = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, this.indexBuffer);
            // Replacing the store lets OpenGL retain any storage still used by an earlier draw.
            RenderSystem.glBufferData(GL15.GL_ARRAY_BUFFER, result.indices(), GL15.GL_STREAM_DRAW);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
        for (var draw : result.draws()) {
            var copy = draw.copy();
            VertexBuffer buffer = copy.source().handle();
            LegacyVertexData data = (LegacyVertexData) buffer;
            offset.set((float) (copy.x() - camera.x), (float) (copy.y() - camera.y), (float) (copy.z() - camera.z));
            offset.upload();
            data.biblioteca$bindForDraw();
            if (draw.sorted()) {
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.indexBuffer);
                try {
                    GlStateManager._drawElements(layer.mode().asGLMode, draw.indexCount(), data.biblioteca$indexGlType(),
                            (long) draw.firstIndex() * copy.source().indexBytes());
                } finally {
                    data.biblioteca$restoreIndices();
                }
            } else {
                buffer.draw();
            }
        }
        VertexBuffer.unbind();
        //? if <1.19 {
        /*VertexBuffer.unbindVertexArray();
        *///?}
    }

    @Override public void close() {
        for (var plan : plans.values()) plan.close();
        plans.clear();
        if (this.indexBuffer >= 0) {
            RenderSystem.glDeleteBuffers(this.indexBuffer);
            this.indexBuffer = -1;
        }
    }
}
