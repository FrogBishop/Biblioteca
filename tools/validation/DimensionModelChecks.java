package babel.biblioteca.validation;

import babel.biblioteca.Compat;
import babel.biblioteca.PendingLoopingConfig;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.client.DimensionConfigModel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class DimensionModelChecks {
    public static void run() {
        var custom = Compat.dimensionKey(Compat.id("example", "orbit"));
        var other = Compat.dimensionKey(Compat.id("example", "caves"));
        AtomicReference<List<ResourceKey<Level>>> loaded = new AtomicReference<>(List.of(custom, Level.NETHER, Level.OVERWORLD, custom));
        DimensionConfigModel model = new DimensionConfigModel(loaded::get, Set.of(), Set.of(), Set.of(), Map.of());
        check(model.dimensions().equals(List.of(Level.OVERWORLD, Level.NETHER, custom)), "dimension IDs are deduplicated and ordered");
        for (int column=0;column<3;column++) model.toggle(custom,column);
        check(model.skyLightMode(custom)==SkyLightMode.DEFAULT, "new dimension sky default");
        check(model.cycleSkyLightMode(custom)==SkyLightMode.HYBRID, "first sky mode cycle");
        loaded.set(List.of(Level.OVERWORLD,other)); check(model.refresh(), "loaded dimension changes are observed");
        check(!model.selectedSkyLightModes().containsKey(custom), "removed dimension draft is excluded from saves");
        loaded.set(List.of(custom,Level.OVERWORLD,other)); model.refresh();
        check(model.enabled(custom,0) && model.skyLightMode(custom)==SkyLightMode.HYBRID, "restored dimensions retain drafts by ID");
        check(model.cycleSkyLightMode(custom)==SkyLightMode.DISABLED && model.cycleSkyLightMode(custom)==SkyLightMode.LOOPING, "three choices cycle back to default");
        model.cycleSkyLightMode(custom); model.apply();
        check(PendingLoopingConfig.getPendingLooping().equals(Set.of(custom)) && PendingLoopingConfig.getPendingSkyLightModes().equals(Map.of(custom,SkyLightMode.HYBRID)), "all attributes reach pending world configuration");
        loaded.set(List.of(Level.END)); model.apply();
        check(PendingLoopingConfig.getPendingLooping().isEmpty() && PendingLoopingConfig.getPendingSkyLightModes().isEmpty(), "Done observes the latest registry state");
        PendingLoopingConfig.consume();
        System.out.println("PASS: live dimension model: merge/order, reload/removal/restoration, three-mode cycling and pending creation settings");
    }
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
