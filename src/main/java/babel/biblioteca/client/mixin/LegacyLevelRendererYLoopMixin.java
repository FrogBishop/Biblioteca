package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Repeats the classic terrain pass at whole world-height offsets.
 *
 * Before 26.3 the renderer walks LevelRenderer.visibleSections and calls
 * renderSectionLayer(type, cameraX, cameraY, cameraZ, ...) once per layer. The camera Y is only
 * used to build the per-section CHUNK_OFFSET uniform, and that uniform is sectionOrigin - camera,
 * so calling the same method again with y - step * height moves every compiled section buffer up
 * by step periods. No mesh is copied and no draw list is rebuilt. The sign matters now that each
 * copy is filtered: the filter tests origin + step * period, so the render call has to agree.
 *
 * A copy is only drawn when its own box is inside the cull frustum. That second gate is not
 * optional: visibleSections is filled by the seam scan with every source section that is visible
 * in *some* periodic row, and renderSectionLayer has no reason to ask again per row. Drawing the
 * whole list at every offset is what made a section seen overhead reappear at the player's feet,
 * and a section seen at the feet reappear overhead.
 *
 * Each layer call needs a different subset of the list, so the field is swapped for one
 * ObjectArrayList per offset. The lists are rebuilt once per frame, on the first terrain call,
 * and reused by the remaining render types; a row with no visible sections skips the layer call
 * entirely. Copying the lists is far cheaper than testing every section again for each of the
 * five terrain render types.
 *
 * Only the terrain render types are repeated. Lines, debug overlays and the other passes that
 * also go through renderSectionLayer stay single-layer.
 */
@Mixin(LevelRenderer.class)
public abstract class LegacyLevelRendererYLoopMixin {

    // @Mutable strips ACC_FINAL from the target field. Without it the accessor setter is a
    // putfield to a final field outside <init>, which the JVM rejects with IllegalAccessError
    // even though the mixin added the method to the same class.
    @Shadow @Final @Mutable private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Shadow private Frustum cullingFrustum;
    @Shadow private Frustum capturedFrustum;

    @Unique private ObjectArrayList<SectionRenderDispatcher.RenderSection>[] biblioteca$stepLists
            = biblioteca$newLists(0);
    @Unique private long biblioteca$stepListsFrame = Long.MIN_VALUE;
    @Unique private long biblioteca$frame;

    /** Marks a new frame so the per-offset lists are rebuilt exactly once. */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void biblioteca$beginFrame(CallbackInfo ci) {
        this.biblioteca$frame++;
    }

    @WrapOperation(
            method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;"
                            + "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDD"
                            + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void biblioteca$repeatTerrainLayer(
            LevelRenderer renderer, RenderType type, double x, double y, double z,
            Matrix4f projection, Matrix4f frustumMatrix,
            Operation<Void> original) {

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) {
            original.call(renderer, type, x, y, z, projection, frustumMatrix);
            return;
        }

        if (!biblioteca$isTerrain(type)) {
            original.call(renderer, type, x, y, z, projection, frustumMatrix);
            return;
        }

        int period = minecraft.level.getHeight();
        if (period <= 0) {
            original.call(renderer, type, x, y, z, projection, frustumMatrix);
            return;
        }

        double viewDistanceBlocks = minecraft.options.getEffectiveRenderDistance() * 16.0;
        int maxSteps = (int) Math.ceil(viewDistanceBlocks / period) + 1;

        ObjectArrayList<SectionRenderDispatcher.RenderSection> source = this.visibleSections;
        this.biblioteca$buildStepLists(source, maxSteps, period);

        for (int step = -maxSteps; step <= maxSteps; step++) {
            if (step == 0) {
                // The source rows are vanilla's own list; they are not filtered here because the
                // section graph already culled them against the (slightly wider) offset frustum.
                original.call(renderer, type, x, y, z, projection, frustumMatrix);
                continue;
            }

            ObjectArrayList<SectionRenderDispatcher.RenderSection> visible =
                    this.biblioteca$stepLists[step + maxSteps];
            if (visible.isEmpty()) continue;

            ((LegacyLevelRendererAccessor) (Object) this).biblioteca$setVisibleSections(visible);
            try {
                // The camera Y is subtracted from each section origin when CHUNK_OFFSET is
                // built, so y - step * period moves the copy *up* by one period. That matches
                // the filter above (origin + step * period) and 26.3's ChunkSectionInfo.y.
                original.call(renderer, type, x, y - (double) step * period, z,
                        projection, frustumMatrix);
            } finally {
                ((LegacyLevelRendererAccessor) (Object) this).biblioteca$setVisibleSections(source);
            }
        }
    }

    /**
     * Builds one filtered list per periodic offset, once per frame.
     *
     * The test is the plain cube test, not the ring-aware one: a copy's twin is the source
     * section and is visible by construction, so asking the ring-aware frustum would answer yes
     * for every copy. There is no FrustumYLoopMixin on this node, so the cube test is already
     * plain; the invoker keeps it allocation-free.
     */
    private void biblioteca$buildStepLists(
            ObjectArrayList<SectionRenderDispatcher.RenderSection> source,
            int maxSteps, int period) {

        if (this.biblioteca$stepListsFrame == this.biblioteca$frame) return;
        this.biblioteca$stepListsFrame = this.biblioteca$frame;

        int stride = maxSteps * 2 + 1;
        ObjectArrayList<SectionRenderDispatcher.RenderSection>[] lists = this.biblioteca$stepLists;
        if (lists.length < stride) {
            lists = biblioteca$newLists(stride);
            this.biblioteca$stepLists = lists;
        }
        for (int i = 0; i < stride; i++) {
            ObjectArrayList<SectionRenderDispatcher.RenderSection> list = lists[i];
            if (list == null) {
                lists[i] = list = new ObjectArrayList<>();
            } else {
                list.clear();
            }
        }

        Frustum frustum = this.capturedFrustum != null ? this.capturedFrustum : this.cullingFrustum;
        if (frustum == null) {
            // No frustum yet (first frame after a resize): keep the old draw-everything behaviour
            // rather than hiding every copy.
            for (int step = -maxSteps; step <= maxSteps; step++) {
                if (step != 0) lists[step + maxSteps].addAll(source);
            }
            return;
        }

        for (int i = 0; i < source.size(); i++) {
            SectionRenderDispatcher.RenderSection section = source.get(i);
            BlockPos origin = section.getOrigin();
            int minX = origin.getX();
            int minZ = origin.getZ();
            double baseY = origin.getY();

            for (int step = -maxSteps; step <= maxSteps; step++) {
                if (step == 0) continue;

                double minY = baseY + (double) step * period;
                if (((LegacyFrustumInvoker) frustum).biblioteca$cubeInFrustum(
                        minX, minY, minZ,
                        minX + 16.0, minY + 16.0, minZ + 16.0)) {
                    lists[step + maxSteps].add(section);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectArrayList<SectionRenderDispatcher.RenderSection>[] biblioteca$newLists(int size) {
        return new ObjectArrayList[size];
    }

    private static boolean biblioteca$isTerrain(RenderType type) {
        return type == RenderType.solid()
                || type == RenderType.cutoutMipped()
                || type == RenderType.cutout()
                || type == RenderType.translucent()
                || type == RenderType.tripwire();
    }
}
