package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.injection.Coerce;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import babel.biblioteca.LoopingRegistry;

@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSection", "net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection"}, remap = false)
public abstract class SnapshotInitMixin {
    @WrapMethod(method = "init")
    private void biblioteca$snapshot(Level level, SectionPos pos, Operation<Void> original) {
        int y = LoopingRegistry.isLooping(level) ? SodiumBridge.foldSection(level, pos.y()) : pos.y();
        original.call(level, SectionPos.of(pos.x(), y, pos.z()));
        ((SnapshotPositionMixin)this).biblioteca$position(pos);
    }
}
