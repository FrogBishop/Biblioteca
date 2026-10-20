package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.MidChunkCopyStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds one ChunkSectionsToRender per periodic layer on 1.21.8.
 *
 * The 1.21.8 terrain path bakes origin - camera into each section's DynamicUniforms transform.
 * prepareChunkRenders(matrix, x, y, z) therefore already takes the camera Y; calling it once per
 * period yields a complete, correctly transformed draw list for that layer. The list is handed
 * to MidChunkSectionsToRenderMixin, which replays the extra lists at the original renderGroup
 * call site. No mesh is copied.
 *
 * The hook is on prepareChunkRenders itself rather than on its caller. The vanilla call is inside
 * the synthetic lambda that addMainPass submits to the frame graph, so a @WrapOperation aimed at
 * addMainPass finds no target; on these nodes that failure is silent because the generated client
 * config uses defaultRequire 0. Re-entering the method through MidLevelRendererInvoker keeps the
 * copies independent of the synthetic name, and a guard stops the re-entry from recursing.
 *
 * Each copy is prepared from a filtered section list. visibleSections contains every source
 * section that is visible in *some* periodic row, and prepareChunkRenders would otherwise bake a
 * transform for all of them at every offset - so a section seen overhead was also submitted at
 * the player's feet, and one seen at the feet was submitted overhead. The filter asks the plain
 * cube test for the copy's own box; rows that keep no section are dropped instead of producing an
 * empty render pass.
 *
 * The filtered lists and the copy list are reused across frames: this runs once per frame and
 * allocating a list per row was pure churn.
 */
@Mixin(LevelRenderer.class)
public abstract class MidLevelRendererYLoopMixin {

    // @Mutable strips ACC_FINAL from the target field. Without it the accessor setter is a
    // putfield to a final field outside <init>, which the JVM rejects with IllegalAccessError
    // even though the mixin added the method to the same class.
    @Shadow @Final @Mutable private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Shadow private Frustum cullingFrustum;
    @Shadow private Frustum capturedFrustum;

    @Unique private final List<ObjectArrayList<SectionRenderDispatcher.RenderSection>> biblioteca$scratch
            = new ArrayList<>();
    @Unique private final List<ChunkSectionsToRender> biblioteca$copies = new ArrayList<>();
    @Unique private boolean biblioteca$preparingCopies;

    @Inject(method = "prepareChunkRenders", at = @At("HEAD"))
    private void biblioteca$preparePeriodicLayers(
            Matrix4fc matrix, double x, double y, double z,
            CallbackInfoReturnable<ChunkSectionsToRender> cir) {

        // The copy builds below call prepareChunkRenders again; those inner calls must run the
        // vanilla body only.
        if (this.biblioteca$preparingCopies) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) {
            MidChunkCopyStore.set(List.of());
            return;
        }

        int period = minecraft.level.getHeight();
        if (period <= 0) {
            MidChunkCopyStore.set(List.of());
            return;
        }

        double viewDistanceBlocks = minecraft.options.getEffectiveRenderDistance() * 16.0;
        int maxSteps = (int) Math.ceil(viewDistanceBlocks / period) + 1;

        List<ChunkSectionsToRender> copies = this.biblioteca$copies;
        copies.clear();

        Frustum frustum = this.capturedFrustum != null ? this.capturedFrustum : this.cullingFrustum;
        ObjectArrayList<SectionRenderDispatcher.RenderSection> source = this.visibleSections;
        MidLevelRendererInvoker invoker = (MidLevelRendererInvoker) (Object) this;

        this.biblioteca$preparingCopies = true;
        try {
            int scratch = 0;
            for (int step = -maxSteps; step <= maxSteps; step++) {
                if (step == 0) continue;

                if (frustum == null) {
                    // No frustum yet (first frame after a resize): keep the old draw-everything
                    // behaviour rather than hiding every copy.
                    copies.add(invoker.biblioteca$prepareChunkRenders(
                            matrix, x, y - (double) step * period, z));
                    continue;
                }

                ObjectArrayList<SectionRenderDispatcher.RenderSection> visible =
                        this.biblioteca$scratch(scratch++);
                this.biblioteca$collect(frustum, source, visible, (double) step * period);
                if (visible.isEmpty()) continue;

                ((MidLevelRendererAccessor) (Object) this).biblioteca$setVisibleSections(visible);
                try {
                    // prepareChunkRenders bakes blockPos - cameraY into modelOffset, so
                    // y - step * period moves the copy *up* by one period. That matches the
                    // filter above (origin + step * period) and 26.3's section-info y.
                    copies.add(invoker.biblioteca$prepareChunkRenders(
                            matrix, x, y - (double) step * period, z));
                } finally {
                    ((MidLevelRendererAccessor) (Object) this).biblioteca$setVisibleSections(source);
                }
            }
        } finally {
            this.biblioteca$preparingCopies = false;
        }

        MidChunkCopyStore.set(copies);
    }

    /**
     * Keeps the sections whose box, shifted by {@code offsetY}, is inside the cull frustum.
     *
     * The test is the plain integer cube test: there is no ring-aware Frustum mixin on this node,
     * but using the invoker keeps the scan allocation-free either way. A box counts as visible at
     * -1 and -2 (intersecting and fully inside).
     */
    private void biblioteca$collect(
            Frustum frustum,
            List<SectionRenderDispatcher.RenderSection> source,
            ObjectArrayList<SectionRenderDispatcher.RenderSection> out,
            double offsetY) {

        for (int i = 0; i < source.size(); i++) {
            SectionRenderDispatcher.RenderSection section = source.get(i);
            BlockPos origin = section.getRenderOrigin();
            int minX = origin.getX();
            int minZ = origin.getZ();
            double minY = origin.getY() + offsetY;

            if (((MidFrustumInvoker) frustum).biblioteca$cubeInFrustum(
                    minX, minY, minZ,
                    minX + 16.0, minY + 16.0, minZ + 16.0) < 0) {
                out.add(section);
            }
        }
    }

    private ObjectArrayList<SectionRenderDispatcher.RenderSection> biblioteca$scratch(int index) {
        List<ObjectArrayList<SectionRenderDispatcher.RenderSection>> scratch = this.biblioteca$scratch;
        while (scratch.size() <= index) {
            scratch.add(new ObjectArrayList<>());
        }
        ObjectArrayList<SectionRenderDispatcher.RenderSection> list = scratch.get(index);
        list.clear();
        return list;
    }
}
