package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.injection.Coerce;
import babel.biblioteca.Compat;
import net.minecraft.world.level.Level;
import net.minecraft.core.SectionPos;

@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache", "net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache"}, remap = false)
public abstract class PackedCacheInvalidationMixin {
    @WrapMethod(method = "invalidate")
    private void biblioteca$invalidateAliases(long key, Operation<Void> original) {
        original.call(key);
        if (!SodiumBridge.looping(this)) return;
        Level level = SodiumBridge.level(this);
        int min = Compat.minSection(level), count = Compat.height(level) >> 4, y = SectionPos.y(key);
        if (y == min) original.call(SectionPos.asLong(SectionPos.x(key), y + count, SectionPos.z(key)));
        if (y == min + count - 1) original.call(SectionPos.asLong(SectionPos.x(key), y - count, SectionPos.z(key)));
    }
}
