package babel.biblioteca.client;

import babel.biblioteca.PendingLoopingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * Builds the "Dimension Attributes…" button for the world-creation screen.
 *
 * The button is appended to the More tab's grid by CreateWorldScreenMoreTabMixin, where it shares
 * that tab's 210 px column width, its 8 px row spacing and its show/hide lifecycle.
 */
public final class CreateWorldScreenInjector {

    private CreateWorldScreenInjector() {}

    /** One More-tab row, sized like the Game Rules / Experiments / Data Packs buttons above it. */
    public static Button createButton() {
        return Button.builder(
                Component.translatable("biblioteca.gui.looping_button"),
                button -> {
                    if (Minecraft.getInstance().gui.screen() instanceof CreateWorldScreen parent) {
                        openConfig(parent);
                    }
                })
                .width(210)
                .build();
    }

    private static void openConfig(CreateWorldScreen parent) {
        // The getters are empty until the screen has been confirmed once, so a fresh world opens
        // boolean toggles off and the default sky mode without a separate "nothing pending" branch.
        Minecraft.getInstance().setScreenAndShow(new LoopingConfigScreen(
                parent,
                new DimensionConfigModel(parent.getUiState()::getSettings,
                        PendingLoopingConfig.getPendingLooping(),
                        PendingLoopingConfig.getPendingBeaconIgnoresSky(),
                        PendingLoopingConfig.getPendingNoVoidFog(),
                        PendingLoopingConfig.getPendingSkyLightModes())));
    }
}
