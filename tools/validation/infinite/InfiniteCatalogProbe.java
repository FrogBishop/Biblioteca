package biblioteca.infiniteprobe;

import babel.biblioteca.Compat;
import babel.biblioteca.PendingLoopingConfig;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.client.CreateWorldScreenInjector;
import babel.biblioteca.client.InfiniteDimensionConfigScreen;
import babel.biblioteca.client.InfiniteEasterEggDimensions;
import babel.biblioteca.client.LoopingDimensionCache;
import babel.biblioteca.compat.InfiniteDimensionKey;
import babel.biblioteca.world.LoopingDimensionManager;
import babel.biblioteca.world.LoopingWorldData;
import java.lang.reflect.Field;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.Registry;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimHash;
import org.lwjgl.glfw.GLFW;

/** Walks every actual GUI page and applies settings for still-unvisited Easter egg worlds. */
public final class InfiniteCatalogProbe implements ClientModInitializer {
    private static final String LONG_NAME = "this_is_a_very_long_phrase_that_hopefully_is_not_in_any_dictionary";
    private static final Set<String> painted = new HashSet<>(), texts = new HashSet<>();
    private static Set<String> expected;
    private static Set<ResourceLocation> initialRegistry;
    private static String initialPhrase;
    private static InfiniteDimensionConfigScreen screen;
    private static List<InfiniteDimensionKey> dimensions;
    private static int ticks, phase, waiting;
    private static boolean ready, done, hoverLong, submitted;
    private static volatile boolean serverVerified;
    private static volatile Throwable failure;

    @Override public void onInitializeClient() {}

    public static void text(String text) {
        if (expected == null) return;
        texts.add(text);
        if (expected.contains(text)) painted.add(text);
        if (text.endsWith("…") && text.length() > 8 && LONG_NAME.startsWith(text.substring(0, text.length() - 1))) painted.add(LONG_NAME);
    }
    public static void tooltip(List<String> lines) { texts.addAll(lines); }
    public static int mouseX(int value) { return hoverLong ? integer("left") + 4 : value; }
    public static int mouseY(int value) { return hoverLong ? integer("top") + 4 : value; }

    public static void tick(Minecraft client) {
        if (done) return;
        try {
            if (!ready) {
                if (client.getOverlay() != null || !(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
                ready = true;
            }
            if (++ticks > 1800) throw new AssertionError("catalog probe timed out");
            if (failure != null) throw new AssertionError("server checks", failure);
            if (phase == 0) {
                if (ticks < 10) return;
                initialRegistry = Set.copyOf(Registry.DIMENSION_TYPE.keySet());
                initialPhrase = DimHash.getLastPasshphrase();
                expected = new HashSet<>();
                for (var entry : InfiniteEasterEggDimensions.entries()) expected.add(entry.name());
                var parent = new CreateWorldScreen(null);
                client.setScreen(parent);
                CreateWorldScreenInjector.openConfig(parent);
                screen = (InfiniteDimensionConfigScreen) client.screen;
                dimensions = (List<InfiniteDimensionKey>) field(screen, "dimensions").get(screen);
                next(1); return;
            }
            if (phase == 10) { verifyWorld(client); return; }
            if (++waiting < 5) return;
            waiting = 0;
            switch (phase) {
                case 1 -> {
                    check(dimensions.size() == 46, "46 actual GUI entries");
                    check(initialRegistry.equals(Registry.DIMENSION_TYPE.keySet()) && initialPhrase.equals(DimHash.getLastPasshphrase()),
                            "opening the menu leaves native registrations and secret passphrase intact");
                    shot(client, "01-vanilla-and-count"); next(2);
                }
                case 2 -> {
                    if (integer("first") >= dimensions.size() - integer("rows")) {
                        check(painted.containsAll(expected), "all 43 original names actually drawn in " + client.options.languageCode);
                        go("ant"); next(3);
                    } else screen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
                }
                case 3 -> { button(0).onPress(); next(4); }
                case 4 -> { shot(client, "02-ant-enabled"); go("green"); next(5); }
                case 5 -> { shot(client, "03-original-green"); go(LONG_NAME); hoverLong = true; next(6); }
                case 6 -> {
                    boolean clipped = client.font.width(LONG_NAME) > integer("widthAvailable");
                    if (clipped) for (String line : client.font.split(LONG_NAME, Math.min(240, screen.width - 24)))
                        check(texts.contains(line), "full long-name tooltip line drawn: " + line);
                    shot(client, "04-long-original-name"); hoverLong = false;
                    button(2).onPress(); button(3).onPress();
                    screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0); next(7);
                }
                case 7 -> {
                    check(integer("first") == dimensions.size() - integer("rows"), "End key reaches final entries");
                    check(InfiniteEasterEggDimensions.displayName(dimensions.get(dimensions.size() - 1)).equals("zones"), "alphabetical final entry");
                    shot(client, "05-last-page"); go("ant"); next(8);
                }
                case 8 -> {
                    check(button(0).getMessage().equals(net.minecraft.client.resources.language.I18n.get("options.on")), "selection survives paging");
                    shot(client, "06-selection-retained");
                    footerDone().onPress();
                    var ant = key("ant"); var last = key(LONG_NAME);
                    check(PendingLoopingConfig.getPendingLooping().equals(Set.of(ant)), "Done applies exact ant registry key");
                    check(PendingLoopingConfig.getPendingNoVoidFog().equals(Set.of(last))
                            && PendingLoopingConfig.getPendingSkyLightModes().equals(Map.of(last, SkyLightMode.HYBRID)), "long-name dimension settings retained");
                    check(initialRegistry.equals(Registry.DIMENSION_TYPE.keySet()) && initialPhrase.equals(DimHash.getLastPasshphrase()), "editing still has no native registry/passphrase side effects");
                    next(9);
                }
                case 9 -> { next(10); client.selectLevel("infinite-world", "Catalog validation", null); }
                default -> throw new AssertionError("unknown phase " + phase);
            }
        } catch (Throwable error) {
            System.out.println("INFINITE_CATALOG_FAILED"); error.printStackTrace();
            if (ready) shot(client, "failure-" + phase);
            done = true; client.stop();
        }
    }

    private static void verifyWorld(Minecraft client) {
        if (client.level == null || client.player == null) return;
        if (!submitted) {
            submitted = true;
            client.getSingleplayerServer().execute(() -> {
                try {
                    var server = client.getSingleplayerServer();
                    int levels = 0; for (var level : server.getAllLevels()) levels++;
                    check(levels == 3, "configuring Easter eggs does not eagerly create their worlds");
                    var ant = key("ant"); var last = key(LONG_NAME);
                    check(LoopingDimensionManager.isLooping(ant) && LoopingDimensionManager.hasNoVoidFog(last)
                            && LoopingDimensionManager.getSkyLightMode(last) == SkyLightMode.HYBRID, "server receives settings before dimensions are visited");
                    Compat.overworld(server).getDataStorage().save();
                    var file = server.getStorageSource().getFile(server.getLevelIdName(), "data/biblioteca_looping.dat");
                    try (var input = new java.io.FileInputStream(file)) {
                        var restored = LoopingWorldData.decode(NbtIo.readCompressed(input).getCompound("data"));
                        check(restored.getLoopingDimensions().equals(Set.of(ant)) && restored.getNoVoidFogDimensions().equals(Set.of(last))
                                && restored.getSkyLightMode(last) == SkyLightMode.HYBRID, "native saved file preserves UI selections for unvisited dimensions");
                    }
                    serverVerified = true;
                } catch (Throwable error) { failure = error; }
            });
        }
        if (serverVerified && LoopingDimensionCache.isLooping(key("ant")) && LoopingDimensionCache.hasNoVoidFog(key(LONG_NAME))) {
            check(LoopingDimensionCache.getSkyLightMode(key(LONG_NAME)) == SkyLightMode.HYBRID, "join packet preserves unvisited dimension settings");
            System.out.println("INFINITE_CATALOG_OK names=" + painted.size() + " total=" + dimensions.size());
            done = true; client.stop();
        }
    }

    private static InfiniteDimensionKey key(String name) {
        return InfiniteEasterEggDimensions.entries().stream().filter(entry -> entry.name().equals(name)).findFirst().orElseThrow().key();
    }
    private static void next(int value) { phase = value; waiting = 0; }
    private static void go(String name) {
        screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        int index = dimensions.indexOf(key(name));
        while (integer("first") < Math.min(index, dimensions.size() - integer("rows")))
            screen.mouseScrolled(integer("left") + 4, integer("top") + 4, -1);
    }
    private static Button button(int column) { return findButton(integer("left") + column * (integer("columnWidth") + 6), integer("top") + 14); }
    private static Button footerDone() { return findButton(screen.width / 2 - 75, screen.height - 28); }
    private static Button findButton(int x, int y) {
        try {
            for (Object object : (List<?>) field(screen, "buttons").get(screen))
                if (object instanceof Button button && button.x == x && button.y == y) return button;
        } catch (Exception error) { throw new AssertionError(error); }
        throw new AssertionError("missing button at " + x + "," + y);
    }
    private static int integer(String name) {
        try { return field(screen, name).getInt(screen); } catch (Exception error) { throw new AssertionError(error); }
    }
    private static Field field(Object object, String name) throws NoSuchFieldException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void shot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.getWindow().getWidth(), client.getWindow().getHeight(),
                client.getMainRenderTarget(), message -> System.out.println("INFINITE_CATALOG_SCREENSHOT " + name));
    }
    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        System.out.println("INFINITE_CATALOG_CASE_OK " + message);
    }
}
