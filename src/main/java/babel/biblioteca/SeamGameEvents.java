package babel.biblioteca;

import net.minecraft.world.level.Level;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** One event visits each canonical section registry once, even in a one-section world. */
public final class SeamGameEvents {
    private record Dispatch(Level level, Set<Object> visited) {}
    private static final ThreadLocal<Dispatch> CURRENT = new ThreadLocal<>();
    private SeamGameEvents() {}

    public static void dispatch(Level level, Runnable action) {
        if (!LoopingRegistry.isLooping(level)) { action.run(); return; }
        Dispatch previous = CURRENT.get();
        CURRENT.set(new Dispatch(level, Collections.newSetFromMap(new IdentityHashMap<>())));
        try { action.run(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static int section(Level level, int section) {
        if (!LoopingRegistry.isLooping(level)) return section;
        int minimum = Compat.minSection(level), count = Compat.sectionCount(level);
        if (count <= 0) return section;
        return minimum + Math.floorMod(section - minimum, count);
    }

    public static <T> T once(Level level, T registry, T empty) {
        Dispatch context = CURRENT.get();
        return LoopingRegistry.isLooping(level) && context != null && context.level == level && !context.visited.add(registry) ? empty : registry;
    }
}
