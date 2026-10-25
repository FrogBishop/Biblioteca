package babel.biblioteca.mixin;

import net.minecraft.world.level.chunk.LightChunkGetter;
//? if >=1.20 {
import net.minecraft.world.level.lighting.LightEngine;
//?} else {
/*import net.minecraft.world.level.lighting.LayerLightEngine;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

/**
 * Exposes LightEngine's chunk source to mixins that target its subclasses.
 *
 * Declaring the accessor on the field's owning class lets the BlockLightEngine mixin use it
 * without duplicating access to an inherited field.
 */
//? if >=1.20 {
@Mixin(LightEngine.class)
//?} else {
/*@Mixin(LayerLightEngine.class)
*///?}

public interface LightEngineAccessor {

    @Accessor("chunkSource")
    LightChunkGetter biblioteca$chunkSource();
    //? if >=1.20 {
    @Accessor("increaseQueue") LongArrayFIFOQueue biblioteca$increaseQueue();
    @Accessor("decreaseQueue") LongArrayFIFOQueue biblioteca$decreaseQueue();
    //?}
}
