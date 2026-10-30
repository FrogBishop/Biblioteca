package babel.biblioteca.client.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterator;
import java.util.List;

/** Each native draw keeps its source buffer and selects one periodic model origin. */
public final class SodiumDrawList<T> implements ChunkRenderListIterator<T> {
    public record Copy<T>(T state, int offset, int faces, double distance) {}
    private final List<Copy<T>> copies;
    private int index;
    public SodiumDrawList(List<Copy<T>> copies) { this.copies = copies; }
    @Override public T getGraphicsState() {
        Copy<T> copy = copies.get(index);
        SodiumRenderContext.offset(copy.offset());
        return copy.state();
    }
    @Override public int getVisibleFaces() { return copies.get(index).faces(); }
    @Override public boolean hasNext() { return index < copies.size(); }
    @Override public void advance() { index++; }
}
