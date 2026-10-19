package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.MidChunkCopyStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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
 */
@Mixin(LevelRenderer.class)
public abstract class MidLevelRendererYLoopMixin {

    @WrapOperation(
            method = "addMainPass",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;"
                            + "prepareChunkRenders(Lorg/joml/Matrix4fc;DDD)"
                            + "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;"))
    private ChunkSectionsToRender biblioteca$preparePeriodicLayers(
            LevelRenderer renderer, Matrix4fc matrix, double x, double y, double z,
            Operation<ChunkSectionsToRender> original) {

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) {
            MidChunkCopyStore.set(List.of());
            return original.call(renderer, matrix, x, y, z);
        }

        int period = minecraft.level.getHeight();
        if (period <= 0) {
            MidChunkCopyStore.set(List.of());
            return original.call(renderer, matrix, x, y, z);
        }

        double viewDistanceBlocks = minecraft.options.getEffectiveRenderDistance() * 16.0;
        int maxSteps = (int) Math.ceil(viewDistanceBlocks / period) + 1;

        List<ChunkSectionsToRender> copies = new ArrayList<>(maxSteps * 2 + 1);
        ChunkSectionsToRender centre = null;

        for (int step = -maxSteps; step <= maxSteps; step++) {
            ChunkSectionsToRender rendered =
                    original.call(renderer, matrix, x, y + (double) step * period, z);
            copies.add(rendered);
            if (step == 0) {
                centre = rendered;
            }
        }

        MidChunkCopyStore.set(copies);
        return centre != null ? centre : copies.get(0);
    }
}
