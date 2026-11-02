package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium 0.3 stores a whole-column biome container, unlike later section-local palettes. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSection", remap = false)
public abstract class LegacyBiomeSnapshotMixin {
    @Unique private int biblioteca$minimumQuart;
    @Unique private int biblioteca$periodQuart;
    @Inject(method = "init", at = @At("HEAD"))
    private void biblioteca$biomePeriod(Level level, SectionPos position, CallbackInfo ci) {
        biblioteca$minimumQuart = Compat.minY(level) >> 2;
        biblioteca$periodQuart = LoopingRegistry.isLooping(level) ? Compat.height(level) >> 2 : 0;
    }
    @ModifyVariable(method = "getBiomeForNoiseGen", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int biblioteca$sourceBiome(int quartY) {
        return biblioteca$periodQuart == 0 ? quartY
                : biblioteca$minimumQuart + Math.floorMod(quartY - biblioteca$minimumQuart, biblioteca$periodQuart);
    }
}
