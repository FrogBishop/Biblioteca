package babel.biblioteca.client;

import babel.biblioteca.PendingLoopingConfig;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * Inserts a "Dimension Attributes…" button into the CreateWorldScreen footer area.
 */
public final class CreateWorldScreenInjector {

    private CreateWorldScreenInjector() {}

    public static void inject(CreateWorldScreen screen, int scaledWidth, int scaledHeight) {
        int btnWidth  = 120;
        int btnHeight = 20;
        int btnX      = scaledWidth / 2 - btnWidth - 4;
        int btnY      = scaledHeight - 52;

        Button btn = Button.builder(
                Component.translatable("biblioteca.gui.looping_button"),
                b -> openConfig(screen)
        ).bounds(btnX, btnY, btnWidth, btnHeight).build();

        Screens.getWidgets(screen).add(btn);
    }

    private static void openConfig(CreateWorldScreen parent) {
        Set<ResourceKey<Level>> looping = PendingLoopingConfig.hasPending()
                ? PendingLoopingConfig.getPendingLooping()
                : Set.of();
        Set<ResourceKey<Level>> beaconIgnoresSky = PendingLoopingConfig.hasPending()
                ? PendingLoopingConfig.getPendingBeaconIgnoresSky()
                : Set.of();
        Minecraft.getInstance().setScreenAndShow(new LoopingConfigScreen(parent, looping, beaconIgnoresSky));
    }
}
