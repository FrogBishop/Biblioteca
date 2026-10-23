package babel.biblioteca.client;


/** Terrain metadata attached to classic OpenGL vertex buffers. */
public interface LegacyVertexData {
    void biblioteca$trackQuads(boolean enabled);
    QuadCenters biblioteca$centers();
    int biblioteca$indexCount();
    int biblioteca$indexBytes();
    int biblioteca$indexGlType();
    boolean biblioteca$valid();
    void biblioteca$bindForDraw();
    void biblioteca$restoreIndices();
}
