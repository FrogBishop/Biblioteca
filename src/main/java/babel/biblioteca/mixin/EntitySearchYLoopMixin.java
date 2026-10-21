package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Lets an entity search find the entities that are beside it on the ring, not only the ones that are
 * beside it in absolute space.
 *
 * <p>A search is a box and an intersection test. Everything an AI goal, an explosion, a command
 * selector or a block entity asks for is evaluated this way, so the boundary appears as a wall: a
 * mob on the top row has no box-shaped neighbourhood that contains the bottom row, no matter how
 * close the ring puts it. Distance checks were corrected separately and cannot help here, because
 * the entity is never even a candidate.
 *
 * <p>The search box is widened by one world height when it comes within reach of a seam, and the
 * results are then filtered by intersecting each entity's box moved onto the copy nearest the query.
 * The filter is what keeps the change safe: an entity 384 blocks away in absolute terms is returned
 * only when its ring position really is inside the queried box, so every caller keeps its own
 * "things in this box" contract. Callers that need coordinates - collisions, geometry - still read
 * the entity's real position, which is why {@code EntityGetterYLoopMixin} continues to provide the
 * folded collision shapes.
 *
 * <p>Boxes that stay at least {@link #biblioteca$SEAM_MARGIN} rows away from both seams are not
 * widened at all: no entity stored in one period, and no copy of it, can reach such a box. That
 * keeps the hot path of ordinary AI and collision queries on the original code.
 */
@Mixin(Level.class)
public abstract class EntitySearchYLoopMixin {

    /**
     * Clearance from a seam below which a search has to be widened. It has to cover the tallest
     * bounding box an entity can have, because a body that pokes out of the world also pokes into
     * the opposite period.
     */
    private static final double biblioteca$SEAM_MARGIN = 16.0;

    /** Guards against widening an already-widened box when one search delegates to another. */
    private static final ThreadLocal<Boolean> biblioteca$IN_SEARCH = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @WrapMethod(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;"
            + "Ljava/util/function/Predicate;)Ljava/util/List;")
    private List<Entity> biblioteca$searchAroundTheRing(
            Entity except, AABB box, Predicate<? super Entity> selector, Operation<List<Entity>> original) {

        Level level = (Level) (Object) this;
        AABB search = biblioteca$widen(level, box);
        if (search == box) return original.call(except, box, selector);

        List<Entity> found = biblioteca$bounded(() -> original.call(except, search, selector));
        return biblioteca$keepRingIntersections(level, box, found);
    }

    @WrapMethod(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;"
            + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;")
    private <T extends Entity> List<T> biblioteca$searchTypesAroundTheRing(
            EntityTypeTest<Entity, T> type, AABB box, Predicate<? super T> selector,
            Operation<List<T>> original) {

        Level level = (Level) (Object) this;
        AABB search = biblioteca$widen(level, box);
        if (search == box) return original.call(type, box, selector);

        List<T> found = biblioteca$bounded(() -> original.call(type, search, selector));
        return biblioteca$keepRingIntersections(level, box, found);
    }

    /**
     * The collecting variant, used by command selectors and anything else that fills a list. It is
     * evaluated without a result cap and trimmed afterwards: filtering after a cap would return
     * fewer entities than asked for when the first candidates are the ones across the seam.
     */
    @WrapMethod(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;"
            + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V")
    private <T extends Entity> void biblioteca$collectAroundTheRing(
            EntityTypeTest<Entity, T> type, AABB box, Predicate<? super T> selector,
            List<? super T> output, int maxResults, Operation<Void> original) {

        Level level = (Level) (Object) this;
        AABB search = biblioteca$widen(level, box);
        if (search == box) {
            original.call(type, box, selector, output, maxResults);
            return;
        }

        int start = output.size();
        biblioteca$boundedRun(() -> original.call(type, search, selector, output, Integer.MAX_VALUE));

        for (int i = output.size() - 1; i >= start; i--) {
            Entity entity = (Entity) output.get(i);
            if (i >= maxResults || !biblioteca$ringIntersects(level, box, entity.getBoundingBox())) {
                output.remove(i);
            }
        }
    }

    private static <T> T biblioteca$bounded(Supplier<T> action) {
        biblioteca$IN_SEARCH.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            biblioteca$IN_SEARCH.set(Boolean.FALSE);
        }
    }

    private static void biblioteca$boundedRun(Runnable action) {
        biblioteca$IN_SEARCH.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            biblioteca$IN_SEARCH.set(Boolean.FALSE);
        }
    }

    private static AABB biblioteca$widen(Level level, AABB box) {
        if (box == null || !LoopingRegistry.isLooping(level)) return box;
        if (biblioteca$IN_SEARCH.get()) return box;

        int period = level.getHeight();
        if (period <= 0) return box;

        int minY = level.getMinY();
        int maxY = level.getMaxY();
        if (box.minY >= minY + biblioteca$SEAM_MARGIN && box.maxY <= maxY - biblioteca$SEAM_MARGIN) return box;

        return box.inflate(0.0, period, 0.0);
    }

    private static <T extends Entity> List<T> biblioteca$keepRingIntersections(
            Level level, AABB box, List<T> found) {

        if (found == null || found.isEmpty()) return found;

        List<T> kept = new ArrayList<>(found.size());
        for (T entity : found) {
            if (biblioteca$ringIntersects(level, box, entity.getBoundingBox())) {
                kept.add(entity);
            }
        }

        return kept;
    }

    private static boolean biblioteca$ringIntersects(Level level, AABB box, AABB entityBox) {
        // A box can reach far past the world, for example a search area around a mob on the top
        // row. Its centre is then a poor reference row; clamp it to the world so the copy that is
        // tested is the one nearest the part of the box that actually contains rows.
        double reference = Math.clamp(box.getCenter().y, level.getMinY(), level.getMaxY());
        return SeamPositions.foldToward(level, entityBox, reference).intersects(box);
    }
}
