package babel.biblioteca.client;

import com.mojang.blaze3d.vertex.VertexFormat;

/** Terrain metadata attached to classic OpenGL vertex buffers. */
public interface LegacyVertexData {
    void biblioteca$trackQuads(boolean enabled);
    QuadCenters biblioteca$centers();
    int biblioteca$indexCount();
    VertexFormat.IndexType biblioteca$indexType();
    void biblioteca$restoreIndices();
}
