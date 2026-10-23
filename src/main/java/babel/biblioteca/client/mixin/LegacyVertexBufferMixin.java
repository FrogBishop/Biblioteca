package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LegacyVertexData;
import babel.biblioteca.client.QuadCenters;
//? if >=1.21.2 {
import com.mojang.blaze3d.buffers.GpuBuffer;
//?}
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
//? if >=1.21 {
import com.mojang.blaze3d.vertex.MeshData;
//?} else {
/*import com.mojang.blaze3d.vertex.BufferBuilder;
*///?}
import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/** Captures translucent terrain centroids once, while the original CPU vertices are still alive. */
@Mixin(VertexBuffer.class)
public abstract class LegacyVertexBufferMixin implements LegacyVertexData {
    @Shadow private int indexCount;
    @Shadow private VertexFormat.IndexType indexType;
    @Shadow private RenderSystem.AutoStorageIndexBuffer sequentialIndices;
    //? if >=1.21.2 {
    @Shadow private GpuBuffer indexBuffer;
    //?} else {
    /*@Shadow private int indexBufferId;
    *///?}
    @Unique private boolean biblioteca$trackQuads;
    @Unique private QuadCenters biblioteca$centers;

    @Override public void biblioteca$trackQuads(boolean enabled) {
        this.biblioteca$trackQuads = enabled;
        if (!enabled) this.biblioteca$centers = null;
    }
    @Override public QuadCenters biblioteca$centers() { return this.biblioteca$centers; }
    @Override public int biblioteca$indexCount() { return this.indexCount; }
    private VertexFormat.IndexType biblioteca$indexType() {
        return this.sequentialIndices != null ? this.sequentialIndices.type() : this.indexType;
    }
    @Override public int biblioteca$indexBytes() { return biblioteca$indexType().bytes; }
    @Override public int biblioteca$indexGlType() { return biblioteca$indexType().asGLType; }
    @Override public boolean biblioteca$valid() { return !((VertexBuffer) (Object) this).isInvalid(); }
    @Override public void biblioteca$bindForDraw() { ((VertexBuffer) (Object) this).bind(); }
    @Override public void biblioteca$restoreIndices() {
        if (this.sequentialIndices != null) this.sequentialIndices.bind(this.indexCount);
        else {
            //? if >=1.21.2 {
            if (this.indexBuffer != null) this.indexBuffer.bind();
            //?} else {
            /*GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.indexBufferId);
            *///?}
        }
    }

    @Inject(method = "upload", at = @At("HEAD"))
    private void biblioteca$captureQuadCenters(
            //? if >=1.21 {
            MeshData mesh,
            //?} else {
            /*BufferBuilder.RenderedBuffer mesh,
            *///?}
            CallbackInfo ci) {
        if (!this.biblioteca$trackQuads) return;
        var state = mesh.drawState();
        //? if <1.21 {
        /*if (state.indexOnly()) return;
        *///?}
        this.biblioteca$centers = null;
        if (state.mode() != VertexFormat.Mode.QUADS || !state.format().equals(DefaultVertexFormat.BLOCK)) return;
        this.biblioteca$centers = QuadCenters.fromVertices(mesh.vertexBuffer(), state.format().getVertexSize(), state.vertexCount());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void biblioteca$clearCenters(CallbackInfo ci) { this.biblioteca$centers = null; }
}
