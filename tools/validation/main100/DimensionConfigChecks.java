package babel.biblioteca.validation.main100;

import babel.biblioteca.PendingLoopingConfig;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.client.DimensionConfigModel;
import babel.biblioteca.client.LoopingConfigScreen;
import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static babel.biblioteca.validation.main100.SeamChecks.check;

/** Uses native dimension registries/context replacements and native scroll/button widgets. */
public final class DimensionConfigChecks {
    private static final String[] VANILLA = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};

    public static void run() throws Exception {
        ResourceKey<Level> alpha = key("alpha:sky_islands"), zeta = key("zeta:caverns");
        AtomicReference<WorldCreationContext> live = new AtomicReference<>(context(VANILLA, "zeta:caverns", "alpha:sky_islands", "minecraft:overworld"));
        DimensionConfigModel model = new DimensionConfigModel(() -> babel.biblioteca.client.CreateWorldDimensions.fromContext(live.get()), Set.of(Level.NETHER), Set.of(), Set.of(), Map.of());
        check(model.dimensions().equals(List.of(Level.OVERWORLD, Level.NETHER, Level.END, alpha, zeta)), "merge preset and enabled pack dimensions, deduplicate and sort");
        for (int column = 0; column < 3; column++) {
            check(!model.enabled(alpha, column), "new datapack dimensions start with vanilla/default attributes");
            model.toggle(alpha, column);
        }
        check(model.skyLightMode(alpha) == SkyLightMode.LOOPING, "new dimensions retain the full-loop default");
        check(model.cycleSkyLightMode(alpha) == SkyLightMode.HYBRID, "sky button reaches vanilla-exposure mode");
        model.toggle(zeta, 2);
        check(!model.refresh(), "unchanged context does not rebuild the list");
        live.set(context(VANILLA, "zeta:caverns", "beta:new_world"));
        check(model.refresh() && !model.dimensions().contains(alpha), "removed pack dimension disappears");
        check(model.enabled(zeta, 2) && model.enabled(Level.NETHER, 0), "reordered rows keep values by dimension ID");
        for (int column = 0; column < 3; column++) check(!model.selected(column).contains(alpha), "hidden dimension excluded from save");
        check(!model.selectedSkyLightModes().containsKey(alpha), "hidden sky-mode draft excluded from save");
        live.set(context(VANILLA, "alpha:sky_islands", "zeta:caverns"));
        model.refresh();
        for (int column = 0; column < 3; column++) check(model.enabled(alpha, column), "restored dimension retains its draft choices");
        check(model.skyLightMode(alpha) == SkyLightMode.HYBRID, "restored custom dimension retains its sky mode");
        model.apply();
        check(PendingLoopingConfig.getPendingLooping().containsAll(Set.of(alpha, Level.NETHER)), "custom looping attribute reaches pending world config");
        check(PendingLoopingConfig.getPendingBeaconIgnoresSky().equals(Set.of(alpha)), "custom beacon attribute saved");
        check(PendingLoopingConfig.getPendingNoVoidFog().equals(Set.of(alpha, zeta)), "custom fog attribute saved");
        check(PendingLoopingConfig.getPendingSkyLightModes().equals(Map.of(alpha, SkyLightMode.HYBRID)), "custom lighting mode saved");
        live.set(context(new String[]{"minecraft:overworld", "preset:moon"}));
        model.apply(); // A reload that completes immediately before Done must also be observed.
        check(model.dimensions().equals(List.of(Level.OVERWORLD, key("preset:moon"))), "custom preset dimensions included without an extra data-pack entry");
        check(PendingLoopingConfig.getPendingLooping().isEmpty(), "Done synchronizes with the latest valid context");
        check(PendingLoopingConfig.getPendingSkyLightModes().isEmpty(), "Done drops modes belonging to unloaded dimensions");
        PendingLoopingConfig.consume();
        checkWidgets();
        System.out.println("PASS: dimension config: live registry replacement, preset/pack merge, removed/restored dimensions, all four saved attributes, 67-row scrolling and resize");
    }

    private static void checkWidgets() throws Exception {
        Field singleton = Arrays.stream(Minecraft.class.getDeclaredFields())
                .filter(f -> f.getType() == Minecraft.class && Modifier.isStatic(f.getModifiers())).findFirst().orElseThrow();
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        Minecraft minecraft = (Minecraft) SeamFixture.allocate(Minecraft.class);
        Field font = Minecraft.class.getDeclaredField("font"); font.setAccessible(true); font.set(minecraft, new LayoutFont());
        singleton.set(null, minecraft);
        try {
            String[] packs = new String[64];
            for (int i = 0; i < packs.length; i++) packs[i] = "datapack:world_" + String.format("%02d", i);
            AtomicReference<WorldCreationContext> live = new AtomicReference<>(context(VANILLA, packs));
            DimensionConfigModel model = new DimensionConfigModel(() -> babel.biblioteca.client.CreateWorldDimensions.fromContext(live.get()), Set.of(), Set.of(), Set.of(), Map.of());
            TestScreen screen = new TestScreen(model);
            for (int[] size : new int[][]{{320, 240}, {640, 360}, {1000, 600}}) {
                screen.layout(size[0], size[1]);
                ContainerObjectSelectionList<?> list = (ContainerObjectSelectionList<?>) screen.children().getFirst();
                check(list.children().size() == 67 && list.maxScrollAmount() > 0, "many dimensions produce a scrollable list");
                check(list.getY() >= 40 && list.getBottom() < size[1] - 36, "list is between column headings and footer");
                list.setScrollAmount(list.maxScrollAmount());
                var last = list.children().getLast();
                check(last.getContentY() >= list.getY() && last.getContentBottom() <= list.getBottom(), "last row can be scrolled fully into view");
                int previousRight = -1;
                for (var child : last.children()) {
                    Button button = (Button) child;
                    check(button.getX() > previousRight && button.getRight() < size[0], "all four buttons stay inside the screen and do not overlap");
                    check(button.getY() >= list.getY() && button.getBottom() <= list.getBottom(), "button hitboxes move with their scrolled row");
                    previousRight = button.getRight();
                }
                Button toggle = (Button) last.children().getFirst();
                boolean before = model.enabled(key(packs[63]), 0);
                toggle.onPress(null);
                check(model.enabled(key(packs[63]), 0) != before, "scrolled button edits the correct custom dimension");
                Button sky = (Button) last.children().get(3);
                for (SkyLightMode mode : new SkyLightMode[]{SkyLightMode.HYBRID, SkyLightMode.DISABLED, SkyLightMode.LOOPING}) {
                    sky.onPress(null);
                    check(model.skyLightMode(key(packs[63])) == mode, "native sky button cycles all three choices after scrolling/resizing");
                    check(sky.getMessage().equals(net.minecraft.network.chat.Component.translatable(mode.translationKey())), "button label tracks the selected sky mode");
                }
            }
            live.set(context(VANILLA, "replacement:dimension"));
            screen.tick();
            ContainerObjectSelectionList<?> list = (ContainerObjectSelectionList<?>) screen.children().getFirst();
            check(list.children().size() == 4, "open page immediately rebuilds after context replacement");
            check(list.scrollAmount() <= list.maxScrollAmount(), "removing rows clamps the old scroll position");
            Object firstRow = list.children().getFirst();
            screen.tick();
            check(list.children().getFirst() == firstRow, "ordinary ticks preserve widgets and keyboard focus");
        } finally {
            singleton.set(null, previous);
        }
    }

    private static WorldCreationContext context(String[] preset, String... packs) {
        var selected = new HashMap<ResourceKey<LevelStem>, LevelStem>();
        for (String id : preset) selected.put(ResourceKey.create(Registries.LEVEL_STEM, Identifier.parse(id)), new LevelStem(null, null));
        var registry = new MappedRegistry<LevelStem>(Registries.LEVEL_STEM, Lifecycle.stable());
        for (String id : packs) registry.register(ResourceKey.create(Registries.LEVEL_STEM, Identifier.parse(id)), new LevelStem(null, null), RegistrationInfo.BUILT_IN);
        return new WorldCreationContext(null, registry.freeze(), new WorldDimensions(selected), null, null, null, null);
    }
    private static ResourceKey<Level> key(String id) { return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id)); }
    private static final class TestScreen extends LoopingConfigScreen {
        TestScreen(DimensionConfigModel model) { super(null, model); }
        void layout(int width, int height) { this.width = width; this.height = height; clearWidgets(); super.init(); }
    }
    /** Text measurement only; widget placement and event dispatch use the real screen/list/button. */
    private static final class LayoutFont extends Font {
        LayoutFont() { super(null); }
        @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
            String value = text.getString();
            int length = Math.max(1, width / 6);
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (int start = 0; start < value.length(); start += length) {
                lines.add(FormattedCharSequence.forward(value.substring(start, Math.min(value.length(), start + length)), Style.EMPTY));
            }
            return lines;
        }
    }
}
