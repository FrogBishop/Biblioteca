package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Shadow private Long2ObjectMap<SectionCopy> sectionCopyCache;

    @Inject(method = "getSectionDataCopy", at = @At("HEAD"), cancellable = true)
    private void biblioteca$foldRegionSectionY(
            Level level, int sectionX, int sectionY, int sectionZ,
            CallbackInfoReturnable<SectionCopy> cir) {

        if (!LoopingRegistry.isLooping(level)) return;

        int minSection = SectionPos.blockToSectionCoord(level.getMinY());
        int count      = level.getSectionsCount();

        if (sectionY >= minSection && sectionY < minSection + count) return;

        int foldedSectionY = Math.floorMod(sectionY - minSection, count) + minSection;

        // Cache under the requested slice: the folded section is shared by both edges.
        cir.setReturnValue(this.sectionCopyCache.computeIfAbsent(
                SectionPos.asLong(sectionX, sectionY, sectionZ),
                key -> {
                    LevelChunk chunk = level.getChunk(sectionX, sectionZ);
                    return new SectionCopy(chunk, chunk.getSectionIndexFromSectionY(foldedSectionY));
                }));
    }
}
