package babel.biblioteca.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 1.16 keeps fixed-function matrices and quad VBOs; only sorted index storage belongs to us. */
public final class FixedTerrainBackend implements AutoCloseable {
    private final Map<RenderType, PeriodicTerrainPlan<VertexBuffer>> plans = new IdentityHashMap<>();
    private int indexBuffer = -1;

    public void draw(List<SectionRenderDispatcher.RenderSection> sections, RenderType layer, PoseStack poses,
                     int period, Vec3 camera, double distance, PeriodicTerrainPlan.Visibility visible) {
        List<PeriodicTerrainPlan.Source<VertexBuffer>> sources = new ArrayList<>();
        for (SectionRenderDispatcher.RenderSection section : sections) {
            if (ClientCompat.empty(section, layer)) continue;
            VertexBuffer buffer = section.getBuffer(layer);
            LegacyVertexData data = (LegacyVertexData) buffer;
            if (!data.biblioteca$valid() || data.biblioteca$indexCount() <= 0) continue;
            BlockPos pos = ClientCompat.origin(section);
            sources.add(new PeriodicTerrainPlan.Source<>(buffer, pos.getX(), pos.getY(), pos.getZ(),
                    data.biblioteca$indexCount(), 4, data.biblioteca$centers()));
        }
        var copies = PeriodicTerrainPlan.copies(sources, period, camera, distance, visible);
        var result = this.plans.computeIfAbsent(layer, key -> new PeriodicTerrainPlan<>()).sort(copies, camera);
        int previousArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousElements = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        try {
            if (result.indices().hasRemaining()) {
                if (this.indexBuffer < 0) this.indexBuffer = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.indexBuffer);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, result.indices(), GL15.GL_STREAM_DRAW);
            }
            for (var draw : result.draws()) {
                var copy = draw.copy();
                VertexBuffer buffer = copy.source().handle();
                ((LegacyVertexData) buffer).biblioteca$bindForDraw();
                poses.pushPose();
                try {
                    poses.translate(copy.x() - camera.x, copy.y() - camera.y, copy.z() - camera.z);
                    if (draw.sorted()) {
                        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.indexBuffer);
                        RenderSystem.pushMatrix();
                        try {
                            RenderSystem.loadIdentity();
                            RenderSystem.multMatrix(poses.last().pose());
                            GL11.glDrawElements(GL11.GL_TRIANGLES, draw.indexCount(), GL11.GL_UNSIGNED_INT,
                                    (long) draw.firstIndex() * 4L);
                        } finally {
                            RenderSystem.popMatrix();
                        }
                    } else {
                        buffer.draw(poses.last().pose(), GL11.GL_QUADS);
                    }
                } finally {
                    poses.popPose();
                }
            }
        } finally {
            DefaultVertexFormat.BLOCK.clearBufferState();
            VertexBuffer.unbind();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArray);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, previousElements);
            RenderSystem.clearCurrentColor();
        }
    }

    @Override public void close() {
        for (var plan : this.plans.values()) plan.close();
        this.plans.clear();
        if (this.indexBuffer >= 0) GL15.glDeleteBuffers(this.indexBuffer);
        this.indexBuffer = -1;
    }
}
