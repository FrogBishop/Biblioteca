package babel.biblioteca;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Places effects at the row each listener will actually perceive them at.
 *
 * Sounds, level events (which carry block break and place particles) and the particle API are all
 * broadcast to players within a radius of a coordinate, and the coordinate travels inside the
 * packet. None of them knows about the ring: an effect for a block on the far side of the seam is a
 * whole world height away by that measure, so the radius check culls it and, if it were sent, the
 * client would draw it at a row nobody is looking at.
 *
 * A single packet cannot be right for two listeners standing on opposite sides of the seam, because
 * the correct copy of the position differs per listener. So each broadcast is rebuilt here as one
 * packet per distinct folded position, which in practice is one per side of the seam no matter how
 * many players are listening: a packet is only encoded when it is written to a connection, so the
 * same instance can go to everyone who needs that copy.
 */
public final class SeamEffects {

    private SeamEffects() {}

    /**
     * Radius broadcast, matching PlayerList.broadcast: sends to every listener in the same level
     * within {@code range} of the folded position.
     *
     * @return false when the dimension does not loop, meaning the caller should run vanilla instead
     */
    public static boolean broadcastPerListener(
            final ServerLevel level,
            final @Nullable Player except,
            final double x,
            final double y,
            final double z,
            final double range,
            final DoubleFunction<Packet<?>> packets) {

        if (!LoopingRegistry.isLooping(level)) return false;

        int period = level.getHeight();
        if (period <= 0) return false;

        double rangeSquared = range * range;
        List<ServerPlayer> players = level.players();
        Packets cache = new Packets();

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player == except || player.level() != level) continue;

            double foldedY = SeamPositions.foldToward(y, player.getY(), period);

            double dx = x - player.getX();
            double dy = foldedY - player.getY();
            double dz = z - player.getZ();

            if (dx * dx + dy * dy + dz * dz < rangeSquared) {
                player.connection.send(cache.get(foldedY, packets));
            }
        }

        return true;
    }

    /**
     * The particle API, matching ServerLevel.sendParticles: sends to listeners whose block position
     * centre is within 32 blocks of the position, or 512 when the limiter is overridden.
     *
     * @return the number of listeners sent to, or -1 when the dimension does not loop
     */
    public static int broadcastParticles(
            final ServerLevel level,
            final ParticleOptions particle,
            final boolean overrideLimiter,
            final boolean alwaysShow,
            final double x,
            final double y,
            final double z,
            final int count,
            final double xDist,
            final double yDist,
            final double zDist,
            final double xSpeed,
            final double ySpeed,
            final double zSpeed,
            final ClientboundLevelParticlesPacket.RandomizationType randomizationType) {

        if (!LoopingRegistry.isLooping(level)) return -1;

        int period = level.getHeight();
        if (period <= 0) return -1;

        double range = overrideLimiter ? 512.0 : 32.0;
        List<ServerPlayer> players = level.players();
        Packets cache = new Packets();
        int sent = 0;

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player.level() != level) continue;

            double foldedY = SeamPositions.foldToward(y, player.getY(), period);

            BlockPos probe = player.blockPosition();
            if (!probe.closerToCenterThan(new Vec3(x, foldedY, z), range)) continue;

            player.connection.send(cache.get(foldedY, packetY -> new ClientboundLevelParticlesPacket(
                    particle,
                    overrideLimiter,
                    alwaysShow,
                    x,
                    packetY,
                    z,
                    (float) xDist,
                    (float) yDist,
                    (float) zDist,
                    (float) xSpeed,
                    (float) ySpeed,
                    (float) zSpeed,
                    count,
                    randomizationType)));

            sent++;
        }

        return sent;
    }

    /**
     * Two slots cover the real cases: everyone on one side of the seam, or players on both sides.
     * A third distinct position builds a fresh packet, which is still correct, just not shared.
     */
    private static final class Packets {

        private double y0 = Double.NaN;
        private double y1 = Double.NaN;
        private Packet<?> packet0;
        private Packet<?> packet1;

        Packet<?> get(double foldedY, DoubleFunction<Packet<?>> factory) {
            if (foldedY == y0) return packet0;
            if (foldedY == y1) return packet1;

            Packet<?> packet = factory.apply(foldedY);

            if (Double.isNaN(y0)) {
                y0 = foldedY;
                packet0 = packet;
            } else if (Double.isNaN(y1)) {
                y1 = foldedY;
                packet1 = packet;
            }

            return packet;
        }
    }
}
