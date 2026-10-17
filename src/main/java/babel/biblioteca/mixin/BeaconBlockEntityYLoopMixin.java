package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Makes beacons usable in a Y-looping dimension, driven by the dimension attribute
 * `beacon_ignores_sky` (see LoopingWorldData):
 *
 * 1. The pyramid is walked downwards one layer at a time and the walk stops as soon as a
 *    layer is below the world:
 *
 *       int ly = y - step;
 *       if (ly < level.getMinY()) break;
 *
 *    A beacon standing on the bottom row therefore finds no base at all, because the very
 *    first layer (y - 1) is already outside the world. The layer read needs no help: rows
 *    below the world are already folded onto the matching rows above it when the block
 *    state is looked up, so only the early exit has to stop firing.
 *
 * 2. A beacon normally only activates once its beam reaches open sky: the tick clears the
 *    collected beam sections as soon as it hits a block, and an empty beam means "no
 *    pyramid" as far as the activation check is concerned. In a ring there is no sky, and
 *    the base of a bottom-row beacon sits on the beam path (y - 1 is the top row), so the
 *    beam is always interrupted by the beacon's own pyramid.
 *
 *    When the dimension attribute is on, the sections collected up to the obstruction are
 *    kept instead of discarded: the beacon always activates as long as its base is valid,
 *    and the beam is drawn up to the first block that is not see-through. When the
 *    attribute is off the vanilla sky rule applies untouched, which in a ring means a
 *    beacon whose base wraps across the seam can never activate.
 *
 * Transparent blocks are handled exactly as before; nothing here changes what interrupts
 * a beam, only whether an interrupted beam still counts as "beacon active".
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityYLoopMixin {

    @WrapOperation(
            method = "updateBase",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinY()I"))
    private static int biblioteca$noFloor(Level level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MIN_VALUE : original.call(level);
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V"))
    private static void biblioteca$keepBeamSections(
            List<?> sections, Operation<Void> original,
            @Local(argsOnly = true) Level level) {
        if (LoopingRegistry.beaconsIgnoreSky(level)) {
            // Keep what the beam collected before the obstruction instead of discarding it.
            return;
        }
        original.call(sections);
    }
}
