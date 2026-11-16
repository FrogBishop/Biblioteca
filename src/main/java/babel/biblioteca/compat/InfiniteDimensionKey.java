package babel.biblioteca.compat;

import net.minecraft.resources.Identifier;

/** Stable registry identity for the snapshot's dynamically created dimensions. */
public record InfiniteDimensionKey(Identifier location) {
    public InfiniteDimensionKey { java.util.Objects.requireNonNull(location); }
    @Override public String toString() { return location.toString(); }
}
