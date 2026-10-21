package babel.biblioteca.client;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Listener-relative positions for sounds and subtitles in a looping dimension. */
public final class LoopingSounds {

    private LoopingSounds() {}

    public static int period() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && LoopingRegistry.isLooping(level) ? level.getHeight() : 0;
    }

    public static double nearestY(double y) {
        int period = period();
        return period > 0
                ? SeamPositions.foldToward(y, ClientCompat.cameraPosition().y, period)
                : y;
    }

    public static Vec3 nearestPosition(Vec3 source, Vec3 listener) {
        int period = period();
        if (period <= 0) return source;
        double y = SeamPositions.foldToward(source.y, listener.y, period);
        return y == source.y ? source : new Vec3(source.x, y, source.z);
    }
}
