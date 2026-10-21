package babel.biblioteca.world;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-world record of the per-dimension attributes this mod adds:
 *
 *   looping             the Y axis wraps around, identifying minY with minY + height
 *   beacon_ignores_sky   beacon beams skip the vanilla "must see the sky" rule
 *   no_void_fog          disable the vanilla void-fog darkening in looping dimensions
 *   no_sky_light         disable the vanilla sky-light channel in looping dimensions
 *
 * Every attribute defaults to false for a new dimension. The two lighting overrides therefore
 * preserve vanilla effects by default; true disables the corresponding effect in that dimension.
 *
 * Stored in the overworld's data storage (saves/<world>/data/biblioteca/looping.dat).
 */
public class LoopingWorldData extends SavedData {

    private final Set<ResourceKey<Level>> loopingDimensions;
    private final Set<ResourceKey<Level>> beaconIgnoresSky;
    private final Set<ResourceKey<Level>> noVoidFog;
    private final Set<ResourceKey<Level>> noSkyLight;

    public LoopingWorldData(
            Set<ResourceKey<Level>> loopingDimensions,
            Set<ResourceKey<Level>> beaconIgnoresSky,
            Set<ResourceKey<Level>> noVoidFog,
            Set<ResourceKey<Level>> noSkyLight) {
        this.loopingDimensions = new HashSet<>(loopingDimensions);
        this.beaconIgnoresSky = new HashSet<>(beaconIgnoresSky);
        this.noVoidFog = new HashSet<>(noVoidFog);
        this.noSkyLight = new HashSet<>(noSkyLight);
    }

    private static final Codec<LoopingWorldData> RECORD = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("looping", List.of())
                    .forGetter(data -> List.copyOf(data.loopingDimensions)),
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("beacon_ignores_sky", List.of())
                    .forGetter(data -> List.copyOf(data.beaconIgnoresSky)),
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("no_void_fog", List.of())
                    .forGetter(data -> List.copyOf(data.noVoidFog)),
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("no_sky_light", List.of())
                    .forGetter(data -> List.copyOf(data.noSkyLight))
    ).apply(instance, (looping, beacon, noVoidFog, noSkyLight) -> new LoopingWorldData(
            new HashSet<>(looping), new HashSet<>(beacon), new HashSet<>(noVoidFog), new HashSet<>(noSkyLight))));

    /** Worlds written before the later attributes existed hold a bare list of dimensions. */
    private static final Codec<LoopingWorldData> LEGACY = ResourceKey.codec(Registries.DIMENSION)
            .listOf()
            .xmap(looping -> new LoopingWorldData(new HashSet<>(looping), Set.of(), Set.of(), Set.of()),
                    data -> List.copyOf(data.loopingDimensions));

    public static final Codec<LoopingWorldData> CODEC = Codec.either(RECORD, LEGACY)
            .xmap(either -> either.map(record -> record, legacy -> legacy),
                    data -> Either.<LoopingWorldData, LoopingWorldData>left(data));

    /** A new world starts with every attribute off; all of them are opt-in per dimension. */
    public static final SavedDataType<LoopingWorldData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("biblioteca", "looping"),
            () -> new LoopingWorldData(Set.of(), Set.of(), Set.of(), Set.of()),
            CODEC,
            null
    );

    public void setLooping(ResourceKey<Level> dimension, boolean enabled) {
        if (enabled) loopingDimensions.add(dimension);
        else loopingDimensions.remove(dimension);
        setDirty();
    }

    public Set<ResourceKey<Level>> getLoopingDimensions() {
        return Set.copyOf(loopingDimensions);
    }

    public void setBeaconIgnoresSky(ResourceKey<Level> dimension, boolean ignored) {
        if (ignored) beaconIgnoresSky.add(dimension);
        else beaconIgnoresSky.remove(dimension);
        setDirty();
    }

    public Set<ResourceKey<Level>> getBeaconIgnoresSky() {
        return Set.copyOf(beaconIgnoresSky);
    }

    public void setNoVoidFog(ResourceKey<Level> dimension, boolean disabled) {
        if (disabled) noVoidFog.add(dimension);
        else noVoidFog.remove(dimension);
        setDirty();
    }

    public Set<ResourceKey<Level>> getNoVoidFogDimensions() {
        return Set.copyOf(noVoidFog);
    }

    public void setNoSkyLight(ResourceKey<Level> dimension, boolean disabled) {
        if (disabled) noSkyLight.add(dimension);
        else noSkyLight.remove(dimension);
        setDirty();
    }

    public Set<ResourceKey<Level>> getNoSkyLightDimensions() {
        return Set.copyOf(noSkyLight);
    }
}
