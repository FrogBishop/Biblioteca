package babel.biblioteca.world;

import babel.biblioteca.SkyLightMode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-world record of the per-dimension attributes this mod adds:
 *
 *   looping             the Y axis wraps around, identifying minY with minY + height
 *   beacon_ignores_sky   beacon beams skip the vanilla "must see the sky" rule
 *   no_void_fog          disable the vanilla void-fog darkening in looping dimensions
 *   sky_light_mode       disabled, looping, or vanilla sky sources with periodic propagation
 *
 * Boolean attributes default to false; sky light defaults to LOOPING. Old no_sky_light entries
 * migrate to DISABLED, with an explicit new sky_light_mode value taking precedence.
 *
 * Stored in the overworld's data storage (saves/<world>/data/biblioteca/looping.dat).
 */
public class LoopingWorldData extends SavedData {

    private final Set<ResourceKey<Level>> loopingDimensions;
    private final Set<ResourceKey<Level>> beaconIgnoresSky;
    private final Set<ResourceKey<Level>> noVoidFog;
    private final Map<ResourceKey<Level>, SkyLightMode> skyLightModes;

    public LoopingWorldData(
            Set<ResourceKey<Level>> loopingDimensions,
            Set<ResourceKey<Level>> beaconIgnoresSky,
            Set<ResourceKey<Level>> noVoidFog,
            Map<ResourceKey<Level>, SkyLightMode> skyLightModes) {
        this.loopingDimensions = new HashSet<>(loopingDimensions);
        this.beaconIgnoresSky = new HashSet<>(beaconIgnoresSky);
        this.noVoidFog = new HashSet<>(noVoidFog);
        this.skyLightModes = new HashMap<>(skyLightModes);
        this.skyLightModes.values().removeIf(mode -> mode == SkyLightMode.DEFAULT);
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
            Codec.unboundedMap(ResourceKey.codec(Registries.DIMENSION), SkyLightMode.CODEC)
                    .optionalFieldOf("sky_light_mode", Map.of())
                    .forGetter(data -> Map.copyOf(data.skyLightModes)),
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("no_sky_light", List.of())
                    .forGetter(data -> List.of())
    ).apply(instance, (looping, beacon, noVoidFog, modes, disabled) -> {
        Map<ResourceKey<Level>, SkyLightMode> migrated = new HashMap<>();
        for (ResourceKey<Level> dimension : disabled) migrated.put(dimension, SkyLightMode.DISABLED);
        migrated.putAll(modes);
        return new LoopingWorldData(new HashSet<>(looping), new HashSet<>(beacon), new HashSet<>(noVoidFog), migrated);
    }));

    /** Worlds written before the later attributes existed hold a bare list of dimensions. */
    private static final Codec<LoopingWorldData> LEGACY = ResourceKey.codec(Registries.DIMENSION)
            .listOf()
            .xmap(looping -> new LoopingWorldData(new HashSet<>(looping), Set.of(), Set.of(), Map.of()),
                    data -> List.copyOf(data.loopingDimensions));

    public static final Codec<LoopingWorldData> CODEC = Codec.either(RECORD, LEGACY)
            .xmap(either -> either.map(record -> record, legacy -> legacy),
                    data -> Either.<LoopingWorldData, LoopingWorldData>left(data));

    /** A new world has looping off and selects full periodic sky when looping is enabled. */
    public static final SavedDataType<LoopingWorldData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("biblioteca", "looping"),
            () -> new LoopingWorldData(Set.of(), Set.of(), Set.of(), Map.of()),
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

    public void setSkyLightMode(ResourceKey<Level> dimension, SkyLightMode mode) {
        if (mode == SkyLightMode.DEFAULT) skyLightModes.remove(dimension);
        else skyLightModes.put(dimension, mode);
        setDirty();
    }

    public SkyLightMode getSkyLightMode(ResourceKey<Level> dimension) {
        return skyLightModes.getOrDefault(dimension, SkyLightMode.DEFAULT);
    }

    public Map<ResourceKey<Level>, SkyLightMode> getSkyLightModes() {
        return Map.copyOf(skyLightModes);
    }
}
