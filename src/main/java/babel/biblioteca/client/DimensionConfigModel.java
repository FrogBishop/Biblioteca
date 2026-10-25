package babel.biblioteca.client;

import babel.biblioteca.PendingLoopingConfig;
import babel.biblioteca.SkyLightMode;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Editable attributes keyed by dimension ID, following the last successfully loaded pack context. */
public final class DimensionConfigModel {
    private static final List<ResourceKey<Level>> VANILLA_ORDER = List.of(Level.OVERWORLD, Level.NETHER, Level.END);
    private final Supplier<List<ResourceKey<Level>>> context;
    private final Map<ResourceKey<Level>, boolean[]> values = new HashMap<>();
    private final Map<ResourceKey<Level>, SkyLightMode> skyModes = new HashMap<>();
    private List<ResourceKey<Level>> dimensions = List.of();

    public DimensionConfigModel(Supplier<List<ResourceKey<Level>>> context,
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

    /** Read the latest valid preset/pack registry. Keep widgets when its dimension IDs are unchanged. */
    public boolean refresh() {
        List<ResourceKey<Level>> updated = context.get().stream()
                .distinct()
                .sorted(Comparator.<ResourceKey<Level>>comparingInt(key -> {
                    int index = VANILLA_ORDER.indexOf(key);
                    return index < 0 ? VANILLA_ORDER.size() : index;
                }).thenComparing(key -> babel.biblioteca.Compat.keyId(key).toString()))
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
