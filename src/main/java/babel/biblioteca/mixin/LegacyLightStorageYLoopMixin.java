package babel.biblioteca.mixin;

import babel.biblioteca.LoopingLightStorage;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LayerLightSectionStorage.class)
public abstract class LegacyLightStorageYLoopMixin implements LoopingLightStorage {
    @Override public DataLayer biblioteca$writable(long section) {
        LightStorageAccessor access = (LightStorageAccessor)(Object)this;
        if (!access.biblioteca$stores(section)) return null;
        if (access.biblioteca$changed().add(section)) access.biblioteca$updating().copyDataLayer(section);
        return access.biblioteca$layer(section, true);
    }
    @Override public void biblioteca$queue(long section, DataLayer data) {
        LightStorageAccessor access = (LightStorageAccessor)(Object)this;
        access.biblioteca$queueLegacy(section, data, true);
        if (data == null) access.biblioteca$untrusted().remove(section);
    }
    @Override public void biblioteca$dirty(long section) {
        LightStorageAccessor access = (LightStorageAccessor)(Object)this;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++)
            access.biblioteca$affected().add(SectionPos.offset(section, x, y, z));
    }
}
