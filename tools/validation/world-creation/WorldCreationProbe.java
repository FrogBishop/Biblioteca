package biblioteca.worldcreation;

import babel.biblioteca.PendingLoopingConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Drives native creation screens without creating a world; reflection only adapts test API names. */
public final class WorldCreationProbe implements ClientModInitializer {
    private static final String ATTRIBUTES = "biblioteca.gui.looping_button";
    private static final String MORE = "createWorld.tab.more.title";
    private int ticks, phase, delay;
    private boolean requested, tabbed, finished;
    private CreateWorldScreen parent;
    private int[] backgroundBefore;

    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(Minecraft mc) {
        if (finished || mc.getOverlay() != null) return;
        try {
            if (!requested && ++ticks == 60) {
                requested = true;
                open(mc);
            }
            if (!requested) return;
            if (parent == null) {
                if (!(mc.screen instanceof CreateWorldScreen)) return;
                parent = (CreateWorldScreen) mc.screen;
                tabbed = find(parent, MORE) != null;
                delay = 20;
            }
            if (delay-- > 0) return;
            delay = 20;
            switch (phase++) {
                case 0 -> { require(find(parent, ATTRIBUTES) == null, "entry hidden on initial page"); more(); }
                case 1 -> { layout(); screenshot(mc, "options-scale4"); click(parent, ATTRIBUTES); }
                case 2 -> {
                    require(mc.screen.getClass().getSimpleName().equals("LegacyDimensionConfigScreen"), "opens dimension settings");
                    screenshot(mc, "attributes");
                    backgroundBefore = border(mc);
                    // An opaque menu must replace prior framebuffer contents on its next frame.
                    mc.getMainRenderTarget().bindWrite(true);
                    RenderSystem.clearColor(1, 0, 1, 1);
                    RenderSystem.clear(0x4000, false);
                    RenderSystem.clearColor(0, 0, 0, 1);
                }
                case 3 -> {
                    background(mc, backgroundBefore); screenshot(mc, "attributes");
                    firstToggle(mc.screen).onPress(); click(mc.screen, "gui.done");
                }
                case 4 -> {
                    require(mc.screen == parent, "returns to original creation screen");
                    require(PendingLoopingConfig.getPendingLooping().size() == 1, "selected dimension retained");
                    more(); layout(); click(parent, tabbed ? "createWorld.tab.game.title" : "gui.done");
                }
                case 5 -> { require(find(parent, ATTRIBUTES) == null, "entry hidden on main page"); more(); }
                case 6 -> { scale(mc, 3); mc.resizeDisplay(); }
                case 7 -> { more(); if (!tabbed) click(parent, "selectWorld.mapType"); }
                case 8 -> { layout(); screenshot(mc, "options-scale3"); click(parent, ATTRIBUTES); }
                case 9 -> {
                    require(hasKey(firstToggle(mc.screen).getMessage(), "options.on"), "selection restored after resize and reopening");
                    click(mc.screen, "gui.done");
                    System.out.println("WORLD_UI_DONE passed=true layout=" + (tabbed ? "tabs" : "classic"));
                    finished = true; mc.stop();
                }
                default -> throw new AssertionError("unexpected phase");
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); System.out.println("WORLD_UI_FAILURE phase=" + phase);
            finished = true; mc.stop();
        }
    }

    private static void open(Minecraft mc) throws Exception {
        try { CreateWorldScreen.class.getMethod("openFresh", Minecraft.class, Screen.class).invoke(null, mc, mc.screen); }
        catch (NoSuchMethodException absent) {
            Method create;
            try { create = CreateWorldScreen.class.getMethod("createFresh", Screen.class); }
            catch (NoSuchMethodException older) { create = CreateWorldScreen.class.getMethod("create", Screen.class); }
            mc.setScreen((Screen) create.invoke(null, mc.screen));
        }
    }
    private void more() throws Exception {
        if (find(parent, ATTRIBUTES) == null) click(parent, tabbed ? MORE : "selectWorld.moreWorldOptions");
    }
    private void layout() throws Exception {
        AbstractWidget attributes = find(parent, ATTRIBUTES);
        require(attributes != null && attributes.active, "entry visible and active");
        int x = coordinate(attributes, "X"), y = coordinate(attributes, "Y");
        if (tabbed) {
            AbstractWidget packs = find(parent, "selectWorld.dataPacks");
            require(packs != null && x == coordinate(packs, "X") && attributes.getWidth() == packs.getWidth(), "native More column");
            require(y == coordinate(packs, "Y") + packs.getHeight() + 8, "native More row spacing");
        } else {
            AbstractWidget bonus = find(parent, "selectWorld.bonusItems"), type = find(parent, "selectWorld.mapType");
            require(bonus != null && type != null && x == coordinate(type, "X") && y == coordinate(bonus, "Y")
                    && attributes.getWidth() == type.getWidth(), "native bonus-chest row and right column");
            require(find(parent, "selectWorld.import_worldgen_settings") != null, "import settings remains accessible");
        }
        for (AbstractWidget other : widgets(parent)) if (other != attributes && other.visible) {
            int ox = coordinate(other, "X"), oy = coordinate(other, "Y");
            require(x + attributes.getWidth() <= ox || ox + other.getWidth() <= x
                    || y + attributes.getHeight() <= oy || oy + other.getHeight() <= y,
                    "overlapping widget: " + other.getMessage().getString());
        }
        require(x >= 0 && y >= 0 && x + attributes.getWidth() <= parent.width && y + attributes.getHeight() <= parent.height,
                "button inside screen");
        System.out.println("WORLD_UI_LAYOUT kind=" + (tabbed ? "tabs" : "classic") + " x=" + x + " y=" + y
                + " width=" + attributes.getWidth() + " height=" + attributes.getHeight() + " screen=" + parent.width + "x" + parent.height);
    }
    private static void click(Screen screen, String key) throws Exception {
        AbstractWidget widget = find(screen, key);
        require(widget != null && widget.active, "clickable widget: " + key);
        double x = coordinate(widget, "X") + widget.getWidth() / 2.0, y = coordinate(widget, "Y") + widget.getHeight() / 2.0;
        require(screen.mouseClicked(x, y, 0), "click dispatched: " + key);
        screen.mouseReleased(x, y, 0);
    }
    private static Button firstToggle(Screen screen) {
        return widgets(screen).stream().filter(w -> w.visible && w.active && w instanceof Button && !hasKey(w.getMessage(), "gui.done"))
                .map(Button.class::cast).findFirst().orElseThrow();
    }
    private static AbstractWidget find(Screen screen, String key) {
        return widgets(screen).stream().filter(w -> w.visible && hasKey(w.getMessage(), key))
                .reduce((a, b) -> { throw new AssertionError("duplicate widget: " + key); }).orElse(null);
    }
    private static List<AbstractWidget> widgets(Screen screen) {
        List<AbstractWidget> result = new ArrayList<>();
        collect(screen, result, Collections.newSetFromMap(new IdentityHashMap<>()));
        return result;
    }
    private static void collect(Object object, List<AbstractWidget> result, Set<Object> visited) {
        if (!visited.add(object)) return;
        if (object instanceof AbstractWidget widget) result.add(widget);
        if (object instanceof ContainerEventHandler container) for (var child : container.children()) collect(child, result, visited);
    }
    private static boolean hasKey(Component component, String key) {
        try {
            Object contents;
            try {
                Method getter = component.getClass().getMethod("getContents");
                contents = getter.getReturnType() == String.class ? component : getter.invoke(component);
            }
            catch (NoSuchMethodException older) { contents = component; }
            try {
                if (contents.getClass().getMethod("getKey").invoke(contents).equals(key)) return true;
                for (Object value : (Object[]) contents.getClass().getMethod("getArgs").invoke(contents))
                    if (value instanceof Component nested && hasKey(nested, key)) return true;
            } catch (NoSuchMethodException literal) {}
            for (Component sibling : component.getSiblings()) if (hasKey(sibling, key)) return true;
            return false;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static int coordinate(AbstractWidget widget, String axis) throws Exception {
        try { return (int) widget.getClass().getMethod("get" + axis).invoke(widget); }
        catch (NoSuchMethodException older) { return widget.getClass().getField(axis.toLowerCase()).getInt(widget); }
    }
    private static void scale(Minecraft mc, int scale) throws Exception {
        try { mc.options.getClass().getField("guiScale").setInt(mc.options, scale); }
        catch (NoSuchFieldException newer) {
            Object option = mc.options.getClass().getMethod("guiScale").invoke(mc.options);
            option.getClass().getMethod("set", Object.class).invoke(option, scale);
        }
    }
    private static Object capture(Minecraft mc) throws Exception {
        Method capture = java.util.Arrays.stream(Screenshot.class.getMethods())
                .filter(m -> m.getName().equals("takeScreenshot") && (m.getParameterCount() == 1 || m.getParameterCount() == 3))
                .findFirst().orElseThrow();
        return capture.getParameterCount() == 1 ? capture.invoke(null, mc.getMainRenderTarget())
                : capture.invoke(null, mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getMainRenderTarget());
    }
    private static int[] border(Minecraft mc) throws Exception {
        Object image = capture(mc);
        try (var closeable = (AutoCloseable) image) {
            Method pixel = image.getClass().getMethod("getPixelRGBA", int.class, int.class);
            int height = (int) image.getClass().getMethod("getHeight").invoke(image);
            int[] colors = new int[(height - 9) / 7 + 1];
            for (int i = 0; i < colors.length; i++) colors[i] = (int) pixel.invoke(image, 8, 8 + i * 7);
            return colors;
        }
    }
    private static void background(Minecraft mc, int[] before) throws Exception {
        Object image = capture(mc);
        try (var closeable = (AutoCloseable) image) {
            Method pixel = image.getClass().getMethod("getPixelRGBA", int.class, int.class);
            int width = (int) image.getClass().getMethod("getWidth").invoke(image);
            int height = (int) image.getClass().getMethod("getHeight").invoke(image);
            int remaining = 0, samples = 0;
            for (int y = 8; y < height; y += 16) for (int x = 8; x < width; x += 16) {
                samples++;
                if (((int) pixel.invoke(image, x, y) & 0xffffff) == 0xff00ff) remaining++;
            }
            int[] after = border(mc);
            int changed = 0;
            for (int i = 0; i < before.length; i++) if (before[i] != after[i]) changed++;
            System.out.println("WORLD_UI_BACKGROUND retained=" + remaining + " samples=" + samples + " changedBorder=" + changed);
            require(remaining == 0, "settings page must repaint previous framebuffer contents");
            require(changed == 0, "settings background must be independent of previous framebuffer contents");
        }
    }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        Object image = capture(mc);
        try (var closeable = (AutoCloseable) image) { image.getClass().getMethod("writeToFile", Path.class).invoke(image, Path.of(name + ".png")); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
