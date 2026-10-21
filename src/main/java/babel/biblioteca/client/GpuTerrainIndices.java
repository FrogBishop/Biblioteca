package babel.biblioteca.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
//? if <1.21.6 {
/*import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
*///?}
import java.nio.ByteBuffer;

/** Owns only periodic alpha indices. Source vertex buffers remain owned by Minecraft. */
public final class GpuTerrainIndices implements AutoCloseable {
    private GpuBuffer buffer;

    public GpuBuffer upload(ByteBuffer indices) {
        int bytes = indices.remaining();
        if (bytes == 0) return null;
        if (buffer == null || buffer.size() < bytes) {
            if (buffer != null) buffer.close();
            //? if >=1.21.6 {
            buffer = RenderSystem.getDevice().createBuffer(() -> "Biblioteca periodic alpha indices",
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, Math.max(65536, bytes));
            //?} else {
            /*buffer = RenderSystem.getDevice().createBuffer(() -> "Biblioteca periodic alpha indices",
                    BufferType.INDICES, BufferUsage.DYNAMIC_WRITE, Math.max(65536, bytes));
            *///?}
        }
        // Command writes preserve ordering with queued draws; this storage is never mapped over an in-flight draw.
        //? if >=1.21.6 {
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, bytes), indices);
        //?} else {
        /*RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer, indices, 0);
        *///?}
        return buffer;
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }
}
