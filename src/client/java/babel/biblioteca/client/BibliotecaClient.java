package babel.biblioteca.client;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.network.LoopingStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

public class BibliotecaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Receive S2C looping-state packet and populate the client-side cache.
        ClientPlayNetworking.registerGlobalReceiver(
                LoopingStatePayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> LoopingDimensionCache.applyPayload(payload)
                )
        );

        // Wire the client checkers into the shared LoopingRegistry.
        LoopingRegistry.setClientChecker(LoopingDimensionCache::isLooping);
        LoopingRegistry.setClientBeaconChecker(LoopingDimensionCache::beaconsIgnoreSky);

        // Inject the "Dimension Attributes…" button into the world-creation screen.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof CreateWorldScreen cws) {
                CreateWorldScreenInjector.inject(cws, scaledWidth, scaledHeight);
            }

            // Inject the "Loop Render Range" slider into Video Settings.
            if (screen instanceof VideoSettingsScreen vss) {
                injectLoopingRenderRangeSlider(client, vss);
            }
        });
    }

    /**
     * Finds the OptionsList inside VideoSettingsScreen via the widget list
     * and appends the Loop Render Range slider to it.
     *
     * We cannot @Shadow OptionsSubScreen.list from a VideoSettingsScreen mixin
     * because @Shadow only searches the direct target class, not its parents.
     * ScreenEvents.AFTER_INIT fires after init() has run, so all widgets
     * (including the OptionsList) are already added and findable.
     */
    private static void injectLoopingRenderRangeSlider(Minecraft client, VideoSettingsScreen vss) {
        // OptionsList is the only AbstractWidget subclass of that type in the screen.
        for (AbstractWidget w : Screens.getWidgets(vss)) {
            if (w instanceof OptionsList list) {
                list.addBig(LoopingRenderSettings.option());
                break;
            }
        }
    }
}
