package babel.biblioteca.client;

import babel.biblioteca.PendingLoopingConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * World-creation sub-screen that lets the player choose which vanilla dimensions
 * will have Y-looping enabled. Opens from the "Looping…" button injected into
 * CreateWorldScreen.
 */
public class LoopingConfigScreen extends Screen {

    private static final Component TITLE =
            Component.translatable("biblioteca.gui.looping_config.title");

    private final Screen parent;
    private final Set<ResourceKey<Level>> initialEnabled;

    // We use CheckboxButton (vanilla widget) — store state manually since
    // Checkbox.selected() is client-only and the Checkbox widget API is stable.
    private boolean overworldEnabled;
    private boolean netherEnabled;
    private boolean endEnabled;

    public LoopingConfigScreen(Screen parent, Set<ResourceKey<Level>> initialEnabled) {
        super(TITLE);
        this.parent       = parent;
        this.initialEnabled = initialEnabled;
        this.overworldEnabled = initialEnabled.contains(Level.OVERWORLD);
        this.netherEnabled    = initialEnabled.contains(Level.NETHER);
        this.endEnabled       = initialEnabled.contains(Level.END);
    }

    @Override
    protected void init() {
        int centreX = width / 2;
        int startY  = height / 2 - 50;
        int rowH    = 24;

        // Toggle buttons (tri-state toggling: click = flip boolean).
        addRenderableWidget(Button.builder(
                overworldLabel(), b -> { overworldEnabled = !overworldEnabled; b.setMessage(overworldLabel()); })
                .bounds(centreX - 80, startY, 160, 20).build());

        addRenderableWidget(Button.builder(
                netherLabel(), b -> { netherEnabled = !netherEnabled; b.setMessage(netherLabel()); })
                .bounds(centreX - 80, startY + rowH, 160, 20).build());

        addRenderableWidget(Button.builder(
                endLabel(), b -> { endEnabled = !endEnabled; b.setMessage(endLabel()); })
                .bounds(centreX - 80, startY + rowH * 2, 160, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> applyAndClose())
                .bounds(centreX - 75, startY + rowH * 3 + 8, 150, 20).build());
    }

    private Component overworldLabel() {
        return Component.translatable("biblioteca.gui.looping_config.overworld")
                .append(": ").append(boolStr(overworldEnabled));
    }
    private Component netherLabel() {
        return Component.translatable("biblioteca.gui.looping_config.nether")
                .append(": ").append(boolStr(netherEnabled));
    }
    private Component endLabel() {
        return Component.translatable("biblioteca.gui.looping_config.end")
                .append(": ").append(boolStr(endEnabled));
    }
    private Component boolStr(boolean v) {
        return v ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    private void applyAndClose() {
        Set<ResourceKey<Level>> enabled = new HashSet<>();
        if (overworldEnabled) enabled.add(Level.OVERWORLD);
        if (netherEnabled)    enabled.add(Level.NETHER);
        if (endEnabled)       enabled.add(Level.END);
        PendingLoopingConfig.set(enabled);
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 70, 0xFFFFFF);
        graphics.centeredText(font, Component.translatable("biblioteca.gui.looping_config.note"),
                width / 2, height / 2 + 50, 0xAAAAAA);
    }
}
