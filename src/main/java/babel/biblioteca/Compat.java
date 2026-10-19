package babel.biblioteca;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The handful of names that moved between the releases this mod targets.
 *
 * Stonecutter rewrites {@code Identifier} to {@code ResourceLocation} before
 * 1.21.11, so the source keeps the modern spelling and only this helper has to
 * know which id factory exists in the active release.
 */
public final class Compat {

    private Compat() {}

    /** A namespaced id, created with the factory that exists in this release. */
    public static Identifier id(String namespace, String path) {
        //? if >=1.21.11 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return java.util.Objects.requireNonNull(Identifier.tryBuild(namespace, path),
                "Invalid identifier: " + namespace + ":" + path);
        *///?}
    }

    /** Parses an id written in a packet or a save file. */
    public static Identifier parseId(String value) {
        //? if >=1.21.11 {
        return Identifier.parse(value);
        //?} else {
        /*return java.util.Objects.requireNonNull(Identifier.tryParse(value),
                "Invalid identifier: " + value);
        *///?}
    }

    /** The id of a dimension key (location() before 1.21.11). */
    public static Identifier keyId(ResourceKey<Level> key) {
        //? if >=1.21.11 {
        return key.identifier();
        //?} else {
        /*return key.location();
        *///?}
    }
}
