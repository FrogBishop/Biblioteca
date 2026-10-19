package babel.biblioteca.client;

import babel.biblioteca.PendingLoopingConfig;
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
 * World-creation sub-screen for the per-dimension attributes of this mod:
 *
 *   left column   does the Y axis loop
 *   right column  does a beacon beam ignore the sky check
 *
 * Opens from the "Dimension Attributes…" button injected into CreateWorldScreen.
 */
public class LoopingConfigScreen extends Screen {

    private static final Component TITLE =
            Component.translatable("biblioteca.gui.looping_config.title");

    private static final String OVERWORLD_KEY = "biblioteca.gui.looping_config.overworld";
    private static final String NETHER_KEY    = "biblioteca.gui.looping_config.nether";
    private static final String END_KEY       = "biblioteca.gui.looping_config.end";

    private final Screen parent;

    // Widget state is kept here rather than read back from the buttons.
    private boolean overworldLooping;
    private boolean netherLooping;
    private boolean endLooping;
    private boolean overworldBeacon;
    private boolean netherBeacon;
    private boolean endBeacon;

    public LoopingConfigScreen(Screen parent,
                               Set<ResourceKey<Level>> initialLooping,
                               Set<ResourceKey<Level>> initialBeaconIgnoresSky) {
        super(TITLE);
        this.parent = parent;
        this.overworldLooping = initialLooping.contains(Level.OVERWORLD);
        this.netherLooping    = initialLooping.contains(Level.NETHER);
        this.endLooping       = initialLooping.contains(Level.END);
        this.overworldBeacon  = initialBeaconIgnoresSky.contains(Level.OVERWORLD);
        this.netherBeacon     = initialBeaconIgnoresSky.contains(Level.NETHER);
        this.endBeacon        = initialBeaconIgnoresSky.contains(Level.END);
    }

    private int columnWidth() {
        return Math.min(150, (width - 30) / 2);
    }

    private int leftX() {
        return width / 2 - columnWidth() - 5;
    }

    private int rightX() {
        return width / 2 + 5;
    }

    private int rowY(int row) {
        return height / 2 - 40 + row * 24;
    }

    @Override
    protected void init() {
        int colW = columnWidth();

        addRenderableWidget(Button.builder(loopingLabel(OVERWORLD_KEY, overworldLooping), b -> {
            overworldLooping = !overworldLooping;
            b.setMessage(loopingLabel(OVERWORLD_KEY, overworldLooping));
        }).bounds(leftX(), rowY(0), colW, 20).build());

        addRenderableWidget(Button.builder(loopingLabel(NETHER_KEY, netherLooping), b -> {
            netherLooping = !netherLooping;
            b.setMessage(loopingLabel(NETHER_KEY, netherLooping));
        }).bounds(leftX(), rowY(1), colW, 20).build());

        addRenderableWidget(Button.builder(loopingLabel(END_KEY, endLooping), b -> {
            endLooping = !endLooping;
            b.setMessage(loopingLabel(END_KEY, endLooping));
        }).bounds(leftX(), rowY(2), colW, 20).build());

        addRenderableWidget(Button.builder(beaconLabel(OVERWORLD_KEY, overworldBeacon), b -> {
            overworldBeacon = !overworldBeacon;
            b.setMessage(beaconLabel(OVERWORLD_KEY, overworldBeacon));
        }).bounds(rightX(), rowY(0), colW, 20).build());

        addRenderableWidget(Button.builder(beaconLabel(NETHER_KEY, netherBeacon), b -> {
            netherBeacon = !netherBeacon;
            b.setMessage(beaconLabel(NETHER_KEY, netherBeacon));
        }).bounds(rightX(), rowY(1), colW, 20).build());

        addRenderableWidget(Button.builder(beaconLabel(END_KEY, endBeacon), b -> {
            endBeacon = !endBeacon;
            b.setMessage(beaconLabel(END_KEY, endBeacon));
        }).bounds(rightX(), rowY(2), colW, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> applyAndClose())
                .bounds(width / 2 - 75, rowY(3) + 12, 150, 20).build());
    }

    private Component loopingLabel(String dimensionKey, boolean value) {
        return Component.translatable(dimensionKey).append(": ").append(boolStr(value));
    }

    private Component beaconLabel(String dimensionKey, boolean value) {
        return Component.translatable("biblioteca.gui.looping_config.beacon_prefix")
                .append(" ")
                .append(Component.translatable(dimensionKey))
                .append(": ")
                .append(boolStr(value));
    }

    private Component boolStr(boolean value) {
        return value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    private void applyAndClose() {
        Set<ResourceKey<Level>> looping = new HashSet<>();
        if (overworldLooping) looping.add(Level.OVERWORLD);
        if (netherLooping)    looping.add(Level.NETHER);
        if (endLooping)       looping.add(Level.END);

        Set<ResourceKey<Level>> beaconIgnoresSky = new HashSet<>();
        if (overworldBeacon) beaconIgnoresSky.add(Level.OVERWORLD);
        if (netherBeacon)    beaconIgnoresSky.add(Level.NETHER);
        if (endBeacon)       beaconIgnoresSky.add(Level.END);

        PendingLoopingConfig.set(looping, beaconIgnoresSky);
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, rowY(0) - 34, 0xFFFFFF);
        graphics.centeredText(font,
                Component.translatable("biblioteca.gui.looping_config.header_looping"),
                leftX() + columnWidth() / 2, rowY(0) - 16, 0xFFFFFF);
        graphics.centeredText(font,
                Component.translatable("biblioteca.gui.looping_config.header_beacon"),
                rightX() + columnWidth() / 2, rowY(0) - 16, 0xFFFFFF);
        graphics.centeredText(font,
                Component.translatable("biblioteca.gui.looping_config.note"),
                width / 2, rowY(3) + 44, 0xAAAAAA);
    }
}
