package babel.biblioteca.mixin;

import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** increaseHeight is protected in the old BeamSection. */
@Mixin(BeaconBlockEntity.BeaconBeamSection.class)
public interface LegacyBeaconBeamSectionAccessor {

    @Invoker("increaseHeight")
    void biblioteca$increaseHeight();
}
