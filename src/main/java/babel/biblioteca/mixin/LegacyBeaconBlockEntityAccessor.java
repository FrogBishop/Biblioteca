package babel.biblioteca.mixin;

import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Beam fields of the pre-1.21.5 BeaconBlockEntity. */
@Mixin(BeaconBlockEntity.class)
public interface LegacyBeaconBlockEntityAccessor {

    @Accessor("beamSections")
    List<BeaconBlockEntity.BeaconBeamSection> biblioteca$getBeamSections();

    @Accessor("beamSections")
    void biblioteca$setBeamSections(List<BeaconBlockEntity.BeaconBeamSection> sections);

    @Accessor("levels")
    void biblioteca$setLevels(int levels);
}