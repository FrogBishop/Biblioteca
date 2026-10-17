package babel.biblioteca.mixin;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes LightEngine's chunk source to mixins that target its subclasses.
 *
 * The field is declared on LightEngine, and a @Shadow in a mixin targeting BlockLightEngine or
 * SkyLightEngine cannot reach it: @Shadow only resolves members declared in the target class
 * itself. An accessor declared on the owning class always resolves.
 */
@Mixin(LightEngine.class)
public interface LightEngineAccessor {

    @Accessor("chunkSource")
    LightChunkGetter biblioteca$chunkSource();
}
