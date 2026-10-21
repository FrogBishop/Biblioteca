package babel.biblioteca.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
//? if >=26.2 {
import com.mojang.blaze3d.IndexType;
//?} else {
/*import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
*///?}

/** A borrowed section allocation, including its base vertex in the 26.1+ shared buffers. */
public record GpuTerrainBuffer(GpuBuffer vertices, GpuBuffer indices, IndexType indexType,
                               int firstIndex, int baseVertex, float visibility) {}
