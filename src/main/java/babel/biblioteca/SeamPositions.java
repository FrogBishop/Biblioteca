package babel.biblioteca;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Coordinate folding for the vertical seam.
 *
 * A looping dimension is a ring of {@code level.getHeight()} rows: the row at {@code minY} and the
 * row at {@code minY + height} are the same row, and every row outside the world has a periodic
 * equivalent inside it. This class is the single implementation of that arithmetic, so the level
 * hooks, the collision and light hooks and the packet handlers cannot disagree about it.
 *
 * Two different folds are needed:
 *
 * <ul>
 *   <li>{@link #foldBlockPos} and {@link #foldY} wrap a row that fell off an edge back into the
 *       period. They keep state inside the world.</li>
 *   <li>{@link #layer} and {@link #foldToward} pick the copy of a row nearest a reference point,
 *       which is how a packet or a render state stays at the copy the receiver is looking at.</li>
 * </ul>
 *
 * Both folds are no-ops for a position that is already in range, so callers that are not sure
 * whether a coordinate crossed the seam can always call them.
 */
public final class SeamPositions {

    private SeamPositions() {}

    /** True when {@code y} is one of the level's rows. */
    public static boolean isInside(Level level, int y) {
        return y >= level.getMinY() && y < level.getMinY() + level.getHeight();
    }

    /**
     * Folds a block position into the level's rows. Returns the same instance when the position is
     * already inside the world, so the in-range path allocates nothing.
     */
    public static BlockPos foldBlockPos(Level level, BlockPos pos) {
        int y = pos.getY();
        if (isInside(level, y)) return pos;

        int minY = level.getMinY();
        int height = level.getHeight();
        return new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
    }

    /**
     * Folds a double Y into the level's rows, keeping the fraction of the block. Returns the input
     * unchanged when it is already in range.
     */
    public static double foldY(Level level, double y) {
        int minY = level.getMinY();
        int height = level.getHeight();
        if (y >= minY && y < minY + height) return y;

        double floored = Math.floor(y);
        double fraction = y - floored;
        return Math.floorMod((int) floored - minY, height) + minY + fraction;
    }

    /**
     * How many world heights separate {@code y} from the copy nearest {@code referenceY}.
     * Positive when that copy is above {@code y}, negative when it is below.
     */
    public static long layer(double y, double referenceY, int period) {
        return Math.round((referenceY - y) / (double) period);
    }

    /** The copy of {@code y} closest to {@code referenceY}. */
    public static double foldToward(double y, double referenceY, int period) {
        return y + (double) layer(y, referenceY, period) * (double) period;
    }
}
