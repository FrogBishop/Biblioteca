package babel.biblioteca.client;

import babel.biblioteca.Compat;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else if >=1.20 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

/** A bounded, wheel-scrollable list using each older release's native buttons and render API. */
public final class LegacyDimensionConfigScreen extends Screen {
    private static final String[] HEADERS = {"header_looping", "header_beacon", "header_no_void_fog", "header_sky_light"};
    private static final int ROW_HEIGHT = 44, GAP = 6, COLOR = 0xFFFFFFFF;
    private final Screen parent;
    private final DimensionConfigModel model;
    private final List<List<FormattedCharSequence>> headings = new ArrayList<>();
    private List<FormattedCharSequence> note = List.of();
    private int first, rows, left, contentWidth, columnWidth, top, bottom, noteY;

    public LegacyDimensionConfigScreen(Screen parent, DimensionConfigModel model) {
        super(Compat.translatable("biblioteca.gui.looping_config.title"));
        this.parent = parent; this.model = model;
    }

    @Override protected void init() {
        model.refresh();
        contentWidth = Math.min(width, 560) - 32;
        left = (width - contentWidth) / 2;
        columnWidth = (contentWidth - 3 * GAP) / 4;
        headings.clear(); int headerLines = 1;
        for (String key : HEADERS) {
            var lines = font.split(Compat.translatable("biblioteca.gui.looping_config." + key), columnWidth);
            headings.add(lines); headerLines = Math.max(headerLines, lines.size());
        }
        note = font.split(Compat.translatable("biblioteca.gui.looping_config.note"), width - 24);
        noteY = height - 36 - note.size() * font.lineHeight;
        top = 34 + headerLines * font.lineHeight + 6;
        rows = Math.max(1, (noteY - top - 6) / ROW_HEIGHT);
        bottom = top + rows * ROW_HEIGHT;
        first = Math.max(0, Math.min(first, Math.max(0, model.dimensions().size() - rows)));
        for (int row = 0; row < rows && first + row < model.dimensions().size(); row++) {
            ResourceKey<Level> dimension = model.dimensions().get(first + row);
            for (int column = 0; column < 4; column++) {
                int c = column;
                add(ClientCompat.button(label(dimension, c), left + c * (columnWidth + GAP), top + row * ROW_HEIGHT + 14,
                        columnWidth, 20, button -> {
                            if (c == 3) model.cycleSkyLightMode(dimension); else model.toggle(dimension, c);
                            button.setMessage(label(dimension, c));
                        }));
            }
        }
        add(ClientCompat.button(CommonComponents.GUI_DONE, width / 2 - 75, height - 28, 150, 20,
                button -> { model.apply(); onClose(); }));
        if (model.dimensions().size() > rows) {
            Button up = ClientCompat.button(Compat.literal("↑"), width / 2 - 105, height - 28, 20, 20, button -> scroll(-1));
            Button down = ClientCompat.button(Compat.literal("↓"), width / 2 + 85, height - 28, 20, 20, button -> scroll(1));
            up.active = first > 0; down.active = first + rows < model.dimensions().size(); add(up); add(down);
        }
    }

    private void add(Button button) {
        //? if >=1.17 {
        addRenderableWidget(button);
        //?} else {
        /*addButton(button);
        *///?}
    }
    private void rebuild() {
        //? if >=1.17 {
        clearWidgets();
        //?} else {
        /*buttons.clear(); children.clear();
        *///?}
        setFocused(null); init();
    }
    private void scroll(int direction) {
        int next = Math.max(0, Math.min(first + direction, Math.max(0, model.dimensions().size() - rows)));
        if (next != first) { first = next; rebuild(); }
    }
    @Override public void tick() { if (model.refresh()) rebuild(); }

    //? if >=1.20.2 {
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return scrollAt(x, y, vertical);
    }
    //?} else {
    /*@Override public boolean mouseScrolled(double x, double y, double amount) { return scrollAt(x, y, amount); }
    *///?}
    private boolean scrollAt(double x, double y, double amount) {
        if (x < left || x >= left + contentWidth || y < top || y >= bottom || amount == 0) return false;
        scroll(amount > 0 ? -1 : 1); return true;
    }
    private Component label(ResourceKey<Level> dimension, int column) {
        if (column == 3) return Compat.translatable(model.skyLightMode(dimension).translationKey());
        boolean enabled = model.enabled(dimension, column);
        if (column == 2) return Compat.translatable("biblioteca.gui.looping_config.no_void_fog_" + (enabled ? "on" : "off"));
        return enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }
    private Component name(ResourceKey<Level> dimension) {
        String key = dimension.equals(Level.OVERWORLD) ? "overworld" : dimension.equals(Level.NETHER) ? "nether" : dimension.equals(Level.END) ? "end" : null;
        return key == null ? Compat.literal(Compat.keyId(dimension).toString()) : Compat.translatable("biblioteca.gui.looping_config." + key);
    }
    @Override public void onClose() {
        //? if >=26.1 {
        Minecraft.getInstance().setScreenAndShow(parent);
        //?} else {
        /*Minecraft.getInstance().setScreen(parent);
        *///?}
    }

    //? if >=26.1 {
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial); drawLabels(graphics, mouseX, mouseY);
    }
    private void drawLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    //?} else if >=1.20 {
    /*@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        //? if <1.20.2 {
        renderBackground(graphics);
        //?}
        super.render(graphics, mouseX, mouseY, partial); drawLabels(graphics, mouseX, mouseY);
    }
    private void drawLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    *///?} else {
    /*@Override public void render(PoseStack graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partial); drawLabels(graphics, mouseX, mouseY);
    }
    private void drawLabels(PoseStack graphics, int mouseX, int mouseY) {
    *///?}
        text(graphics, title.getVisualOrderText(), width / 2 - font.width(title) / 2, 14, COLOR);
        for (int col = 0; col < 4; col++) for (int line = 0; line < headings.get(col).size(); line++) {
            var value = headings.get(col).get(line);
            text(graphics, value, left + col * (columnWidth + GAP) + (columnWidth - font.width(value)) / 2, 34 + line * font.lineHeight, COLOR);
        }
        for (int row = 0; row < rows && first + row < model.dimensions().size(); row++) {
            var dimension = model.dimensions().get(first + row);
            var value = Compat.literal(font.plainSubstrByWidth(name(dimension).getString(), contentWidth)).getVisualOrderText();
            text(graphics, value, left, top + row * ROW_HEIGHT + 1, COLOR);
            int bx = left + 3 * (columnWidth + GAP), by = top + row * ROW_HEIGHT + 14;
            if (mouseX >= bx && mouseX < bx + columnWidth && mouseY >= by && mouseY < by + 20) {
                Component tip = Compat.literal(Compat.keyId(dimension) + "\n").append(Compat.translatable(model.skyLightMode(dimension).translationKey() + ".description"));
                //? if >=1.21.6 {
                graphics.setTooltipForNextFrame(tip, mouseX, mouseY);
                //?} else if >=1.20 {
                /*graphics.renderTooltip(font, tip, mouseX, mouseY);
                *///?} else {
                /*renderTooltip(graphics, tip, mouseX, mouseY);
                *///?}
            }
        }
        for (int line = 0; line < note.size(); line++) {
            var value = note.get(line);
            text(graphics, value, (width - font.width(value)) / 2, noteY + line * font.lineHeight, 0xFFAAAAAA);
        }
    }

    //? if >=26.1 {
    private void text(GuiGraphicsExtractor graphics, FormattedCharSequence value, int x, int y, int color) { graphics.text(font, value, x, y, color); }
    //?} else if >=1.20 {
    /*private void text(GuiGraphics graphics, FormattedCharSequence value, int x, int y, int color) { graphics.drawString(font, value, x, y, color); }
    *///?} else {
    /*private void text(PoseStack graphics, FormattedCharSequence value, int x, int y, int color) { font.drawShadow(graphics, value, x, y, color); }
    *///?}
}
