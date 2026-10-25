package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingLight;
import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Rebuild the opposite seam's mesh when its neighbouring light samples change. */
@Mixin(LayerLightSectionStorage.class)
public abstract class LightStorageYLoopMixin {
    @WrapOperation(method = "swapSectionMap", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LightChunkGetter;onLightUpdate(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)V"))
    private void biblioteca$notifyPeriodicNeighbour(LightChunkGetter source, LightLayer layer, SectionPos section, Operation<Void> original) {
        Level level = LoopingLight.levelOf(source);
        if (level != null && LoopingRegistry.isLooping(level)) {
            int min = Compat.minSection(level);
            int folded = min + Math.floorMod(section.y() - min, Compat.sectionCount(level));
            if (folded != section.y()) section = SectionPos.of(section.x(), folded, section.z());
        }
        original.call(source, layer, section);
    }
}
