package babel.biblioteca.client.sodium.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ClonedChunkSectionCache.class, remap = false)
public abstract class SodiumSectionCacheMixin {
    @Shadow @Final private Level world;

    @WrapMethod(method = "invalidate")
    private void biblioteca$invalidateBoundaryCopies(int x, int y, int z, Operation<Void> original) {
        original.call(x, y, z);
        if (!LoopingRegistry.isLooping(world)) return;
        int min = Compat.minSection(world), count = Compat.height(world) >> 4;
        if (y == min) original.call(x, y + count, z);
        if (y == min + count - 1) original.call(x, y - count, z);
    }
}
