package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.core.SectionPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keep the periodic sky solver in vanilla storage so saving and light packets stay authoritative. */
@Mixin(LayerLightSectionStorage.class)
public interface LightStorageAccessor extends babel.biblioteca.LoopingLightStorage {
    @Accessor("chunkSource") LightChunkGetter biblioteca$chunkSource();
    @Invoker("storingLightForSection") boolean biblioteca$stores(long section);
    @Invoker("getStoredLevel") int biblioteca$get(long block);
    @Invoker("setStoredLevel") void biblioteca$set(long block, int value);
    @Invoker("updateSectionStatus") void biblioteca$sectionStatus(long section, boolean empty);
    //? if >=1.20 {
    @Invoker("getDataLayerToWrite") DataLayer biblioteca$writable(long section);
    @Invoker("queueSectionData") void biblioteca$queue(long section, DataLayer data);
    @Invoker("markSectionAndNeighborsAsAffected") void biblioteca$dirty(long section);
    //?} else {
    /*@Accessor("updatingSectionData") DataLayerStorageMap<?> biblioteca$updating();
    @Accessor("changedSections") LongSet biblioteca$changed();
    @Accessor("sectionsAffectedByLightUpdates") LongSet biblioteca$affected();
    @Accessor("untrustedSections") LongSet biblioteca$untrusted();
    @Invoker("getDataLayer") DataLayer biblioteca$layer(long section, boolean updating);
    @Invoker("runAllUpdates") void biblioteca$allocateSections();
    @Invoker("queueSectionData") void biblioteca$queueLegacy(long section, DataLayer data, boolean trusted);
    *///?}
}
