package babel.biblioteca.client.mixin;

import babel.biblioteca.client.MidChunkCopyStore;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replays the extra periodic ChunkSectionsToRender lists at the original renderGroup call.
 *
 * Each replay is a separate render pass on 1.21.8; that is the simple, correct first form. The
 * guard stops a replay from recursively replaying the whole store again.
 */
@Mixin(ChunkSectionsToRender.class)
public abstract class MidChunkSectionsToRenderMixin {

    @Inject(method = "renderGroup", at = @At("HEAD"))
    private void biblioteca$renderPeriodicCopies(
            ChunkSectionLayerGroup group, CallbackInfo ci) {

        if (MidChunkCopyStore.isRendering()) return;

        var copies = MidChunkCopyStore.copies();
        if (copies.isEmpty()) return;

        MidChunkCopyStore.enterRendering();
        try {
            for (ChunkSectionsToRender copy : copies) {
                if (copy != (Object) this) {
                    copy.renderGroup(group);
                }
            }
        } finally {
            MidChunkCopyStore.exitRendering();
        }
    }
}
