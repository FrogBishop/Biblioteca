package babel.biblioteca.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Attributes for the dimensions in the current world preset and successfully loaded data packs. */
public class LoopingConfigScreen extends Screen {
    private static final Component TITLE = Component.translatable("biblioteca.gui.looping_config.title");
    private static final String[] HEADER_KEYS = {
            "biblioteca.gui.looping_config.header_looping",
            "biblioteca.gui.looping_config.header_beacon",
            "biblioteca.gui.looping_config.header_no_void_fog",
            "biblioteca.gui.looping_config.header_sky_light"
    };
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int NOTE_COLOR = 0xFFAAAAAA;
    private static final int COLUMNS = HEADER_KEYS.length;
    private static final int COL_GAP = 6;
    private static final int ROW_H = 44;
    private static final int HEADER_Y = 34;
    private final Screen parent;
    private final DimensionConfigModel model;
    private final List<List<FormattedCharSequence>> headers = new ArrayList<>();
    private List<FormattedCharSequence> note = List.of();
    private DimensionList dimensionList;
    private int noteY;

    public LoopingConfigScreen(Screen parent, DimensionConfigModel model) {
        super(TITLE);
        this.parent = parent;
        this.model = model;
    }

    @Override
    protected void init() {
        model.refresh();
        double scroll = dimensionList == null ? 0 : dimensionList.scrollAmount();
        int listWidth = Math.min(width, 560);
        int contentWidth = listWidth - 32;
        int buttonWidth = columnWidth(contentWidth);
        headers.clear();
        int headerLines = 1;
        for (String key : HEADER_KEYS) {
            List<FormattedCharSequence> lines = font.split(Component.translatable(key), buttonWidth);
            headers.add(lines);
            headerLines = Math.max(headerLines, lines.size());
        }
        note = font.split(Component.translatable("biblioteca.gui.looping_config.note"), width - 24);
        noteY = height - 36 - note.size() * font.lineHeight;
        int listTop = HEADER_Y + headerLines * font.lineHeight + 6;
        dimensionList = new DimensionList(listWidth, Math.max(ROW_H, noteY - listTop - 6), listTop);
        dimensionList.setX((width - listWidth) / 2);
        refreshRows(scroll);
        addRenderableWidget(dimensionList);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            model.apply();
            onClose();
        }).bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    @Override
    public void tick() {
        // Read the parent's live context. Failed/in-progress pack reloads leave the previous
        // valid context in place; a successful reload replaces it on the client thread.
        if (model.refresh() && dimensionList != null) refreshRows(dimensionList.scrollAmount());
    }

    private void refreshRows(double scroll) {
        dimensionList.setFocused(null);
        dimensionList.replaceEntries(model.dimensions().stream().map(DimensionRow::new).toList());
        dimensionList.setScrollAmount(scroll);
    }

    private static int columnWidth(int width) { return Math.max(1, (width - (COLUMNS - 1) * COL_GAP) / COLUMNS); }

    private static Component dimensionName(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.OVERWORLD)) return Component.translatable("biblioteca.gui.looping_config.overworld");
        if (dimension.equals(Level.NETHER)) return Component.translatable("biblioteca.gui.looping_config.nether");
        if (dimension.equals(Level.END)) return Component.translatable("biblioteca.gui.looping_config.end");
        return Component.literal(dimension.identifier().toString());
    }

    private Component stateLabel(ResourceKey<Level> dimension, int column) {
        if (column == 3) return Component.translatable(model.skyLightMode(dimension).translationKey());
        boolean value = model.enabled(dimension, column);
        return switch (column) {
            case 2 -> Component.translatable(value
                    ? "biblioteca.gui.looping_config.no_void_fog_on" : "biblioteca.gui.looping_config.no_void_fog_off");
            default -> value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        };
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 14, TEXT_COLOR);
        int contentX = dimensionList.getRowLeft() + 2;
        int buttonWidth = columnWidth(dimensionList.getRowWidth() - 4);
        for (int column = 0; column < COLUMNS; column++) {
            int center = contentX + column * (buttonWidth + COL_GAP) + buttonWidth / 2;
            List<FormattedCharSequence> lines = headers.get(column);
            for (int line = 0; line < lines.size(); line++) {
                graphics.centeredText(font, lines.get(line), center, HEADER_Y + line * font.lineHeight, TEXT_COLOR);
            }
        }
        for (int line = 0; line < note.size(); line++) {
            graphics.centeredText(font, note.get(line), width / 2, noteY + line * font.lineHeight, NOTE_COLOR);
        }
    }

    private final class DimensionList extends ContainerObjectSelectionList<DimensionRow> {
        DimensionList(int width, int height, int y) {
            super(LoopingConfigScreen.this.minecraft, width, height, y, ROW_H);
        }
        @Override public int getRowWidth() { return getWidth() - 28; }
    }

    private final class DimensionRow extends ContainerObjectSelectionList.Entry<DimensionRow> {
        private final Component name;
        private final Component identifier;
        private final List<Button> buttons = new ArrayList<>();

        DimensionRow(ResourceKey<Level> dimension) {
            name = dimensionName(dimension);
            identifier = Component.literal(dimension.identifier().toString());
            for (int column = 0; column < COLUMNS; column++) {
                int c = column;
                Component header = Component.translatable(HEADER_KEYS[c]);
                buttons.add(Button.builder(stateLabel(dimension, c), button -> {
                            if (c == 3) model.cycleSkyLightMode(dimension);
                            else model.toggle(dimension, c);
                            button.setMessage(stateLabel(dimension, c));
                            button.setTooltip(settingTooltip(dimension, c));
                        })
                        .bounds(0, 0, 1, 20)
                        .tooltip(settingTooltip(dimension, c))
                        .createNarration(supplier -> Component.empty().append(name).append(", ").append(header)
                                .append(": ").append(supplier.get()))
                        .build());
            }
        }

        private Tooltip settingTooltip(ResourceKey<Level> dimension, int column) {
            if (column != 3) return null;
            var text = Component.empty().append(identifier).append(" — ").append(Component.translatable(HEADER_KEYS[column]));
            text.append("\n").append(Component.translatable(model.skyLightMode(dimension).translationKey() + ".description"));
            return Tooltip.create(text);
        }

        private void positionButtons() {
            int buttonWidth = columnWidth(getContentWidth());
            for (int column = 0; column < buttons.size(); column++) {
                Button button = buttons.get(column);
                button.setPosition(getContentX() + column * (buttonWidth + COL_GAP), getContentY() + 14);
                button.setWidth(buttonWidth);
            }
        }

        @Override public void setX(int x) { super.setX(x); positionButtons(); }
        @Override public void setY(int y) { super.setY(y); positionButtons(); }
        @Override public void setWidth(int width) { super.setWidth(width); positionButtons(); }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(name, getContentWidth())),
                    getContentX(), getContentY() + 1, TEXT_COLOR);
            if (hovered && mouseY < getContentY() + 13) graphics.setTooltipForNextFrame(identifier, mouseX, mouseY);
            for (Button button : buttons) button.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override public List<? extends GuiEventListener> children() { return buttons; }
        @Override public List<? extends NarratableEntry> narratables() { return buttons; }
    }
}
