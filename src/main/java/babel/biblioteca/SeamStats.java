package babel.biblioteca;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;

/**
 * The "seam crossings" statistic.
 *
 * <p>Registered as a vanilla custom stat, so it is stored in the player's stats file, appears in the
 * statistics screen's General tab, and is synced by the vanilla award path. The counter is bumped
 * from the movement handlers ({@code ServerGamePacketListenerMixin}) on the packet that actually
 * changes the ring copy - not from a tick loop, so it costs nothing while the player stays on one
 * copy.
 *
 * <p>Registration has to happen before the custom-stat registry freezes, which is why the common
 * initializer calls {@link #init()}.
 */
public final class SeamStats {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("biblioteca", "seam_crossings");

    private static Stat<Identifier> seamCrossings;

    private SeamStats() {}

    /** Called from the common initializer; the custom-stat registry is not frozen yet. */
    public static void init() {
        Registry.register(BuiltInRegistries.CUSTOM_STAT, ID, ID);
        seamCrossings = Stats.CUSTOM.get(ID, StatFormatter.DEFAULT);
        Biblioteca.LOGGER.info("[Biblioteca] Registered statistic {}", ID);
    }

    /** Counts one seam crossing for the player. */
    public static void awardCrossing(ServerPlayer player) {
        if (seamCrossings != null) {
            player.awardStat(seamCrossings, 1);
        }
    }
}
