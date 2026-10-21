package babel.biblioteca.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
//? if >=1.21.6 {
import com.mojang.blaze3d.buffers.GpuBufferSlice;
//?}
import com.mojang.blaze3d.systems.RenderPass.Draw;
import net.minecraft.world.phys.Vec3;

/** Converts an ordered run to the native draw ABI without changing its mesh or index addressing. */
public final class GpuTerrainDraws {
    private GpuTerrainDraws() {}

    //? if >=1.21.6 {
    public static Draw<GpuBufferSlice[]> create(
    //?} else {
    /*public static Draw create(
    *///?}
            PeriodicTerrainPlan.Draw<GpuTerrainBuffer> draw, GpuBuffer sortedIndices, Vec3 camera, int uniformIndex) {
        var copy = draw.copy();
        var source = copy.source().handle();
        GpuBuffer indices = draw.sorted() ? sortedIndices : source.indices();
        //? if >=1.21.6 {
        return new Draw<>(
        //?} else {
        /*return new Draw(
        *///?}
                0, source.vertices(), indices, indices == null ? null : source.indexType(),
                draw.sorted() ? draw.firstIndex() : source.firstIndex(), draw.indexCount(),
                //? if >=26.1 {
                source.baseVertex(),
                //?}
                //? if >=1.21.6 {
                (slices, uploader) -> uploader.upload(
                        //? if >=1.21.11 {
                        "ChunkSection",
                        //?} else {
                        /*"DynamicTransforms",
                        *///?}
                        slices[uniformIndex]));
                //?} else {
                /*uploader -> uploader.upload("ModelOffset", (float) (copy.x() - camera.x),
                        (float) (copy.y() - camera.y), (float) (copy.z() - camera.z)));
                *///?}
    }
}
