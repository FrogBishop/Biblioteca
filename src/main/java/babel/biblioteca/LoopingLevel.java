package babel.biblioteca;

/**
 * A level, or a pathfinding view of one, that reports whether its dimension loops.
 *
 * The question is asked on the hottest paths in the game - every block state lookup, every
 * collision query, every frustum test - and has to answer from a set of dimension keys each time.
 * The answer is cached on the level and invalidated by LoopingRegistry's revision counter when
 * attributes are loaded, changed, synchronized or cleared.
 */
public interface LoopingLevel {

    boolean biblioteca$isLooping();

    /** Immutable answer plus the revision it was computed for, so the pair cannot tear. */
    record Snapshot(int revision, boolean looping) {}
}
