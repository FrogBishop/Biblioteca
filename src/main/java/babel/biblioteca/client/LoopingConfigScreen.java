package babel.biblioteca.client;

import babel.biblioteca.PendingLoopingConfig;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * World-creation sub-screen for the per-dimension attributes of this mod.
 *
 * One row per vanilla dimension and one column per attribute. Every attribute is off for a new
 * dimension. The two lighting columns are "No void fog" and "No sky light": false preserves the
 * vanilla effect and true disables it. Their buttons name the resulting behaviour directly.
 *
 * Opens from the "Dimension Attributes…" button injected into CreateWorldScreen.
 */
public class LoopingConfigScreen extends Screen {

    private static final Component TITLE =
            Component.translatable("biblioteca.gui.looping_config.title");

    // Text colors are ARGB. GuiGraphicsExtractor.text() returns early when the alpha is
    // zero, so these must stay opaque or the whole line becomes invisible.
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int NOTE_COLOR = 0xFFAAAAAA;

    private static final List<ResourceKey<Level>> DIMENSIONS = List.of(
            Level.OVERWORLD, Level.NETHER, Level.END);
    private static final String[] DIMENSION_KEYS = {
            "biblioteca.gui.looping_config.overworld",
            "biblioteca.gui.looping_config.nether",
            "biblioteca.gui.looping_config.end"
    };
    private static final String[] HEADER_KEYS = {
            "biblioteca.gui.looping_config.header_looping",
            "biblioteca.gui.looping_config.header_beacon",
            "biblioteca.gui.looping_config.header_no_void_fog",
            "biblioteca.gui.looping_config.header_no_sky_light"
    };

    private static final int COLUMNS = HEADER_KEYS.length;
    private static final int BUTTON_W = 86;
    private static final int BUTTON_H = 20;
    private static final int COL_GAP = 6;
    private static final int ROW_H = 24;
    private static final int LABEL_W = 84;

    private final Screen parent;

    /** [dimension][attribute], in the order of DIMENSIONS and HEADER_KEYS. */
    private final boolean[][] values = new boolean[DIMENSIONS.size()][COLUMNS];

    public LoopingConfigScreen(
            Screen parent,
            Set<ResourceKey<Level>> initialLooping,
            Set<ResourceKey<Level>> initialBeaconIgnoresSky,
            Set<ResourceKey<Level>> initialNoVoidFog,
            Set<ResourceKey<Level>> initialNoSkyLight) {
        super(TITLE);
        this.parent = parent;
        for (int row = 0; row < DIMENSIONS.size(); row++) {
            ResourceKey<Level> dimension = DIMENSIONS.get(row);
            values[row][0] = initialLooping.contains(dimension);
            values[row][1] = initialBeaconIgnoresSky.contains(dimension);
            values[row][2] = initialNoVoidFog.contains(dimension);
            values[row][3] = initialNoSkyLight.contains(dimension);
        }
    }

    private int gridWidth() {
        return LABEL_W + COLUMNS * (BUTTON_W + COL_GAP) - COL_GAP;
    }

    private int gridX() {
        return (width - gridWidth()) / 2;
    }

    private int buttonX(int column) {
        return gridX() + LABEL_W + column * (BUTTON_W + COL_GAP);
    }

    private int rowY(int row) {
        return height / 2 - 34 + row * ROW_H;
    }

    @Override
    protected void init() {
        for (int row = 0; row < DIMENSIONS.size(); row++) {
            for (int column = 0; column < COLUMNS; column++) {
                final int r = row;
                final int c = column;
                addRenderableWidget(Button.builder(stateLabel(c, values[r][c]), button -> {
                    values[r][c] = !values[r][c];
                    button.setMessage(stateLabel(c, values[r][c]));
                }).bounds(buttonX(column), rowY(row), BUTTON_W, BUTTON_H).build());
            }
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> applyAndClose())
                .bounds(width / 2 - 75, rowY(DIMENSIONS.size()) + 10, 150, 20).build());
    }

    private Component stateLabel(int column, boolean value) {
        return switch (column) {
            case 2 -> Component.translatable(value
                    ? "biblioteca.gui.looping_config.no_void_fog_on"
                    : "biblioteca.gui.looping_config.no_void_fog_off");
            case 3 -> Component.translatable(value
                    ? "biblioteca.gui.looping_config.no_sky_light_on"
                    : "biblioteca.gui.looping_config.no_sky_light_off");
            default -> value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        };
    }

    private void applyAndClose() {
        Set<ResourceKey<Level>> looping = new HashSet<>();
        Set<ResourceKey<Level>> beaconIgnoresSky = new HashSet<>();
        Set<ResourceKey<Level>> noVoidFog = new HashSet<>();
        Set<ResourceKey<Level>> noSkyLight = new HashSet<>();

        for (int row = 0; row < DIMENSIONS.size(); row++) {
            ResourceKey<Level> dimension = DIMENSIONS.get(row);
            if (values[row][0]) looping.add(dimension);
            if (values[row][1]) beaconIgnoresSky.add(dimension);
            if (values[row][2]) noVoidFog.add(dimension);
            if (values[row][3]) noSkyLight.add(dimension);
        }

        PendingLoopingConfig.set(looping, beaconIgnoresSky, noVoidFog, noSkyLight);
        onClose();
    }

    @Override
    public void onClose() {
        //? if >=26.1 {
Minecraft.getInstance().setScreenAndShow(parent);
//?} else {
/*Minecraft.getInstance().setScreen(parent);
*///?}
    }

//? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(font, title, width / 2, rowY(0) - 46, TEXT_COLOR);

        for (int column = 0; column < COLUMNS; column++) {
            graphics.centeredText(font,
                    Component.translatable(HEADER_KEYS[column]),
                    buttonX(column) + BUTTON_W / 2, rowY(0) - 18, TEXT_COLOR);
        }

        for (int row = 0; row < DIMENSIONS.size(); row++) {
            Component name = Component.translatable(DIMENSION_KEYS[row]);
            graphics.text(font, name,
                    gridX() + LABEL_W - 10 - font.width(name),
                    rowY(row) + 6, TEXT_COLOR);
        }

        graphics.centeredText(font,
                Component.translatable("biblioteca.gui.looping_config.note"),
                width / 2, rowY(DIMENSIONS.size()) + 40, NOTE_COLOR);
    }
//?} else {
/*    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, rowY(0) - 46, TEXT_COLOR);

        for (int column = 0; column < COLUMNS; column++) {
            graphics.drawCenteredString(font,
                    Component.translatable(HEADER_KEYS[column]),
                    buttonX(column) + BUTTON_W / 2, rowY(0) - 18, TEXT_COLOR);
        }

        for (int row = 0; row < DIMENSIONS.size(); row++) {
            Component name = Component.translatable(DIMENSION_KEYS[row]);
            graphics.drawString(font, name,
                    gridX() + LABEL_W - 10 - font.width(name),
                    rowY(row) + 6, TEXT_COLOR);
        }

        graphics.drawCenteredString(font,
                Component.translatable("biblioteca.gui.looping_config.note"),
                width / 2, rowY(DIMENSIONS.size()) + 40, NOTE_COLOR);
    }
*///?}
}
