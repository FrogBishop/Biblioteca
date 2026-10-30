package babel.biblioteca.client.sodium.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Clone the opposite boundary's blocks/light, retaining the snapshot's original array frame. */
@Mixin(value = ClonedChunkSection.class, remap = false)
public abstract class SodiumClonedSectionMixin {
    @Shadow @Final private Level world;
    @Shadow private SectionPos pos;

    @WrapMethod(method = "init")
    private void biblioteca$periodicSnapshot(SectionPos requested, Operation<Void> original) {
        if (!LoopingRegistry.isLooping(world)) { original.call(requested); return; }
        int min = Compat.minSection(world), count = Compat.height(world) >> 4;
        int y = Math.floorMod(requested.y() - min, count) + min;
        original.call(SectionPos.of(requested.x(), y, requested.z()));
        this.pos = requested;
    }
}
