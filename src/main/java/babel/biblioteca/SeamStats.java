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
 * Registered as a vanilla custom stat, so it is stored in the player's stats file, appears in the
 * statistics screen's General tab and is synced by the vanilla award path. The counter is bumped
 * from the movement handlers (ServerGamePacketListenerMixin) on the packet that actually changes
 * the ring copy - not from a tick loop.
 */
public final class SeamStats {

    public static final Identifier SEAM_CROSSINGS_ID =
            Identifier.fromNamespaceAndPath("biblioteca", "seam_crossings");

    private static Stat<Identifier> seamCrossings;

    private SeamStats() {}

    /** Called from the common initializer; the custom-stat registry is not frozen yet. */
    public static void init() {
        Registry.register(BuiltInRegistries.CUSTOM_STAT, SEAM_CROSSINGS_ID, SEAM_CROSSINGS_ID);
        seamCrossings = Stats.CUSTOM.get(SEAM_CROSSINGS_ID, StatFormatter.DEFAULT);
        Biblioteca.LOGGER.info("[Biblioteca] Registered statistic {}", SEAM_CROSSINGS_ID);
    }

    /** Counts one seam crossing for the player. */
    public static void awardCrossing(ServerPlayer player) {
        if (seamCrossings != null) {
            player.awardStat(seamCrossings, 1);
        }
    }
}
