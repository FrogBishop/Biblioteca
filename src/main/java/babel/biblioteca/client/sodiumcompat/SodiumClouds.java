package babel.biblioteca.client.sodiumcompat;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.CloudLayers;
import babel.biblioteca.client.LoopingClientSettings;
import net.minecraft.world.level.Level;

/** Sodium 0.5/0.6 keeps one cloud mesh; only height and native draw state vary per image. */
public final class SodiumClouds {
    private record Frame(float height, boolean multiple) {}
    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();
    private SodiumClouds() {}
    public static Float height() { Frame frame = FRAME.get(); return frame == null ? null : frame.height(); }
    public static boolean multiple() { Frame frame = FRAME.get(); return frame != null && frame.multiple(); }
    public static void draw(Level level, double cameraY, Runnable draw) {
        if (level == null || !LoopingRegistry.isLooping(level)) { draw.run(); return; }
        //? if <1.21.6 {
        /*float height = ((net.minecraft.client.multiplayer.ClientLevel)level).effects().getCloudHeight();
        *///?} else {
        // Newer Sodium versions retain the native cloud renderer and use CloudRendererLoopMixin.
        float height = Float.NaN;
        //?}
        if (!Float.isFinite(height)) { draw.run(); return; }
        CloudLayers layers = new CloudLayers();
        float nearest = layers.prepare(height, cameraY, ClientCompat.renderDistance(), Compat.height(level),
                LoopingClientSettings.multipleCloudLayers());
        Frame previous = FRAME.get();
        try {
            for (int i = 0; i < layers.count(); i++) {
                FRAME.set(new Frame(nearest + layers.shift(i), layers.multiple()));
                draw.run();
            }
        } finally { if (previous == null) FRAME.remove(); else FRAME.set(previous); }
    }
}
