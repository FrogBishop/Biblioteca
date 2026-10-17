package babel.biblioteca.client;

import babel.biblioteca.Biblioteca;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.OptionInstance;
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
    private static boolean loaded;

    private LoopingClientSettings() {}

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
        preloadSections = Mth.clamp(value, MIN_PRELOAD_SECTIONS, MAX_PRELOAD_SECTIONS);
        loaded = true;
        save();
    }

    /** The slider that appears in the video settings screen. */
    public static OptionInstance<Integer> createPreloadOption() {
        return new OptionInstance<>(
                "biblioteca.options.preload",
                OptionInstance.noTooltip(),
                (caption, value) -> Options.genericValueLabel(caption, value),
                new OptionInstance.IntRange(MIN_PRELOAD_SECTIONS, MAX_PRELOAD_SECTIONS),
                preloadSections(),
                LoopingClientSettings::setPreloadSections);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) return;

        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (root.has(PRELOAD_KEY)) {
                preloadSections = Mth.clamp(root.get(PRELOAD_KEY).getAsInt(),
                        MIN_PRELOAD_SECTIONS, MAX_PRELOAD_SECTIONS);
            }
        } catch (IOException | RuntimeException exception) {
            Biblioteca.LOGGER.warn("[Biblioteca] Could not read {}", path, exception);
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty(PRELOAD_KEY, preloadSections);

        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            Biblioteca.LOGGER.warn("[Biblioteca] Could not write {}", path, exception);
        }
    }
}
