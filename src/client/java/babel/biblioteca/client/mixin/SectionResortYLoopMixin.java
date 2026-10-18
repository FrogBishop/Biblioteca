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
 * Sorts a mirrored section's translucent geometry for the row it is actually drawn at.
 *
 * Both halves of the resort have to agree on that row, and they are computed separately:
 *
 *   - createVertexSorting decides the ORDER, from a section position and the camera;
 *   - TranslucencyPointOfView decides WHEN to re-sort, from the camera and the section node.
 *
 * Leaving either one on the section's own coordinates keeps the quads ordered for a camera hundreds
 * of blocks away, which is what made mirrored water blend as if every block had an extra face.
 * Changing only the viewpoint, as a first attempt did, changed when the re-sort happened but not the
 * order it produced, so nothing looked different.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$ResortTransparencyTask")
public abstract class SectionResortYLoopMixin {


    @WrapOperation(
            method = "doTask",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;"
                            + "createVertexSorting(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/phys/Vec3;)"
                            + "Lcom/mojang/blaze3d/vertex/VertexSorting;"))
    private VertexSorting biblioteca$sortForDrawnRow(
            SectionRenderDispatcher.RenderSection section, SectionPos pos, Vec3 camera,
            Operation<VertexSorting> original) {


        return original.call(section, SeamSortOrder.drawnPos(pos), camera);
    }

    @WrapOperation(
            method = "doTask",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/TranslucencyPointOfView;"
                            + "of(Lnet/minecraft/world/phys/Vec3;J)"
                            + "Lnet/minecraft/client/renderer/chunk/TranslucencyPointOfView;"))
    private TranslucencyPointOfView biblioteca$viewpointForDrawnRow(
            Vec3 camera, long sectionNode, Operation<TranslucencyPointOfView> original) {

        return original.call(camera, SeamSortOrder.drawnNode(sectionNode));
    }
}
