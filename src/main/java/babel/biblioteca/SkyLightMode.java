package babel.biblioteca;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Sky sources in a looping dimension; propagation and sampling remain periodic in both lit modes. */
public enum SkyLightMode {
    DISABLED("disabled"),
    LOOPING("looping"),
    HYBRID("hybrid");

    public static final SkyLightMode DEFAULT = LOOPING;
    public static final Codec<SkyLightMode> CODEC = Codec.STRING.comapFlatMap(id -> {
        for (SkyLightMode mode : values()) if (mode.id.equals(id)) return DataResult.success(mode);
        return DataResult.error(() -> "Unknown sky light mode: " + id);
    }, SkyLightMode::id);

    private final String id;
    SkyLightMode(String id) { this.id = id; }
    public String id() { return id; }
    public String translationKey() { return "biblioteca.sky_light." + id; }
    public SkyLightMode next() { return values()[(ordinal() + 1) % values().length]; }

    public static SkyLightMode byId(String id) {
        for (SkyLightMode mode : values()) if (mode.id.equals(id)) return mode;
        throw new IllegalArgumentException("Unknown sky light mode: " + id);
    }
}
