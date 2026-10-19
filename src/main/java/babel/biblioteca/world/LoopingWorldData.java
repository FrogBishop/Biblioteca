package babel.biblioteca.world;

import babel.biblioteca.Compat;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
import java.util.List;
import java.util.Set;

/**
 * Per-world record of the per-dimension attributes this mod adds:
 *
 *   looping              the Y axis wraps around, minY touching maxY
 *   beacon_ignores_sky   beacon beams skip the vanilla "must see the sky" rule
 *
 * Stored in the overworld's data storage (saves/<world>/data/biblioteca/looping.dat).
 */
public class LoopingWorldData extends SavedData {

    private final Set<ResourceKey<Level>> loopingDimensions;
    private final Set<ResourceKey<Level>> beaconIgnoresSky;

    public LoopingWorldData(Set<ResourceKey<Level>> loopingDimensions, Set<ResourceKey<Level>> beaconIgnoresSky) {
        this.loopingDimensions = new HashSet<>(loopingDimensions);
        this.beaconIgnoresSky = new HashSet<>(beaconIgnoresSky);
    }

    private static final Codec<LoopingWorldData> RECORD = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("looping", List.of())
                    .forGetter(data -> List.copyOf(data.loopingDimensions)),
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("beacon_ignores_sky", List.of())
                    .forGetter(data -> List.copyOf(data.beaconIgnoresSky))
    ).apply(instance, (looping, beacon) -> new LoopingWorldData(new HashSet<>(looping), new HashSet<>(beacon))));

    /** Worlds written before the second attribute existed hold a bare list of dimensions. */
    private static final Codec<LoopingWorldData> LEGACY = ResourceKey.codec(Registries.DIMENSION)
            .listOf()
            .xmap(looping -> new LoopingWorldData(new HashSet<>(looping), Set.of()),
                    data -> List.copyOf(data.loopingDimensions));

    public static final Codec<LoopingWorldData> CODEC = Codec.either(RECORD, LEGACY)
            .xmap(either -> either.map(record -> record, legacy -> legacy),
                    data -> Either.<LoopingWorldData, LoopingWorldData>left(data));

    /** A new world starts with every attribute off; both are opt-in per dimension. */
    //? if >=26.1 {
    public static final SavedDataType<LoopingWorldData> TYPE = new SavedDataType<>(
            Compat.id("biblioteca", "looping"),
            () -> new LoopingWorldData(Set.of(), Set.of()),
            CODEC,
            null
    );
    //?}
    //? if >=1.21.5 <26.1 {
    /*public static final SavedDataType<LoopingWorldData> TYPE = new SavedDataType<>(
            "biblioteca_looping",
            () -> new LoopingWorldData(Set.of(), Set.of()),
            CODEC,
            DataFixTypes.LEVEL
    );
    *///?}
    //? if >=1.20.5 <1.21.5 {
    /*public static final SavedData.Factory<LoopingWorldData> TYPE = new SavedData.Factory<>(
            () -> new LoopingWorldData(Set.of(), Set.of()),
            (tag, provider) -> decode(tag),
            DataFixTypes.LEVEL
    );
    *///?}
    //? if <1.20.5 {
    /*public static final SavedData.Factory<LoopingWorldData> TYPE = new SavedData.Factory<>(
            () -> new LoopingWorldData(Set.of(), Set.of()),
            tag -> decode(tag),
            DataFixTypes.LEVEL
    );
    *///?}

    //? if <1.21.5 {
    /*private static LoopingWorldData decode(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag)
                .result()
                .orElseGet(() -> new LoopingWorldData(Set.of(), Set.of()));
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

    public boolean isLooping(ResourceKey<Level> dimension) {
        return loopingDimensions.contains(dimension);
    }

    public void setLooping(ResourceKey<Level> dimension, boolean enabled) {
        if (enabled) loopingDimensions.add(dimension);
        else loopingDimensions.remove(dimension);
        setDirty();
    }

    public Set<ResourceKey<Level>> getLoopingDimensions() {
        return Set.copyOf(loopingDimensions);
    }

    public boolean beaconIgnoresSky(ResourceKey<Level> dimension) {
        return beaconIgnoresSky.contains(dimension);
    }

    public void setBeaconIgnoresSky(ResourceKey<Level> dimension, boolean ignored) {
        if (ignored) beaconIgnoresSky.add(dimension);
        else beaconIgnoresSky.remove(dimension);
        setDirty();
    }

    public Set<ResourceKey<Level>> getBeaconIgnoresSky() {
        return Set.copyOf(beaconIgnoresSky);
    }
}
