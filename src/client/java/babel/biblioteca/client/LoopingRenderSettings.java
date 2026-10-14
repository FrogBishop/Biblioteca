package babel.biblioteca.client;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

/**
 * Client-side rendering settings for Y-loop dimensions.
 * Not persisted to options.txt — resets to default each session.
 *
 * loopingRenderRange: single-direction vertical range in blocks.
 * Sections/entities within [cameraY - range, cameraY + range] are
 * rendered at all looping offsets that fall inside that window.
 */
public final class LoopingRenderSettings {

    public static final int DEFAULT_RANGE = 512;
    public static final int MIN_RANGE     = 32;
    public static final int MAX_RANGE     = 1024;

    // Lazily created once and reused.
    private static OptionInstance<Integer> INSTANCE;

    private LoopingRenderSettings() {}

    public static OptionInstance<Integer> option() {
        if (INSTANCE == null) {
            INSTANCE = new OptionInstance<>(
                    "options.biblioteca.loopingRenderRange",
                    OptionInstance.noTooltip(),
                    (caption, value) -> Component.translatable(
                            "options.biblioteca.loopingRenderRange",
                            value),
                    new OptionInstance.IntRange(MIN_RANGE, MAX_RANGE),
                    DEFAULT_RANGE,
                    value -> {}   // no side-effect needed; value is read at render time
            );
        }
        return INSTANCE;
    }

    /** Current single-direction vertical render range in blocks. */
    public static int get() {
        return option().get();
    }
}
