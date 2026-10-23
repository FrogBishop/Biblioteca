package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LegacyVertexData;
import babel.biblioteca.client.QuadCenters;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.ByteBuffer;

/** Reads the actual vertex upload before the pre-1.19 BufferBuilder releases it. */
@Mixin(VertexBuffer.class)
public abstract class BufferedVertexYLoopMixin implements LegacyVertexData {
    @Shadow private VertexFormat format;
    //? if >=1.17 {
    @Shadow private int indexCount;
    @Shadow private VertexFormat.IndexType indexType;
    @Shadow private VertexFormat.Mode mode;
    @Shadow private boolean sequentialIndices;
    @Shadow private int arrayObjectId;
    @Shadow private int vertextBufferId;
    //?} else {
    /*@Shadow private int vertexCount;
    @Shadow private int id;
    *///?}
    @Unique private boolean biblioteca$track;
    @Unique private QuadCenters biblioteca$centers;

    @Override public void biblioteca$trackQuads(boolean enabled) {
        this.biblioteca$track = enabled;
        if (!enabled) this.biblioteca$centers = null;
    }
    @Override public QuadCenters biblioteca$centers() { return this.biblioteca$centers; }
    @Override public int biblioteca$indexCount() {
        //? if >=1.17 {
        return this.indexCount;
        //?} else {
        /*return this.vertexCount / 4 * 6;
        *///?}
    }
    @Override public int biblioteca$indexBytes() {
        //? if >=1.17 {
        return (this.sequentialIndices ? RenderSystem.getSequentialBuffer(this.mode, this.indexCount).type() : this.indexType).bytes;
        //?} else {
        /*return 4;
        *///?}
    }
    @Override public int biblioteca$indexGlType() {
        //? if >=1.17 {
        return (this.sequentialIndices ? RenderSystem.getSequentialBuffer(this.mode, this.indexCount).type() : this.indexType).asGLType;
        //?} else {
        /*return org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
        *///?}
    }
    @Override public boolean biblioteca$valid() {
        //? if >=1.17 {
        return this.vertextBufferId > 0;
        //?} else {
        /*return this.id >= 0;
        *///?}
    }
    @Override public void biblioteca$bindForDraw() {
        //? if >=1.17 {
        RenderSystem.glBindVertexArray(() -> this.arrayObjectId);
        ((VertexBuffer) (Object) this).bind();
        this.format.setupBufferState();
        //?} else {
        /*((VertexBuffer) (Object) this).bind();
        this.format.setupBufferState(0L);
        *///?}
    }
    @Override public void biblioteca$restoreIndices() { ((VertexBuffer) (Object) this).bind(); }

    @WrapOperation(method = "upload_", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;glBufferData(ILjava/nio/ByteBuffer;I)V"))
    private void biblioteca$capture(int target, ByteBuffer bytes, int usage, Operation<Void> original) {
        if (target == org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER && this.biblioteca$track) {
            this.biblioteca$centers = this.format.equals(DefaultVertexFormat.BLOCK)
                    ? QuadCenters.fromVertices(bytes, this.format.getVertexSize(), bytes.remaining() / this.format.getVertexSize()) : null;
        }
        original.call(target, bytes, usage);
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void biblioteca$release(CallbackInfo ci) { this.biblioteca$centers = null; }
}
