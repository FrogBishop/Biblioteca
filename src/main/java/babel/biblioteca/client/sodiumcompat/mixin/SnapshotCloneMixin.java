package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.injection.Coerce;
import net.minecraft.core.SectionPos;

@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache", "net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache"}, remap = false)
public abstract class SnapshotCloneMixin {
    @WrapMethod(method = "clone") @Coerce
    private Object biblioteca$snapshot(int x, int y, int z, Operation<Object> original) {
        int canonical = SodiumBridge.looping(this) ? SodiumBridge.foldSection(SodiumBridge.level(this), y) : y;
        Object snapshot = original.call(x, canonical, z);
        if (canonical != y) ((SnapshotPositionMixin)snapshot).biblioteca$position(SectionPos.of(x, y, z));
        return snapshot;
    }
}
