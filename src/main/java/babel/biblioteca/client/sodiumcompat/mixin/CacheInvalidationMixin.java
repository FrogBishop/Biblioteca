package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.injection.Coerce;
import babel.biblioteca.Compat;
import net.minecraft.world.level.Level;

@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache", "net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache"}, remap = false)
public abstract class CacheInvalidationMixin {
    @WrapMethod(method = "invalidate")
    private void biblioteca$invalidateAliases(int x, int y, int z, Operation<Void> original) {
        original.call(x, y, z);
        if (!SodiumBridge.looping(this)) return;
        Level level = SodiumBridge.level(this);
        int min = Compat.minSection(level), count = Compat.height(level) >> 4;
        if (y == min) original.call(x, y + count, z);
        if (y == min + count - 1) original.call(x, y - count, z);
    }
}
