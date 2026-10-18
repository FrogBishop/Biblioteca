package babel.biblioteca.client.mixin;

import babel.biblioteca.client.SeamSortOrder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sorts a mirrored section's translucent geometry for the drawn row when the section is compiled.
 *
 * A section's translucent quads are ordered once, when the mesh is built, from a sorting origin
 * derived from the camera and the section's own coordinates. A mirror row is a whole world height
 * away from those coordinates, so a freshly compiled section is ordered for a camera hundreds of
 * blocks off and its water blends as if every block had an extra face.
 *
 * This is the path that made the artefact come back simply by looking up at the sky and down again:
 * that changes which sections are compiled, and every new mesh bakes the wrong origin. The resort
 * path is patched separately, but it only runs when the camera moves - which is why the water used
 * to look right only after walking across the seam.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$CompileTask")
public abstract class SectionCompileSortYLoopMixin {

    @WrapOperation(
            method = "doTask",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;"
                            + "createVertexSorting(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/phys/Vec3;)"
                            + "Lcom/mojang/blaze3d/vertex/VertexSorting;"))
    private VertexSorting biblioteca$compileSortForDrawnRow(
            SectionRenderDispatcher.RenderSection section, SectionPos pos, Vec3 camera,
            Operation<VertexSorting> original) {

        return original.call(section, SeamSortOrder.drawnPos(pos), camera);
    }

    /** The viewpoint stored with the mesh has to match, or the next resort would fight this one. */
    @WrapOperation(
            method = "doTask",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/TranslucencyPointOfView;"
                            + "of(Lnet/minecraft/world/phys/Vec3;J)"
                            + "Lnet/minecraft/client/renderer/chunk/TranslucencyPointOfView;"))
    private TranslucencyPointOfView biblioteca$compileViewpointForDrawnRow(
            Vec3 camera, long sectionNode, Operation<TranslucencyPointOfView> original) {

        return original.call(camera, SeamSortOrder.drawnNode(sectionNode));
    }
}
