package babel.biblioteca.world;

import com.mojang.serialization.Codec;
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
 * Per-world persistent record of which dimensions have Y-looping enabled.
 * Stored in the overworld's data storage (saves/<world>/data/biblioteca/looping.dat).
 * Uses Codec-based serialisation (MC 26.x SavedData API).
 */
public class LoopingWorldData extends SavedData {

    private final Set<ResourceKey<Level>> loopingDimensions;

    public LoopingWorldData(Set<ResourceKey<Level>> dims) {
        this.loopingDimensions = new HashSet<>(dims);
    }

    /** Serialises as a JSON array of dimension resource-key strings. */
    public static final Codec<LoopingWorldData> CODEC =
            ResourceKey.codec(Registries.DIMENSION)
                    .listOf()
                    .xmap(
                            list -> new LoopingWorldData(new HashSet<>(list)),
                            data -> List.copyOf(data.loopingDimensions)
                    );

    /** Default: only the overworld loops. */
    public static final SavedDataType<LoopingWorldData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("biblioteca", "looping"),
            () -> new LoopingWorldData(new HashSet<>(Set.of(Level.OVERWORLD))),
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
}
