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
 *   looping              the Y axis wraps around, minY touching maxY
 *   beacon_ignores_sky   beacon beams skip the vanilla "must see the sky" rule
 *   void_fog             show the vanilla void-fog darkening (the ring suppresses it by default)
 *   sky_light            keep the vanilla sky-light channel (the ring removes it by default)
 *
 * Every attribute is off for a new dimension. The void-fog and sky-light entries describe the
 * *vanilla phenomenon*, not the fix: with them off the ring keeps its uniform lighting, and turning
 * one on restores the vanilla effect for that dimension only.
 *
 * Stored in the overworld's data storage (saves/<world>/data/biblioteca/looping.dat).
 */
public class LoopingWorldData extends SavedData {

    private final Set<ResourceKey<Level>> loopingDimensions;
    private final Set<ResourceKey<Level>> beaconIgnoresSky;
    private final Set<ResourceKey<Level>> voidFog;
    private final Set<ResourceKey<Level>> skyLight;

    public LoopingWorldData(
            Set<ResourceKey<Level>> loopingDimensions,
            Set<ResourceKey<Level>> beaconIgnoresSky,
            Set<ResourceKey<Level>> voidFog,
            Set<ResourceKey<Level>> skyLight) {
        this.loopingDimensions = new HashSet<>(loopingDimensions);
        this.beaconIgnoresSky = new HashSet<>(beaconIgnoresSky);
        this.voidFog = new HashSet<>(voidFog);
        this.skyLight = new HashSet<>(skyLight);
    }

    private static final Codec<LoopingWorldData> RECORD = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("looping", List.of())
                    .forGetter(data -> List.copyOf(data.loopingDimensions)),
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("beacon_ignores_sky", List.of())
                    .forGetter(data -> List.copyOf(data.beaconIgnoresSky)),
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("void_fog", List.of())
                    .forGetter(data -> List.copyOf(data.voidFog)),
            ResourceKey.codec(Registries.DIMENSION).listOf()
                    .optionalFieldOf("sky_light", List.of())
                    .forGetter(data -> List.copyOf(data.skyLight))
    ).apply(instance, (looping, beacon, voidFog, skyLight) -> new LoopingWorldData(
            new HashSet<>(looping), new HashSet<>(beacon), new HashSet<>(voidFog), new HashSet<>(skyLight))));

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

    public boolean showsVoidFog(ResourceKey<Level> dimension) {
        return voidFog.contains(dimension);
    }

    public void setVoidFog(ResourceKey<Level> dimension, boolean shown) {
        if (shown) voidFog.add(dimension);
        else voidFog.remove(dimension);
        setDirty();
    }

    public Set<ResourceKey<Level>> getVoidFogDimensions() {
        return Set.copyOf(voidFog);
    }

    public boolean keepsSkyLight(ResourceKey<Level> dimension) {
        return skyLight.contains(dimension);
    }

    public void setSkyLight(ResourceKey<Level> dimension, boolean kept) {
        if (kept) skyLight.add(dimension);
        else skyLight.remove(dimension);
        setDirty();
    }

    public Set<ResourceKey<Level>> getSkyLightDimensions() {
        return Set.copyOf(skyLight);
    }
}
