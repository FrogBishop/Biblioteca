package babel.biblioteca.client;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;

/**
 * Places a cloud layer on the periodic copy nearest the camera.
 *
 * A dimension has one absolute cloud height (192 in the overworld) and the
 * renderer draws exactly one layer, so in a looping dimension that layer exists
 * at cloudHeight + n * worldHeight and the copy has to be chosen per frame. With
 * no fold, a player standing at y = -64 gets the layer at 192 - 256 blocks above
 * them - while the copy 128 blocks below is the one that belongs to the ring.
 */
public final class CloudFold {

    private CloudFold() {
    }

    public static float nearest(float cloudHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return cloudHeight;
        if (!LoopingRegistry.isLooping(minecraft.level)) return cloudHeight;

        int period = minecraft.level.getHeight();
        if (period <= 0) return cloudHeight;

        long layers = Math.round((minecraft.player.getEyeY() - cloudHeight) / period);
        return (float) (cloudHeight + layers * (double) period);
    }

    public static int nearest(int cloudHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return cloudHeight;
        if (!LoopingRegistry.isLooping(minecraft.level)) return cloudHeight;

        int period = minecraft.level.getHeight();
        if (period <= 0) return cloudHeight;

        long layers = Math.round((minecraft.player.getEyeY() - cloudHeight) / period);
        return (int) (cloudHeight + layers * period);
    }
}
