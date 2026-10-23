package babel.biblioteca.client;

import babel.biblioteca.Compat;

import babel.biblioteca.PendingLoopingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * Builds the "Dimension Attributes…" button for the world-creation screen.
 *
 * The button is appended to the More tab's grid by CreateWorldScreenMoreTabMixin, where it shares
 * that tab's 210 px column width, its 8 px row spacing and its show/hide lifecycle.
 */
public final class CreateWorldScreenInjector {

    private CreateWorldScreenInjector() {}

    /** One More-tab row, sized like the Game Rules / Experiments / Data Packs buttons above it. */
    public static Button createButton(CreateWorldScreen parent) {
        return ClientCompat.button(Compat.translatable("biblioteca.gui.looping_button"),
                0, 0, 210, 20, button -> openConfig(parent));
    }

    public static void openConfig(CreateWorldScreen parent) {
        Set<ResourceKey<Level>> looping = PendingLoopingConfig.hasPending()
                ? PendingLoopingConfig.getPendingLooping()
                : Set.of();
        Set<ResourceKey<Level>> beaconIgnoresSky = PendingLoopingConfig.hasPending()
                ? PendingLoopingConfig.getPendingBeaconIgnoresSky()
                : Set.of();
        //? if >=26.1 {
        Minecraft.getInstance().setScreenAndShow(new LoopingConfigScreen(parent, looping, beaconIgnoresSky,
                PendingLoopingConfig.getPendingNoVoidFog(), PendingLoopingConfig.getPendingNoSkyLight()));
        //?} else {
        /*Minecraft.getInstance().setScreen(new LoopingConfigScreen(parent, looping, beaconIgnoresSky,
                PendingLoopingConfig.getPendingNoVoidFog(), PendingLoopingConfig.getPendingNoSkyLight()));
        *///?}
    }
}
