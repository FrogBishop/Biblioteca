package babel.biblioteca.client;

import babel.biblioteca.PendingLoopingConfig;
import babel.biblioteca.SkyLightMode;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Editable attributes keyed by dimension ID, following the last successfully loaded pack context. */
public final class DimensionConfigModel {
    private static final List<ResourceKey<Level>> VANILLA_ORDER = List.of(Level.OVERWORLD, Level.NETHER, Level.END);
    private final Supplier<WorldCreationContext> context;
    private final Map<ResourceKey<Level>, boolean[]> values = new HashMap<>();
    private final Map<ResourceKey<Level>, SkyLightMode> skyModes = new HashMap<>();
    private WorldCreationContext lastContext;
    private List<ResourceKey<Level>> dimensions = List.of();

    public DimensionConfigModel(Supplier<WorldCreationContext> context,
            Set<ResourceKey<Level>> looping, Set<ResourceKey<Level>> beacon,
            Set<ResourceKey<Level>> noVoidFog, Map<ResourceKey<Level>, SkyLightMode> skyLightModes) {
        this.context = context;
        skyModes.putAll(skyLightModes);
        List<Set<ResourceKey<Level>>> initial = List.of(looping, beacon, noVoidFog);
        for (int column = 0; column < initial.size(); column++) {
            for (ResourceKey<Level> dimension : initial.get(column)) {
                values.computeIfAbsent(dimension, key -> new boolean[3])[column] = true;
            }
        }
        refresh();
    }

    /** Contexts are replaced after pack validation or preset changes, never mutated in place. */
    public boolean refresh() {
        WorldCreationContext current = context.get();
        if (current == lastContext) return false;
        lastContext = current;
        // Match WorldDimensions.bake: dimensions from both the selected preset and loaded packs
        // are used to create the world. Dimension *types* alone do not create dimensions.
        List<ResourceKey<Level>> updated = Stream.concat(
                        current.selectedDimensions().levels().stream(),
                        current.datapackDimensions().registryKeySet().stream().map(Registries::levelStemToLevel))
                .distinct()
                .sorted(Comparator.<ResourceKey<Level>>comparingInt(key -> {
                    int index = VANILLA_ORDER.indexOf(key);
                    return index < 0 ? VANILLA_ORDER.size() : index;
                }).thenComparing(key -> key.identifier().toString()))
                .toList();
        if (updated.equals(dimensions)) return false;
        dimensions = updated;
        for (ResourceKey<Level> dimension : dimensions) values.computeIfAbsent(dimension, key -> new boolean[3]);
        return true;
    }

    public List<ResourceKey<Level>> dimensions() { return dimensions; }
    public boolean enabled(ResourceKey<Level> dimension, int column) { return values.get(dimension)[column]; }
    public boolean toggle(ResourceKey<Level> dimension, int column) {
        boolean[] row = values.get(dimension);
        return row[column] = !row[column];
    }

    public SkyLightMode skyLightMode(ResourceKey<Level> dimension) {
        return skyModes.getOrDefault(dimension, SkyLightMode.DEFAULT);
    }

    public SkyLightMode cycleSkyLightMode(ResourceKey<Level> dimension) {
        SkyLightMode next = skyLightMode(dimension).next();
        skyModes.put(dimension, next);
        return next;
    }

    public Map<ResourceKey<Level>, SkyLightMode> selectedSkyLightModes() {
        Map<ResourceKey<Level>, SkyLightMode> selected = new HashMap<>();
        for (ResourceKey<Level> dimension : dimensions) {
            SkyLightMode mode = skyLightMode(dimension);
            if (mode != SkyLightMode.DEFAULT) selected.put(dimension, mode);
        }
        return selected;
    }

    /** Hidden dimensions keep their draft values in case a reload restores them, but are not saved. */
    public Set<ResourceKey<Level>> selected(int column) {
        Set<ResourceKey<Level>> selected = new HashSet<>();
        for (ResourceKey<Level> dimension : dimensions) if (enabled(dimension, column)) selected.add(dimension);
        return selected;
    }

    public void apply() {
        refresh();
        PendingLoopingConfig.set(selected(0), selected(1), selected(2), selectedSkyLightModes());
    }
}
