package babel.biblioteca.mixin;

import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Keep the periodic sky solver in vanilla storage so saving and light packets stay authoritative. */
@Mixin(LayerLightSectionStorage.class)
public interface LightStorageAccessor {
    @Invoker("storingLightForSection") boolean biblioteca$stores(long section);
    @Invoker("getStoredLevel") int biblioteca$get(long block);
    @Invoker("setStoredLevel") void biblioteca$set(long block, int value);
    @Invoker("getDataLayerToWrite") DataLayer biblioteca$writable(long section);
    @Invoker("updateSectionStatus") void biblioteca$sectionStatus(long section, boolean empty);
    @Invoker("queueSectionData") void biblioteca$queue(long section, DataLayer data);
    @Invoker("markSectionAndNeighborsAsAffected") void biblioteca$dirty(long section);
}
