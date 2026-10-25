package babel.biblioteca.client;

import babel.biblioteca.Compat;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.stream.Stream;

/** Reads the same successfully loaded settings that vanilla will use to create the world. */
public final class CreateWorldDimensions {
    private CreateWorldDimensions() {}

    public static List<ResourceKey<Level>> loaded(CreateWorldScreen parent) {
        //? if >=1.19.4 {
        var context = parent.getUiState().getSettings();
        //?} else if >=1.19.3 {
        /*var context = parent.worldGenSettingsComponent.settings();
        *///?}
        //? if >=1.19.3 {
        return fromContext(context);
        //?} else if >=1.19 {
        /*return parent.worldGenSettingsComponent.settings().worldGenSettings().dimensions().keySet().stream()
                .map(Compat::dimensionKey).toList();
        *///?}
        //? if <1.19 {
        /*return parent.worldGenSettingsComponent.makeSettings(false).dimensions().keySet().stream()
                .map(Compat::dimensionKey).toList();
        *///?}
    }

    //? if >=1.19.3 {
    public static List<ResourceKey<Level>> fromContext(net.minecraft.client.gui.screens.worldselection.WorldCreationContext context) {
        return Stream.concat(context.selectedDimensions().levels().stream(),
                context.datapackDimensions().keySet().stream().map(Compat::dimensionKey)).toList();
    }
    //?}
}
