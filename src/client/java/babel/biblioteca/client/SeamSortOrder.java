package babel.biblioteca.client;

import net.minecraft.core.SectionPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which periodic row each section is currently drawn at for the camera, so its
 * translucent geometry can be sorted for the row it is actually seen at.
 *
 * Vanilla sorts a section's translucent quads for an origin derived from the camera and the
 * section's own coordinates, and re-sorts when that view changes. A section mirrored onto another row
 * is drawn a whole world height away from its own coordinates, so that origin is off by exactly that
 * distance and the quads inside it end up ordered as if the camera were hundreds of blocks away -
 * water surfaces then blend in the wrong order and read as an extra face on every block.
 *
 * Only the row nearest the camera is recorded. It is the one the player can look at closely, and one
 * mesh can hold only one sorting state anyway; rows further away reuse that ordering, which is
 * acceptable because they sit behind fog.
 *
 * Written from the extraction pass on the render thread and read from the section resort tasks, which
 * run on worker threads, hence the concurrent map. It is small and rewritten every frame.
 */
public final class SeamSortOrder {

    private static final Map<Long, Integer> CURRENT = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> NEXT = new ConcurrentHashMap<>();

    private SeamSortOrder() {}

    /** Starts a new frame's mapping; committed by commit(). */
    public static void beginFrame() {
        NEXT.clear();
    }

    /** @param sectionShift section-grid offset (not blocks) the section is drawn at */
    public static void putNext(long sectionNode, int sectionShift) {
        NEXT.put(sectionNode, sectionShift);
    }

    /**
     * Publishes this frame's mapping.
     *
     * @return true when it differs from the previous frame, which is the caller's cue to ask for a
     *         translucent re-sort - the meshes were sorted before this mapping existed
     */
    public static boolean commit() {
        if (CURRENT.equals(NEXT)) return false;

        CURRENT.clear();
        CURRENT.putAll(NEXT);
        return true;
    }

    private static int shiftOf(long sectionNode) {
        Integer shift = CURRENT.get(sectionNode);
        return shift == null ? 0 : shift;
    }

    /** The section coordinates the section is drawn at, for use as a sorting origin. */
    public static SectionPos drawnPos(SectionPos pos) {
        int shift = shiftOf(pos.asLong());
        if (shift == 0) return pos;

        return SectionPos.of(pos.x(), pos.y() + shift, pos.z());
    }

    /** The section node the section is drawn at, for use as a translucency viewpoint. */
    public static long drawnNode(long sectionNode) {
        int shift = shiftOf(sectionNode);
        if (shift == 0) return sectionNode;

        return SectionPos.asLong(
                SectionPos.x(sectionNode), SectionPos.y(sectionNode) + shift, SectionPos.z(sectionNode));
    }
}
