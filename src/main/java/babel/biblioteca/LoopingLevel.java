package babel.biblioteca;

/**
 * A level that remembers whether its dimension loops.
 *
 * The question is asked on the hottest paths in the game - every block state lookup, every
 * collision query, every frustum test - and has to answer from a set of dimension keys each time.
 * The answer only changes when an operator changes a dimension attribute, so it is cached on the
 * level and invalidated by LoopingRegistry's revision counter.
 */
public interface LoopingLevel {

    boolean biblioteca$isLooping();

    /** Immutable answer plus the revision it was computed for, so the pair cannot tear. */
    record Snapshot(int revision, boolean looping) {}
}
