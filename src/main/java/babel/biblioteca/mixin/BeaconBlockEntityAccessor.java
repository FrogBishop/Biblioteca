package babel.biblioteca.mixin;

import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Write access to the two beam fields BeaconBlockEntity keeps private.
 *
 * BeaconBlockEntityYLoopMixin builds the beam itself and has to store it, but it does that from
 * a static injection handler (vanilla's tick is static), and a @Shadow instance field is not
 * reachable from a static method.
 */
@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityAccessor {

    @Accessor("beamSections")
    void biblioteca$setBeamSections(List<BeaconBeamOwner.Section> sections);

    @Accessor("levels")
    void biblioteca$setLevels(int levels);
}
