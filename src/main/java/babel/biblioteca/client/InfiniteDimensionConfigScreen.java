package babel.biblioteca.client;

import babel.biblioteca.Compat;
import babel.biblioteca.compat.InfiniteDimensionKey;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import java.util.ArrayList;
import java.util.List;

/** Native pre-PoseStack UI for the Infinity snapshot, sharing the dimension settings model. */
public final class InfiniteDimensionConfigScreen extends Screen {
    private final Screen parent;
    private final DimensionConfigModel model;
    private final List<List<String>> headings = new ArrayList<>();
    private List<String> note = List.of();
    private List<InfiniteDimensionKey> dimensions = List.of();
    private int first, rows, left, widthAvailable, columnWidth, top, bottom, noteY;

    public InfiniteDimensionConfigScreen(Screen parent, DimensionConfigModel model) {
        super(Compat.translatable("biblioteca.gui.looping_config.title"));
        this.parent = parent;
        this.model = model;
    }

    @Override protected void init() {
        model.refresh();
        dimensions = InfiniteEasterEggDimensions.ordered(model.dimensions());
        widthAvailable = Math.min(width, 560) - 32;
        left = (width - widthAvailable) / 2;
        columnWidth = (widthAvailable - 18) / 4;
        headings.clear();
        int lines = 1;
        for (String key : List.of("header_looping", "header_beacon", "header_no_void_fog", "header_sky_light")) {
            List<String> text = font.split(I18n.get("biblioteca.gui.looping_config." + key), columnWidth);
            headings.add(text);
            lines = Math.max(lines, text.size());
        }
        note = font.split(I18n.get("biblioteca.gui.looping_config.note"), width - 24);
        noteY = height - 36 - note.size() * 9;
        top = 40 + lines * 9;
        rows = Math.max(1, (noteY - top - 6) / 44);
        bottom = top + rows * 44;
        first = Math.max(0, Math.min(first, Math.max(0, dimensions.size() - rows)));
        for (int row = 0; row < rows && first + row < dimensions.size(); row++) {
            InfiniteDimensionKey dimension = dimensions.get(first + row);
            for (int col = 0; col < 4; col++) {
                int column = col;
                addButton(new Button(left + col * (columnWidth + 6), top + row * 44 + 14,
                        columnWidth, 20, label(dimension, column), button -> {
                    if (column == 3) model.cycleSkyLightMode(dimension); else model.toggle(dimension, column);
                    button.setMessage(label(dimension, column));
                }));
            }
        }
        addButton(new Button(width / 2 - 75, height - 28, 150, 20, I18n.get("gui.done"), button -> {
            model.apply(); onClose();
        }));
        if (dimensions.size() > rows) {
            Button up = addButton(new Button(width / 2 - 105, height - 28, 20, 20, "↑", button -> scroll(-1)));
            Button down = addButton(new Button(width / 2 + 85, height - 28, 20, 20, "↓", button -> scroll(1)));
            up.active = first > 0;
            down.active = first + rows < dimensions.size();
        }
    }

    private String label(InfiniteDimensionKey dimension, int column) {
        if (column == 3) return I18n.get(model.skyLightMode(dimension).translationKey());
        boolean enabled = model.enabled(dimension, column);
        if (column == 2) return I18n.get("biblioteca.gui.looping_config.no_void_fog_" + (enabled ? "on" : "off"));
        return I18n.get(enabled ? "options.on" : "options.off");
    }

    private void rebuild() { buttons.clear(); children.clear(); setFocused(null); init(); }
    private void scroll(int delta) {
        int next = Math.max(0, Math.min(first + delta, Math.max(0, dimensions.size() - rows)));
        if (first != next) { first = next; rebuild(); }
    }
    @Override public void tick() { if (model.refresh()) rebuild(); }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (x < left || x >= left + widthAvailable || y < top || y >= bottom || amount == 0) return false;
        scroll(amount > 0 ? -1 : 1); return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> { scroll(-rows); return true; }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> { scroll(rows); return true; }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> { scroll(-dimensions.size()); return true; }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> { scroll(dimensions.size()); return true; }
            default -> { return super.keyPressed(key, scanCode, modifiers); }
        }
    }
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override public void render(int mouseX, int mouseY, float partial) {
        renderBackground();
        super.render(mouseX, mouseY, partial);
        drawCenteredString(font, title.getColoredString(), width / 2, 14, -1);
        drawCenteredString(font, (first + 1) + "–" + Math.min(first + rows, dimensions.size()) + " / " + dimensions.size(), width / 2, 24, 0xAAAAAA);
        for (int col = 0; col < 4; col++) for (int line = 0; line < headings.get(col).size(); line++) {
            drawCenteredString(font, headings.get(col).get(line), left + col * (columnWidth + 6) + columnWidth / 2, 34 + line * 9, -1);
        }
        List<String> hoverTooltip = null;
        for (int row = 0; row < rows && first + row < dimensions.size(); row++) {
            InfiniteDimensionKey dimension = dimensions.get(first + row);
            String id = dimension.toString();
            String key = switch (id) {
                case "minecraft:overworld" -> "overworld";
                case "minecraft:the_nether" -> "nether";
                case "minecraft:the_end" -> "end";
                default -> null;
            };
            String name = key == null ? InfiniteEasterEggDimensions.displayName(dimension) : I18n.get("biblioteca.gui.looping_config." + key);
            boolean shortened = font.width(name) > widthAvailable;
            String shown = shortened ? font.substrByWidth(name, widthAvailable - font.width("…")) + "…" : name;
            int nameY = top + row * 44 + 1;
            drawString(font, shown, left, nameY, -1);
            if (shortened && mouseX >= left && mouseX < left + widthAvailable && mouseY >= nameY && mouseY < nameY + 10)
                hoverTooltip = font.split(name, Math.min(240, width - 24));
            int bx = left + 3 * (columnWidth + 6), by = top + row * 44 + 14;
            if (mouseX >= bx && mouseX < bx + columnWidth && mouseY >= by && mouseY < by + 20) {
                List<String> tooltip = new ArrayList<>();
                tooltip.add(id);
                tooltip.addAll(font.split(I18n.get(model.skyLightMode(dimension).translationKey() + ".description"), Math.min(240, width - 24)));
                hoverTooltip = tooltip;
            }
        }
        for (int line = 0; line < note.size(); line++) drawCenteredString(font, note.get(line), width / 2, noteY + line * 9, 0xAAAAAA);
        if (hoverTooltip != null) renderTooltip(hoverTooltip, mouseX, mouseY);
    }
}
