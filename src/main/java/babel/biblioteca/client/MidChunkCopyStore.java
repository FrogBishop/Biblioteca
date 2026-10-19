package babel.biblioteca.client;

import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;

import java.util.List;

/**
 * Per-frame hand-off between the 1.21.8 chunk prepare wrapper and the chunk render mixin.
 *
 * prepareChunkRenders already bakes the camera offset into each draw's transform. Calling it
 * once per periodic layer therefore produces one complete ChunkSectionsToRender for that layer;
 * the render mixin replays those objects at the same point in the frame. The store is a render
 * thread local because the two hooks live in different classes.
 */
public final class MidChunkCopyStore {

    private MidChunkCopyStore() {}

    private static final class State {
        List<ChunkSectionsToRender> copies = List.of();
        boolean rendering;
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    public static void set(List<ChunkSectionsToRender> copies) {
        STATE.get().copies = copies;
    }

    public static List<ChunkSectionsToRender> copies() {
        return STATE.get().copies;
    }

    public static boolean isRendering() {
        return STATE.get().rendering;
    }

    public static void enterRendering() {
        STATE.get().rendering = true;
    }

    public static void exitRendering() {
        STATE.get().rendering = false;
    }
}
