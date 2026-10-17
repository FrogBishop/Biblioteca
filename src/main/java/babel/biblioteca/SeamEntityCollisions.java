package babel.biblioteca;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.Predicate;

/**
 * Collects the entity collision shapes that a Y-looping dimension puts on the other side
 * of its seam, expressed in the query's own coordinates.
 *
 * A query for a box near the bottom row has to look one period up to find the entities
 * standing near the top row, because those are the ones it is touching; the shapes they
 * report are then moved back down so callers can treat them like any other collider.
 */
public final class SeamEntityCollisions {

    /** How far from a seam a query still has to consider the opposite row. */
    public static final double REACH = 4.0;

    private SeamEntityCollisions() {}

    /**
     * Appends to {@code out} the shapes of every entity whose ring position overlaps
     * {@code box}, where {@code yShift} is the period to search at (positive to look up,
     * negative to look down) and is undone again on the way out.
     */
    public static void collect(Level level, Entity source, AABB box, double yShift, List<VoxelShape> out) {
        Predicate<Entity> canCollide = source == null
                ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(source::canCollideWith);

        for (Entity entity : level.getEntities(source, box.move(0.0, yShift, 0.0).inflate(1.0E-7), canCollide)) {
            out.add(Shapes.create(entity.getBoundingBox().move(0.0, -yShift, 0.0)));
        }
    }
}
