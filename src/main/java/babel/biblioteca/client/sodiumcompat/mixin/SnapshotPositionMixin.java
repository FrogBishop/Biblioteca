package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSection", "net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection"}, remap = false)
public interface SnapshotPositionMixin {
    @Mutable @Accessor("pos") void biblioteca$position(SectionPos requested);
}
