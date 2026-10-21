package babel.biblioteca;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Coordinate folding for the boundary between the world's top and bottom.
 *
 * A looping dimension is a ring of {@code level.getHeight()} rows: the row at {@code minY} and the
 * row at {@code minY + height} are the same row, and every row outside the world has a periodic
 * equivalent inside it. These helpers are shared by world access, movement and positional effects.
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
 * Canonical folds leave coordinates already inside the world unchanged. A nearest-copy query can
 * move an in-range coordinate by a whole period when its reference point is across the boundary.
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

    /**
     * The copy of a point nearest a reference row, for state that has to stay beside an observer:
     * packet targets, interpolation points, look targets.
     */
    public static Vec3 foldToward(Level level, Vec3 position, double referenceY) {
        int period = level.getHeight();
        if (period <= 0) return position;

        double folded = foldToward(position.y, referenceY, period);
        return folded == position.y ? position : new Vec3(position.x, folded, position.z);
    }

    /**
     * The copy of a box nearest a reference row. The shift is always a whole number of periods, so
     * the box keeps its size and only its ring position is chosen.
     */
    public static AABB foldToward(Level level, AABB box, double referenceY) {
        int period = level.getHeight();
        if (period <= 0) return box;

        long layers = layer(box.getCenter().y, referenceY, period);
        return layers == 0L ? box : box.move(0.0, (double) layers * period, 0.0);
    }

    /**
     * Squared distance from a point to a box measured around the ring: the point is compared with
     * the copy of the box nearest to it, which is the copy the point is actually beside.
     */
    public static double distanceToSqr(Level level, Vec3 point, AABB box) {
        return foldToward(level, box, point.y).distanceToSqr(point);
    }

    /** Ring-aware squared distance between two points. */
    public static double distanceToSqr(Level level, Vec3 from, Vec3 to) {
        return from.distanceToSqr(foldToward(level, to, from.y));
    }

    /**
     * The row to compare with when measuring distance, chosen for the ring.
     *
     * <p>Nearly every distance query compares two points that are already close, so the copy that
     * has to be picked is the query's own row and the answer comes back unchanged. The check is
     * written as a range comparison instead of a rounding so that this common case stays a pair of
     * comparisons; only a query across the boundary pays for the rounding.
     */
    public static double foldForDistance(Level level, double y, double referenceY) {
        int period = level.getHeight();
        if (period <= 0) return y;

        double half = (double) period * 0.5;
        double delta = y - referenceY;
        if (delta <= half && delta >= -half) return y;

        return foldToward(y, referenceY, period);
    }
}
