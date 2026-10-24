package babel.biblioteca.mixin;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes LightEngine's chunk source to mixins that target its subclasses.
 *
 * Declaring the accessor on the field's owning class lets the BlockLightEngine mixin use it
 * without duplicating access to an inherited field.
 */
@Mixin(LightEngine.class)
public interface LightEngineAccessor {

    @Accessor("chunkSource")
    LightChunkGetter biblioteca$chunkSource();

    @Accessor("increaseQueue")
    LongArrayFIFOQueue biblioteca$increaseQueue();

    @Accessor("decreaseQueue")
    LongArrayFIFOQueue biblioteca$decreaseQueue();
}
