package babel.biblioteca.world;

import babel.biblioteca.SkyLightMode;
import babel.biblioteca.Compat;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.5 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}
//? if >=1.21.5 <26.1 {
import net.minecraft.util.datafix.DataFixTypes;
//?}
//? if <1.21.5 {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixTypes;
*///?}
//? if >=1.20.5 <1.21.5 {
/*import net.minecraft.core.HolderLookup;
*///?}

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
        //? if <1.17 {
        /*super("biblioteca_looping");
        *///?}
        this.loopingDimensions = new HashSet<>(loopingDimensions);
        this.beaconIgnoresSky = new HashSet<>(beaconIgnoresSky);
        this.noVoidFog = new HashSet<>(noVoidFog);
        this.skyLightModes = new HashMap<>(skyLightModes);
        this.skyLightModes.values().removeIf(mode -> mode == SkyLightMode.DEFAULT);
    }

    private static final Codec<LoopingWorldData> RECORD = RecordCodecBuilder.create(instance -> instance.group(
            Compat.dimensionCodec().listOf()
                    .optionalFieldOf("looping", List.of())
                    .forGetter(data -> List.copyOf(data.loopingDimensions)),
            Compat.dimensionCodec().listOf()
                    .optionalFieldOf("beacon_ignores_sky", List.of())
                    .forGetter(data -> List.copyOf(data.beaconIgnoresSky)),
            Compat.dimensionCodec().listOf()
                    .optionalFieldOf("no_void_fog", List.of())
                    .forGetter(data -> List.copyOf(data.noVoidFog)),
            Codec.unboundedMap(Compat.dimensionCodec(), SkyLightMode.CODEC)
                    .optionalFieldOf("sky_light_mode", Map.of())
                    .forGetter(data -> Map.copyOf(data.skyLightModes)),
            Compat.dimensionCodec().listOf()
                    .optionalFieldOf("no_sky_light", List.of())
                    .forGetter(data -> List.of())
    ).apply(instance, (looping, beacon, noVoidFog, modes, disabled) -> {
        Map<ResourceKey<Level>, SkyLightMode> migrated = new HashMap<>();
        for (ResourceKey<Level> dimension : disabled) migrated.put(dimension, SkyLightMode.DISABLED);
        migrated.putAll(modes);
        return new LoopingWorldData(new HashSet<>(looping), new HashSet<>(beacon), new HashSet<>(noVoidFog), migrated);
    }));

    /** Worlds written before the later attributes existed hold a bare list of dimensions. */
    private static final Codec<LoopingWorldData> LEGACY = Compat.dimensionCodec()
            .listOf()
            .xmap(looping -> new LoopingWorldData(new HashSet<>(looping), Set.of(), Set.of(), Map.of()),
                    data -> List.copyOf(data.loopingDimensions));

    public static final Codec<LoopingWorldData> CODEC = Codec.either(RECORD, LEGACY)
            .xmap(either -> either.map(record -> record, legacy -> legacy),
                    data -> Either.<LoopingWorldData, LoopingWorldData>left(data));

    /** A new world starts with every attribute off; all attributes are opt-in per dimension. */
    //? if >=26.1 {
    public static final SavedDataType<LoopingWorldData> TYPE = new SavedDataType<>(
            Compat.id("biblioteca", "looping"),
            () -> new LoopingWorldData(Set.of(), Set.of(), Set.of(), Map.of()),
            CODEC,
            null
    );
    //?}
    //? if >=1.21.5 <26.1 {
    /*public static final SavedDataType<LoopingWorldData> TYPE = new SavedDataType<>(
            "biblioteca_looping",
            () -> new LoopingWorldData(Set.of(), Set.of(), Set.of(), Map.of()),
            CODEC,
            DataFixTypes.LEVEL
    );
    *///?}
    //? if >=1.20.5 <1.21.5 {
    /*public static final SavedData.Factory<LoopingWorldData> TYPE = new SavedData.Factory<>(
            () -> new LoopingWorldData(Set.of(), Set.of(), Set.of(), Map.of()),
            (tag, provider) -> decode(tag),
            DataFixTypes.LEVEL
    );
    *///?}
    //? if >=1.20.2 <1.20.5 {
    /*public static final SavedData.Factory<LoopingWorldData> TYPE = new SavedData.Factory<>(
            () -> new LoopingWorldData(Set.of(), Set.of(), Set.of(), Map.of()),
            tag -> decode(tag),
            DataFixTypes.LEVEL
    );
    *///?}

    //? if <1.21.5 {
    /*public static LoopingWorldData decode(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag)
                .result()
                .orElseGet(() -> new LoopingWorldData(Set.of(), Set.of(), Set.of(), Map.of()));
    }

    private CompoundTag writeTo(CompoundTag tag) {
        // DimensionDataStorage writes this tag as the file payload; the codec
        // fields therefore belong at the top level, not under a "data" key.
        return CODEC.encodeStart(NbtOps.INSTANCE, this)
                .result()
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .orElse(tag);
    }
    *///?}
    //? if >=1.20.5 <1.21.5 {
    /*@Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return writeTo(tag);
    }
    *///?}
    //? if <1.20.5 {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
        return writeTo(tag);
    }
    *///?}

    public static LoopingWorldData empty() {
        return new LoopingWorldData(Set.of(), Set.of(), Set.of(), Map.of());
    }

    //? if <1.17 {
    /*@Override
    public void load(CompoundTag tag) {
        LoopingWorldData decoded = decode(tag);
        this.loopingDimensions.clear(); this.loopingDimensions.addAll(decoded.loopingDimensions);
        this.beaconIgnoresSky.clear(); this.beaconIgnoresSky.addAll(decoded.beaconIgnoresSky);
        this.noVoidFog.clear(); this.noVoidFog.addAll(decoded.noVoidFog);
        this.skyLightModes.clear(); this.skyLightModes.putAll(decoded.skyLightModes);
    }
    *///?}

    public void setLooping(ResourceKey<Level> dimension, boolean enabled) {
        if (enabled) loopingDimensions.add(dimension);
        else loopingDimensions.remove(dimension);
        setDirty();
    }

    public boolean isLooping(ResourceKey<Level> dimension) { return loopingDimensions.contains(dimension); }
    public boolean beaconIgnoresSky(ResourceKey<Level> dimension) { return beaconIgnoresSky.contains(dimension); }

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
