package babel.biblioteca.validation;

import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Real transformed entity/search/spawn methods against a tiny in-memory level, without a game world. */
public final class SeamChecks {
    public static void run(boolean client) throws Exception {
        int min = SeamFixture.hasSections() ? -64 : 0;
        int nativeHeight = SeamFixture.hasSections() ? 384 : 256;
        for (int period : new int[]{16, nativeHeight}) {
            Level level = SeamFixture.create(min, period);
            double top = min + period;
            ItemEntity low = item(level, min + 0.25);
            ItemEntity high = item(level, top - 0.25);
            near(low.distanceToSqr(high), 0.25, "entity distance across seam");
            near(low.distanceTo(high), 0.5, "float entity distance across seam");
            near(SeamPositions.distanceToSqr(level, new Vec3(0, min - 0.25, 0), new AABB(-1, top - 0.5, -1, 1, top, 1)),
                    0, "point inside periodic hitbox");
            level.getClass().getField("looping").setBoolean(level, false);
            near(low.distanceToSqr(high), (period - 0.5) * (period - 0.5), "non-looping entity distance");
            level.getClass().getField("looping").setBoolean(level, true);

            SeamFixture.Data data = SeamFixture.data(level);
            ItemEntity unrelated = item(level, min + period * 0.5);
            data.entities.addAll(List.of(unrelated, high, low));
            AABB query = new AABB(-1, min - 1, -1, 1, min + 1, 1);
            check(level.getEntities((Entity) null, query, e -> true).equals(List.of(high, low)), "ring search filters widened middle rows");
            check(typedSearch(level, query, e -> true).equals(List.of(high, low)), "typed ring search");
            if (!SeamFixture.hasSections()) {
                check(Level.class.getMethod("getLoadedEntitiesOfClass", Class.class, AABB.class, java.util.function.Predicate.class)
                        .invoke(level, ItemEntity.class, query, null).equals(List.of(high, low)), "loaded class search in legacy chunks");
                check(Level.class.getMethod("getEntities", EntityType.class, AABB.class, java.util.function.Predicate.class)
                        .invoke(level, high.getType(), query, (java.util.function.Predicate<Entity>) e -> true)
                        .equals(List.of(high, low)), "legacy EntityType query");
            }
            check(level.getEntities((Entity) null, query.move(0, period * 3.0, 0), e -> true).equals(List.of(high, low)), "query in a distant periodic frame");
            check(level.getEntities((Entity) null, query, e -> !level.getEntities((Entity) null,
                    query.move(0, period, 0), nested -> nested == low).isEmpty()).equals(List.of(high, low)), "independent nested query keeps its own ring frame");
            checkOptionalSearchApis(level, query, high, low);
            data.entities.clear();

            // Successive unwrapped writes must not shift an already-folded previous row again.
            high.yOld = top - 0.25; high.yo = high.yOld;
            high.setPos(0, top + 0.25, 0);
            near(high.getY(), min + 0.25, "canonical position");
            near(high.yOld, min - 0.25, "previous row follows first crossing");
            high.yOld = high.getY(); high.yo = high.yOld;
            high.setPos(0, top + 0.5, 0);
            near(high.yOld, min + 0.25, "repeated unwrapped writes keep interpolation local");
            high.yOld = top - 0.1; high.yo = high.yOld;
            high.setPos(0, min + 0.1, 0);
            near(high.yOld, min - 0.1, "already canonical packet crossing");

            checkSpawnOffset(level, period);
            checkPathSurface(level, period, high);
            ItemFrame frame = new ItemFrame(level, new BlockPos(0, (int) top, 0), Direction.NORTH);
            near(frame.getBoundingBox().getCenter().y, frame.getY(), "item frame box matches entity position");
            Method anchor = find(frame.getClass(), "biblioteca$attachmentPos", 0);
            check(((BlockPos) anchor.invoke(frame)).getY() == min, "item frame attachment follows box");
            if (client) checkInterpolation(level, top);
        }
        System.out.println("DATA_OK seam: native entity queries, previous positions, spawn/path surfaces, path nodes and decoration anchors (16/" + nativeHeight + " rows)" + (client ? ", native/queued client interpolation" : ""));
    }

    private static ItemEntity item(Level level, double y) throws Exception {
        Class<?> types;
        try { types = Class.forName("net.minecraft.world.entity.EntityTypes"); }
        catch (ClassNotFoundException ignored) { types = EntityType.class; }
        ItemEntity entity = new ItemEntity((EntityType<? extends ItemEntity>) types.getField("ITEM").get(null), level);
        entity.setPos(0, y, 0);
        return entity;
    }

    private static void checkSpawnOffset(Level level, int period) throws Exception {
        int min = SeamFixture.minimum(level);
        Method offset = EntityType.class.getDeclaredMethod("getYOffset", net.minecraft.world.level.LevelReader.class, BlockPos.class, boolean.class, AABB.class);
        offset.setAccessible(true);
        int top = min + period;
        SeamFixture.Data data = SeamFixture.data(level);
        for (int ground : new int[]{top - 1, top - 2}) {
            data.blocks.clear();
            data.blocks.put(new BlockPos(0, ground, 0), Blocks.STONE.defaultBlockState());
            int spawn = ground + 1 == top ? min : ground + 1;
            // The temporary spawn move is one row up and has already been committed/folded.
            double temporary = spawn + 1 == top ? min : spawn + 1;
            AABB box = new AABB(0.2, temporary, 0.2, 0.8, temporary + 1.8, 0.8);
            double result = (double) offset.invoke(null, level, new BlockPos(0, spawn, 0), true, box);
            near(result, 0.0, "spawn offset above boundary ground " + ground);
        }
        data.blocks.clear();
    }

    private static void checkInterpolation(Level level, double top) throws Exception {
        int min = SeamFixture.minimum(level);
        ItemEntity entity = item(level, top - 0.3);
        Class<?> handlerType = null;
        try { handlerType = Class.forName("net.minecraft.world.entity.InterpolationHandler"); }
        catch (ClassNotFoundException ignored) { }
        if (handlerType == null) {
            // 1.20.4--1.21.4 consumes stored target coordinates on every interpolation step.
            Method step;
            try {
                step = Entity.class.getDeclaredMethod("lerpPositionAndRotationStep", int.class, double.class, double.class, double.class, double.class, double.class);
            } catch (NoSuchMethodException legacy) {
                checkLivingInterpolation(level, top);
                return;
            }
            step.setAccessible(true);
            for (int remaining = 3; remaining > 0; remaining--) {
                double previous = entity.getY();
                entity.yOld = previous; entity.yo = previous;
                step.invoke(entity, remaining, 0.0, min + 0.3, 0.0, 0.0, 0.0);
                check(Math.abs(SeamPositions.foldToward(entity.getY(), previous, (int) (top - min)) - previous) < 0.3, "legacy interpolation crosses in short steps");
            }
            near(entity.getY(), min + 0.3, "legacy interpolation ends at packet row");
            return;
        }
        if (!handlerType.isInterface()) {
            Object handler = handlerType.getConstructor(Entity.class).newInstance(entity);
            handlerType.getMethod("interpolateTo", Vec3.class, float.class, float.class).invoke(handler, new Vec3(0, top + 0.3, 0), 0f, 0f);
            for (int tick = 0; tick < 3; tick++) {
                double previous = entity.getY();
                entity.yOld = previous; entity.yo = previous;
                handlerType.getMethod("interpolate").invoke(handler);
                check(Math.abs(SeamPositions.foldToward(entity.getY(), previous, (int) (top - min)) - previous) < 0.3, "stored interpolation target remains near entity");
            }
            near(entity.getY(), min + 0.3, "stored interpolation ends at packet row");
        } else {
            Class<?> linear = Class.forName("net.minecraft.world.entity.LinearInterpolationHandler");
            Object handler = linear.getMethod("create", Entity.class).invoke(null, entity);
            Class<?> path = Class.forName("net.minecraft.world.entity.PositionPath");
            Object target = path.getMethod("of", Vec3.class).invoke(null, new Vec3(0, top + 0.3, 0));
            handlerType.getMethod("interpolateTo", path, float.class, float.class, boolean.class).invoke(handler, target, 0f, 0f, true);
            for (int tick = 0; tick < 3; tick++) {
                double previous = entity.getY();
                entity.yOld = previous; entity.yo = previous;
                handlerType.getMethod("interpolate").invoke(handler);
                check(Math.abs(SeamPositions.foldToward(entity.getY(), previous, (int) (top - min)) - previous) < 0.3, "linear path remains near entity");
            }
            near(entity.getY(), min + 0.3, "linear path ends at packet row");
            checkSteppedInterpolation(level, top, handlerType, path);
        }
    }

    private static void checkPathSurface(Level level, int period, Entity entity) throws Exception {
        int min = SeamFixture.minimum(level);
        Class<?> navigation = Class.forName("net.minecraft.world.entity.ai.navigation.GroundPathNavigation");
        Method surface = navigation.getDeclaredMethod("biblioteca$findSurfacePosition", Level.class, BlockPos.class, int.class);
        surface.setAccessible(true);
        SeamFixture.Data data = SeamFixture.data(level);
        int top = min + period;
        data.blocks.put(new BlockPos(0, top - 1, 0), Blocks.STONE.defaultBlockState());
        BlockPos target = (BlockPos) surface.invoke(null, level, new BlockPos(0, min + 1, 0), period);
        check(target.equals(new BlockPos(0, min, 0)), "ground destination finds surface across lower seam");
        target = (BlockPos) surface.invoke(null, level, new BlockPos(0, top - 1, 0), period);
        check(target.equals(new BlockPos(0, top, 0)), "ground destination finds air across upper seam");
        data.blocks.clear();
        surface.invoke(null, level, new BlockPos(0, min, 0), period); // empty rings terminate
        var path = new net.minecraft.world.level.pathfinder.Path(List.of(
                new net.minecraft.world.level.pathfinder.Node(0, top - 1, 0)), new BlockPos(0, top - 1, 0), true);
        entity.setPos(0, min + 0.1, 0);
        near(path.getEntityPosAtNode(entity, 0).y, min - 1, "path follower sees the node in its own period");
    }

    private static void checkSteppedInterpolation(Level level, double top, Class<?> handlerType, Class<?> pathType) throws Exception {
        int min = SeamFixture.minimum(level);
        ItemEntity entity = item(level, top - 0.3);
        Object handler = Class.forName("net.minecraft.world.entity.SteppedInterpolationHandler")
                .getMethod("create", Entity.class).invoke(null, entity);
        Field field = Entity.class.getDeclaredField("interpolationHandler"); field.setAccessible(true); field.set(entity, handler);
        Class<?> stepType = Class.forName("net.minecraft.world.entity.PositionStep");
        var step = stepType.getConstructor(Vec3.class, int.class);
        var stepped = Class.forName("net.minecraft.world.entity.PositionPath$Stepped").getConstructor(Vec3.class, List.class);
        Object path = stepped.newInstance(new Vec3(0, min + 0.4, 0), List.of(
                step.newInstance(new Vec3(0, top - 0.1, 0), 2),
                step.newInstance(new Vec3(0, min + 0.1, 0), 2), step.newInstance(new Vec3(0, min + 0.4, 0), 2)));
        Method move = Entity.class.getMethod("moveOrInterpolateTo", pathType, float.class, float.class, boolean.class);
        move.invoke(entity, path, 0f, 0f, true);
        for (int tick = 0; tick < 16; tick++) {
            if (tick == 4) {
                Object next = stepped.newInstance(new Vec3(0, min + 1, 0), List.of(
                        step.newInstance(new Vec3(0, min + 0.7, 0), 2), step.newInstance(new Vec3(0, min + 1, 0), 2)));
                move.invoke(entity, next, 0f, 0f, true);
            }
            double previous = entity.getY();
            entity.yOld = previous; entity.yo = previous;
            handlerType.getMethod("interpolate").invoke(handler);
            check(Math.abs(SeamPositions.foldToward(entity.getY(), previous, (int) (top - min)) - previous) < 1,
                    "queued stepped paths stay continuous at tick " + tick);
            check(Math.abs(entity.yOld - entity.getY()) < 1, "stepped path keeps previous rendering row local");
        }
        near(entity.getY(), min + 1, "queued stepped path reaches final row");
    }

    private static List<?> typedSearch(Level level, AABB box, java.util.function.Predicate<Entity> selector) throws Exception {
        if (!SeamFixture.hasSections()) {
            return (List<?>) Level.class.getMethod("getEntitiesOfClass", Class.class, AABB.class, java.util.function.Predicate.class)
                    .invoke(level, ItemEntity.class, box, selector);
        }
        Class<?> types = Class.forName("net.minecraft.world.level.entity.EntityTypeTest");
        Object type = types.getMethod("forClass", Class.class).invoke(null, ItemEntity.class);
        return (List<?>) Level.class.getMethod("getEntities", types, AABB.class, java.util.function.Predicate.class)
                .invoke(level, type, box, selector);
    }

    private static void checkOptionalSearchApis(Level level, AABB box, ItemEntity high, ItemEntity low) throws Exception {
        if (!SeamFixture.hasSections()) return;
        Class<?> types = Class.forName("net.minecraft.world.level.entity.EntityTypeTest");
        Object type = types.getMethod("forClass", Class.class).invoke(null, ItemEntity.class);
        try {
            Method exists = Level.class.getMethod("hasEntities", types, AABB.class, java.util.function.Predicate.class);
            check((boolean) exists.invoke(level, type, box, (java.util.function.Predicate<Entity>) e -> e == high),
                    "short-circuit search sees the opposite seam");
        } catch (NoSuchMethodException absent) { }
        try {
            Method collect = Level.class.getMethod("getEntities", types, AABB.class, java.util.function.Predicate.class, List.class, int.class);
            List<Entity> limited = new ArrayList<>();
            collect.invoke(level, type, box, (java.util.function.Predicate<Entity>) e -> true, limited, 1);
            check(limited.equals(List.of(high)), "cap is applied after ring filtering");
            limited.clear(); limited.add(low);
            collect.invoke(level, type, box, (java.util.function.Predicate<Entity>) e -> e != low, limited, 2);
            check(limited.equals(List.of(low, high)), "collecting query retains existing results");
        } catch (NoSuchMethodException absent) { /* These releases cap results in the caller. */ }
    }

    private static void checkLivingInterpolation(Level level, double top) throws Exception {
        int min = SeamFixture.minimum(level);
        Entity entity = SeamFixture.living(level);
        entity.setPos(0, top - 0.3, 0);
        Method lerp = null;
        for (Method candidate : entity.getClass().getMethods()) {
            if (candidate.getName().equals("lerpTo") && candidate.getParameterTypes()[0] == double.class) { lerp = candidate; break; }
        }
        if (lerp == null) throw new AssertionError("Missing native living interpolation entry");
        if (lerp.getParameterCount() == 7) lerp.invoke(entity, 0.0, min + 0.3, 0.0, 0f, 0f, 3, false);
        else lerp.invoke(entity, 0.0, min + 0.3, 0.0, 0f, 0f, 3);
        Field steps = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("lerpSteps"); steps.setAccessible(true);
        Method tick = net.minecraft.world.entity.LivingEntity.class.getMethod("aiStep");
        for (int remaining = 3; remaining > 0; remaining--) {
            double previous = entity.getY();
            entity.yOld = previous; entity.yo = previous;
            steps.setInt(entity, remaining);
            boolean committed = false;
            SeamFixture.stopAfterPosition(entity, true);
            try { tick.invoke(entity); }
            catch (java.lang.reflect.InvocationTargetException call) {
                if (!(call.getCause() instanceof SeamFixture.PositionRecorded)) throw call;
                committed = true;
            } finally { SeamFixture.stopAfterPosition(entity, false); }
            check(committed, "legacy aiStep must commit its native interpolated position");
            check(Math.abs(SeamPositions.foldToward(entity.getY(), previous, SeamFixture.period(level)) - previous) < 0.3,
                    "legacy living interpolation crosses in short steps");
        }
        near(entity.getY(), min + 0.3, "legacy living interpolation ends at packet row");
    }

    private static Method find(Class<?> type, String name, int count) throws Exception {
        for (Method method : type.getMethods()) if (method.getName().equals(name) && method.getParameterCount() == count) return method;
        throw new NoSuchMethodException(name);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-6, message + ": " + actual + " != " + expected);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
