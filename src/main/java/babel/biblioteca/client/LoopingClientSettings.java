package babel.biblioteca.client;

import babel.biblioteca.Biblioteca;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
//? if >=1.19 {
import net.minecraft.client.OptionInstance;
//?} else {
/*import net.minecraft.client.ProgressOption;
*///?}

import net.minecraft.client.Options;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side settings that are not part of the vanilla options file.
 *
 * The seam preload is the number of extra sections, measured as blocks of slack divided by 16,
 * that are kept warm around the far side of the seam. See SectionOcclusionGraphLoopMixin for what
 * it is for; this class only stores and surfaces the value.
 *
 * Persisted to config/biblioteca-client.json rather than options.txt because adding a field to
 * the vanilla options serialiser would mean patching it.
 */
public final class LoopingClientSettings {

    /** 0 disables preloading entirely: the far side is only warmed when it is already in view. */
    public static final int MIN_PRELOAD_SECTIONS = 0;

    /** One slider step is one section (16 blocks) of slack. Eight is a 128 block shell. */
    public static final int MAX_PRELOAD_SECTIONS = 8;

    /** Slider step in blocks. Kept here so the renderer and the option cannot disagree. */
    public static final int BLOCKS_PER_PRELOAD_STEP = 16;

    private static final String FILE_NAME = "biblioteca-client.json";
    private static final String PRELOAD_KEY = "seamPreloadSections";

    private static int preloadSections = MIN_PRELOAD_SECTIONS;
    private static boolean entityMirrors = true;
    private static boolean multipleCloudLayers = true;
    private static boolean loaded;
    private static Path configDirectory;

    private LoopingClientSettings() {}

    public static void setConfigDirectory(Path directory) {
        configDirectory = java.util.Objects.requireNonNull(directory);
        loaded = false;
        preloadSections = MIN_PRELOAD_SECTIONS;
        entityMirrors = true;
        multipleCloudLayers = true;
    }

    /** Extra sections of slack to keep warm on the far side of the seam. */
    public static int preloadSections() {
        if (!loaded) {
            loaded = true;
            load();
        }

        return preloadSections;
    }

    /** Same value expressed in blocks, which is what the frustum test needs. */
    public static double preloadBlocks() {
        return (double) preloadSections() * BLOCKS_PER_PRELOAD_STEP;
    }

    public static void setPreloadSections(int value) {
        preloadSections();
        preloadSections = Mth.clamp(value, MIN_PRELOAD_SECTIONS, MAX_PRELOAD_SECTIONS);
        loaded = true;
        save();
    }

    public static boolean entityMirrors() {
        if (configDirectory != null) preloadSections();
        return entityMirrors;
    }

    public static boolean multipleCloudLayers() {
        if (configDirectory != null) preloadSections();
        return multipleCloudLayers;
    }

    public static void setEntityMirrors(boolean enabled) {
        preloadSections(); entityMirrors = enabled; save();
    }

    public static void setMultipleCloudLayers(boolean enabled) {
        preloadSections(); multipleCloudLayers = enabled; save();
    }

    /** The slider that appears in the video settings screen. */
//? if >=1.19 {
    public static OptionInstance<Boolean> createEntityMirrorsOption() {
        return OptionInstance.createBoolean("biblioteca.options.entity_mirrors", entityMirrors(), LoopingClientSettings::setEntityMirrors);
    }

    public static OptionInstance<Boolean> createCloudLayersOption() {
        return OptionInstance.createBoolean("biblioteca.options.cloud_layers", multipleCloudLayers(), LoopingClientSettings::setMultipleCloudLayers);
    }

    public static OptionInstance<Integer> createPreloadOption() {
        return new OptionInstance<>(
                "biblioteca.options.preload",
                OptionInstance.noTooltip(),
                (caption, value) -> Options.genericValueLabel(caption, value),
                new OptionInstance.IntRange(MIN_PRELOAD_SECTIONS, MAX_PRELOAD_SECTIONS),
                preloadSections(),
                LoopingClientSettings::setPreloadSections);
    }


//?} else {
/*    public static ProgressOption createEntityMirrorsOption() {
        return booleanOption("biblioteca.options.entity_mirrors", LoopingClientSettings::entityMirrors, LoopingClientSettings::setEntityMirrors);
    }
    public static ProgressOption createCloudLayersOption() {
        return booleanOption("biblioteca.options.cloud_layers", LoopingClientSettings::multipleCloudLayers, LoopingClientSettings::setMultipleCloudLayers);
    }
    private static ProgressOption booleanOption(String key, java.util.function.BooleanSupplier read, java.util.function.Consumer<Boolean> write) {
        return new ProgressOption(key, 0, 1, 1.0f, options -> read.getAsBoolean() ? 1.0 : 0.0,
                (options, value) -> write.accept(value >= 0.5),
                (options, option) -> babel.biblioteca.Compat.translatable("options.generic_value",
                        babel.biblioteca.Compat.translatable(key), babel.biblioteca.Compat.translatable(read.getAsBoolean() ? "options.on" : "options.off")));
    }
    public static ProgressOption createPreloadOption() {
        return new ProgressOption("biblioteca.options.preload", MIN_PRELOAD_SECTIONS, MAX_PRELOAD_SECTIONS, 1.0f,
                options -> (double) preloadSections(),
                (options, value) -> setPreloadSections((int) Math.round(value)),
                (options, option) -> babel.biblioteca.Compat.translatable("options.generic_value",
                        babel.biblioteca.Compat.translatable("biblioteca.options.preload"), preloadSections()));
    }

*///?}
    private static Path file() {
        return java.util.Objects.requireNonNull(configDirectory, "Client platform has not initialized").resolve(FILE_NAME);
    }

    private static void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) return;

        try {
            JsonObject root = new JsonParser().parse(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (root.has(PRELOAD_KEY)) {
                preloadSections = Mth.clamp(root.get(PRELOAD_KEY).getAsInt(),
                        MIN_PRELOAD_SECTIONS, MAX_PRELOAD_SECTIONS);
            }
            if (root.has("entityMirrors")) entityMirrors = root.get("entityMirrors").getAsBoolean();
            if (root.has("multipleCloudLayers")) multipleCloudLayers = root.get("multipleCloudLayers").getAsBoolean();
        } catch (IOException | RuntimeException exception) {
            Biblioteca.LOGGER.warn("[Biblioteca] Could not read {}", path, exception);
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty(PRELOAD_KEY, preloadSections);
        root.addProperty("entityMirrors", entityMirrors);
        root.addProperty("multipleCloudLayers", multipleCloudLayers);

        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            Biblioteca.LOGGER.warn("[Biblioteca] Could not write {}", path, exception);
        }
    }
}
