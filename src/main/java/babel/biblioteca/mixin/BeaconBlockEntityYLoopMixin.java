package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes beacons work in a Y-looping dimension.
 *
 * Vanilla decides whether a beacon is lit by scanning the column above it: the beam is built as
 * the scan climbs, and the beacon only counts as lit while that beam is non-empty. Nothing
 * anywhere asks "is there sky?" - an unobstructed column *is* the sky test. That rule cannot be
 * expressed here any more, so both halves of it are handled explicitly.
 *
 * 1. The dimension has no sky at all, so without the beacon_ignores_sky attribute the sky rule
 *    can never be satisfied: the beam and the pyramid level are forced to nothing and the beacon
 *    stays dark. This has to be explicit because vanilla's test only fails when something opaque
 *    blocks the column, and in a ring a column with open air above the beacon never blocks.
 *
 * 2. With the attribute on, the column is scanned around the ring: past the top row the scan
 *    continues at the bottom row, so a beam leaving the top of the world arrives under the
 *    bottom of the world, which is where the ring puts it. The scan stops at the first block
 *    that damps light like vanilla does, and runs at most one world height so a beam can never
 *    overlap itself.
 *
 * The scan is done in one pass rather than vanilla's ten-blocks-per-tick cursor because the
 * cursor is a plain Y counter: it cannot be continued across the seam, and the "scan finished"
 * test compares it against the column's surface height, which is meaningless once the scan has
 * wrapped. A full column is a few hundred block lookups, which is cheaper than the incremental
 * version takes to converge (vanilla needs 38 ticks to cover the same distance).
 *
 * The beam itself is drawn by BeaconBeamRenderYLoopMixin, which stops it where the scan stopped.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityYLoopMixin {

    /** The pyramid walk may reach past the bottom row, where the ring continues at the top row. */
    @WrapOperation(
            method = "updateBase",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinY()I"))
    private static int biblioteca$noFloor(Level level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MIN_VALUE : original.call(level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void biblioteca$ringBeam(
            Level level, BlockPos pos, BlockState selfState, BeaconBlockEntity entity, CallbackInfo ci) {

        if (!LoopingRegistry.isLooping(level)) return;

        BeaconBlockEntityAccessor accessor = (BeaconBlockEntityAccessor) entity;

        if (!LoopingRegistry.beaconsIgnoreSky(level)) {
            // No sky, so the beacon can never satisfy vanilla's rule. Dark, and no effects.
            if (!entity.getBeamSections().isEmpty()) {
                accessor.biblioteca$setBeamSections(new ArrayList<>());
            }
            accessor.biblioteca$setLevels(0);
            return;
        }

        accessor.biblioteca$setBeamSections(biblioteca$scanAroundTheRing(level, pos));
    }

    /**
     * Walks the column above {@code origin} once around the ring and returns the beam it builds,
     * stopping where vanilla stops: at the first block that damps light like a solid block.
     */
    private static List<BeaconBeamOwner.Section> biblioteca$scanAroundTheRing(Level level, BlockPos origin) {
        int period = level.getHeight();
        int minY   = level.getMinY();
        int x      = origin.getX();
        int z      = origin.getZ();

        List<BeaconBeamOwner.Section> sections = new ArrayList<>(4);
        BeaconBeamOwner.Section lastSection = null;

        for (int step = 0; step < period; step++) {
            int sectionY = Math.floorMod(origin.getY() + step - minY, period) + minY;
            BlockState state = level.getBlockState(new BlockPos(x, sectionY, z));

            if (state.getBlock() instanceof BeaconBeamBlock beamBlock) {
                int color = beamBlock.getColor().getTextureDiffuseColor();
                if (sections.size() <= 1) {
                    lastSection = new BeaconBeamOwner.Section(color);
                    sections.add(lastSection);
                } else if (lastSection != null) {
                    if (color == lastSection.getColor()) {
                        lastSection.increaseHeight();
                    } else {
                        lastSection = new BeaconBeamOwner.Section(
                                ARGB.average(lastSection.getColor(), color));
                        sections.add(lastSection);
                    }
                }
            } else {
                // Same test vanilla uses, including its bedrock exception.
                if (lastSection == null
                        || state.getLightDampening() >= 15 && !state.is(Blocks.BEDROCK)) {
                    break;
                }
                lastSection.increaseHeight();
            }
        }

        return sections;
    }
}
