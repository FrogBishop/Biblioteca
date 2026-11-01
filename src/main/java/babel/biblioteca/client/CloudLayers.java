package babel.biblioteca.client;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;

/** A bounded list of translations of one native cloud mesh, ordered far to near. */
public final class CloudLayers {
    public static final int MAX_LAYERS = 65;
    private final float[] shifts = new float[MAX_LAYERS];
    private int count = 1;
    private boolean multiple;

    public boolean multiple() { return multiple; }
    public int count() { return count; }
    public float shift(int index) { return shifts[index]; }

    public float prepare(float height, double cameraY, int range) {
        var level = Minecraft.getInstance().level;
        boolean looping = level != null && LoopingRegistry.isLooping(level);
        return prepare(height, cameraY, range, looping ? Compat.height(level) : 0,
                looping && LoopingClientSettings.multipleCloudLayers());
    }

    public float prepare(float height, double cameraY, int range, int period, boolean enabled) {
        count = 1; shifts[0] = 0; multiple = enabled && period > 0 && Float.isFinite(height);
        if (period <= 0 || !Float.isFinite(height)) return height;
        float nearest = (float) (height + Math.round((cameraY - height) / period) * (double) period);
        if (!multiple) return nearest;
        double reach = Math.max(0, range) * 16.0;
        int first = Math.max(-32, (int) Math.ceil((cameraY - reach - nearest - 4) / period));
        int last = Math.min(32, (int) Math.floor((cameraY + reach - nearest) / period));
        // Retain the nearest layer even when ordinary cloud height exceeds terrain view distance.
        first = Math.min(first, 0); last = Math.max(last, 0);
        count = 0;
        for (int image = first; image <= last; image++) shifts[count++] = image * (float) period;
        for (int i = 1; i < count; i++) {
            float value = shifts[i]; int j = i;
            while (j > 0 && Math.abs(nearest + shifts[j - 1] + 2 - cameraY) < Math.abs(nearest + value + 2 - cameraY)) {
                shifts[j] = shifts[j - 1]; j--;
            }
            shifts[j] = value;
        }
        return nearest;
    }
}
