package babel.biblioteca;

import java.util.Set;

/** Internal access to a native swept-movement record; translation never changes displacement. */
public interface SeamMovement {
    void biblioteca$shiftY(double offset);
    double biblioteca$toY();

    static void shiftAll(Iterable<?> records, double offset, Set<Object> shifted) {
        if (records == null) return; // Entity's initial position is committed during construction.
        for (Object record : records) {
            if (record instanceof SeamMovement movement) {
                if (shifted.add(record)) movement.biblioteca$shiftY(offset);
            } else if (record instanceof Iterable<?> nested) shiftAll(nested, offset, shifted);
        }
    }

    static boolean endsAt(Iterable<?> records, double y) {
        if (records == null) return false;
        Object last = null;
        for (Object record : records) last = record;
        return last instanceof SeamMovement movement ? Math.abs(movement.biblioteca$toY() - y) < 1.0e-7
                : last instanceof Iterable<?> nested && endsAt(nested, y);
    }
}
