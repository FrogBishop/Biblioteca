package babel.biblioteca.mixin;

import babel.biblioteca.LoopingLight;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A missing section in a ring is not an implicit level-15 sky source. */
@Mixin(SkyLightSectionStorage.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class SkyLightStorageYLoopMixin extends LayerLightSectionStorage {
    protected SkyLightStorageYLoopMixin(LightChunkGetter source, DataLayerStorageMap data) {
        super(LightLayer.SKY, source, data);
    }

    @Inject(method = "getLightValue(JZ)I", at = @At("HEAD"), cancellable = true)
    private void biblioteca$storedPeriodicSky(long node, boolean updating, CallbackInfoReturnable<Integer> cir) {
        Level level = LoopingLight.levelOf(this.chunkSource);
        if (level == null || !LoopingRegistry.isLooping(level)) return;
        node = LoopingLight.foldBlockNode(node, level);
        DataLayer layer = this.getDataLayer(SectionPos.blockToSection(node), updating);
        cir.setReturnValue(layer == null ? 0 : layer.get(BlockPos.getX(node) & 15, BlockPos.getY(node) & 15, BlockPos.getZ(node) & 15));
    }

    @Inject(method = "createDataLayer", at = @At("HEAD"), cancellable = true)
    private void biblioteca$noImplicitSky(long section, CallbackInfoReturnable<DataLayer> cir) {
        Level level = LoopingLight.levelOf(this.chunkSource);
        if (level == null || !LoopingRegistry.isLooping(level)) return;
        DataLayer queued = (DataLayer) this.queuedSections.get(section);
        cir.setReturnValue(queued == null ? new DataLayer() : queued);
    }
}
