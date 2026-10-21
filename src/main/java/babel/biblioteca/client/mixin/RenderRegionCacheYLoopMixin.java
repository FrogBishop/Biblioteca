package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Gives section compilation the wrapped neighbour instead of an empty one at the world edges.
 *
 * Section geometry is not compiled against the level, but against a RenderSectionRegion: a 3x3x3
 * snapshot of block states taken around the section being built. For the section at the top of the
 * world, the "+1 section" slice of that snapshot is above the world, so it was filled with an empty
 * copy - air. Anything that looks at the block above while building the top section therefore saw
 * air rather than the bottom row of the world, and the seam came out visibly broken: a fluid on the
 * top row did not see the fluid above it, so its surface stayed lowered and its model stopped being
 * a full cube. Face culling and the smooth-lighting neighbour probes have the same blind spot.
 *
 * The fix is at the source of the snapshot: when a slice falls outside the world, copy the section
 * it folds to instead. SectionCopy reads block states by the low bits of the queried position, so a
 * copy of the bottom section answers correctly for the row just above the top - the local row index
 * of y=320 is 0, which is exactly the bottom row.
 */
@Mixin(RenderRegionCache.class)
public abstract class RenderRegionCacheYLoopMixin {

    @ModifyVariable(method = "getSectionDataCopy", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int biblioteca$foldRegionSectionY(int sectionY, @Local(argsOnly = true) Level level) {
        if (!LoopingRegistry.isLooping(level)) return sectionY;

        int minSection = SectionPos.blockToSectionCoord(level.getMinY());
        int count      = level.getSectionsCount();

        // Vanilla constructs and caches the immutable snapshot under its canonical section key.
        return Math.floorMod(sectionY - minSection, count) + minSection;
    }
}
