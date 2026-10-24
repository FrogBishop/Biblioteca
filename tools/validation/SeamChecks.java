package babel.biblioteca.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** End-to-end native leash ticks and path searches; no mocks of the decisions being fixed. */
public final class SeamChecks {
    public static void run(boolean client) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        new babel.biblioteca.Biblioteca().onInitialize();
        if (client) {
            Class<?> initializer = Class.forName("babel.biblioteca.client.BibliotecaClient");
            initializer.getMethod("onInitializeClient").invoke(initializer.getConstructor().newInstance());
        }
        // Complete the registry lifecycle normally performed by world loading. These fixtures
        // use plain stone/air, with no datapack-specific block classifications.
        net.minecraft.core.registries.BuiltInRegistries.REGISTRY.forEach(registry -> {
            ((net.minecraft.core.MappedRegistry<?>) registry).bindAllTagsToEmpty();
            registry.freeze();
        });
        // Run groups independently so an unfixed build exposes all three regressions in one run.
        var failures = new java.util.ArrayList<Throwable>();
        for (int period : new int[]{16, 384}) {
            verify(failures, () -> checkLeashes(SeamFixture.create(-64, period)));
            verify(failures, () -> checkPositions(SeamFixture.create(-64, period)));
            verify(failures, () -> checkPaths(SeamFixture.create(-64, period)));
        }
        if (!failures.isEmpty()) {
            AssertionError error = new AssertionError("Seam regression failures: " + failures.size());
            failures.forEach(error::addSuppressed);
            throw error;
        }
    }

    private static void checkLeashes(Level level) throws Exception {
        int min = level.getMinY(), period = level.getHeight(), top = min + period;
        TestMob mob = new TestMob(level), holder = new TestMob(level);
        var sounds = SeamFixture.soundLevel();
        for (int direction : new int[]{1, -1}) {
            mob.setPos(0, direction > 0 ? top - 0.5 : min + 0.5, 0);
            holder.setPos(0, direction > 0 ? min + 0.5 : top - 0.5, 0);
            mob.setLeashedTo(holder, false);
            near(mob.leashDistanceTo(holder), 1, "leash center distance across seam");
            Leashable.tickLeash(sounds, mob);
            check(mob.isLeashed() && !mob.snapped, "short seam leash survives native tick");

            holder.setPos(0, mob.getY() + direction * 7, 0);
            mob.setDeltaMovement(Vec3.ZERO);
            Leashable.tickLeash(sounds, mob);
            check(mob.isLeashed(), "elastic seam leash survives native tick");
            check(mob.getDeltaMovement().y * direction > 0 && mob.getDeltaMovement().length() < 1,
                    "spring pulls toward the nearby holder, without a world-height impulse");

            holder.setPos(3, mob.getY() + direction * 3, 0);
            mob.closeRangeLeashBehaviour(holder);
            Vec3 wanted = ((RecordingNavigation) mob.getNavigation()).wanted.subtract(mob.position());
            near(wanted.y / wanted.x, direction, "slack leash navigation follows the short diagonal");
        }
        mob.setPos(0, top - 0.5, 0); holder.setPos(13, min + 0.5, 0);
        mob.setLeashedTo(holder, false);
        Leashable.tickLeash(sounds, mob);
        check(mob.snapped && !mob.isLeashed(), "genuinely overlong leash still breaks");
        SeamFixture.data(level).looping = false;
        mob.snapped = false; holder.setPos(0, min + 0.5, 0);
        mob.setLeashedTo(holder, false);
        near(mob.leashDistanceTo(holder), period - 1, "ordinary dimensions keep straight-line leash lengths");
        Leashable.tickLeash(sounds, mob);
        check(mob.snapped, "ordinary dimension leash still breaks across distant rows");
        System.out.println("PASS: leashes " + period + " rows: both directions, native tick, spring/follow direction, genuine break, non-looping control");
    }

    private static void checkPositions(Level level) {
        int min = level.getMinY(), top = min + level.getHeight();
        for (int direction : new int[]{1, -1}) {
            TestMob entity = new TestMob(level);
            double start = direction > 0 ? top - 0.25 : min + 0.25;
            entity.setPos(0, start, 0);
            for (int tick = 1; tick <= 6; tick++) {
                double previous = entity.getY();
                entity.yOld = entity.yo = previous;
                double raw = start + direction * tick * 0.5;
                entity.setPos(0, raw, 0);
                near(entity.getY() - entity.yOld, direction * 0.5, "consecutive unwrapped writes preserve previous render row");
                near(entity.getY() - entity.yo, direction * 0.5, "consecutive writes preserve previous camera row");
                entity.setPos(0, raw, 0);
                near(entity.getY() - entity.yOld, direction * 0.5, "repeated write in one tick is idempotent");
            }
            entity.setPos(0, start, 0);
            entity.yOld = entity.yo = start;
            double canonicalTarget = direction > 0 ? min + 0.25 : top - 0.25;
            entity.absSnapTo(0, canonicalTarget, 0);
            near(entity.yo, canonicalTarget, "absolute packet does not shift an already-updated camera row");
            near(entity.getY() - entity.yOld, direction * 0.5, "absolute packet keeps body history local");
        }
        System.out.println("PASS: committed movement " + level.getHeight() + " rows: repeated writes, both directions, camera/body history");
    }

    private static void checkPaths(Level level) throws Exception {
        int min = level.getMinY(), top = min + level.getHeight();
        TestMob mob = new TestMob(level);
        mob.setOnGround(true);
        var navigation = new GroundPathNavigation(mob, level);
        // A six-step staircase crosses the stored seam at x=3. All chunks contain only canonical
        // rows; the real navigation snapshot and evaluator must supply both periodic copies.
        for (int x = 0; x <= 6; x++) for (int z = -1; z <= 1; z++) {
            SeamFixture.stone(level, x, top - 4 + x, z);
        }
        mob.setPos(0.5, top - 3, 0.5);
        Path up = navigation.createPath(new BlockPos(6, min + 3, 0), 0);
        check(up != null && up.canReach(), "upward seam stair search reaches its destination");
        near(up.getEndNode().y, top + 3, "upward search keeps contiguous node rows");
        mob.setPos(6.5, min + 3, 0.5);
        Path down = navigation.createPath(new BlockPos(0, top - 3, 0), 0);
        check(down != null && down.canReach(), "downward seam stair search reaches its destination");
        near(down.getEndNode().y, min - 3, "downward search continues below the stored bottom");
        for (int i = 0; i < down.getNodeCount(); i++) {
            Vec3 next = down.getEntityPosAtNode(mob, i);
            check(Math.abs(next.y - mob.getY()) <= 3, "path follower stays beside the next node after folding");
            mob.setPos(next);
        }

        PathNavigationRegion region = new PathNavigationRegion(level, new BlockPos(-16, min - 16, -16), new BlockPos(16, min + 16, 16));
        mob.setPos(3.5, min, 0.5);
        for (var world : new net.minecraft.world.level.CollisionGetter[]{level, region}) {
            check(WalkNodeEvaluator.getPathTypeStatic(new PathfindingContext(world, mob), new BlockPos.MutableBlockPos(3, min, 0)) == PathType.WALKABLE,
                    "bottom row recognizes support in both level and snapshot");
        }
        AABB support = new AABB(3.1, min - 0.8, 0.1, 3.9, min - 0.2, 0.9);
        check(!region.noCollision(mob, support), "snapshot collision sees the periodic top block below the bottom");
        SeamFixture.data(level).looping = false;
        check(region.noCollision(mob, support), "ordinary snapshot retains its physical bottom boundary");
        mob.setPos(6.5, min + 3, 0.5);
        Path ordinary = navigation.createPath(new BlockPos(0, top - 3, 0), 0);
        check(ordinary == null || !ordinary.canReach(), "ordinary dimension does not acquire a periodic path");
        SeamFixture.data(level).looping = true;

        SeamFixture.data(level).blocks.clear();
        SeamFixture.stone(level, 0, min, 0);
        SeamFixture.stone(level, 1, min - 6, 0);
        mob.setPos(0.5, min + 1, 0.5);
        Path unsafeFall = navigation.createPath(new BlockPos(1, top - 5, 0), 0);
        check(unsafeFall == null || !unsafeFall.canReach(), "seam path still respects maximum safe fall distance");
        SeamFixture.data(level).blocks.clear();
        SeamFixture.data(level).reads = 0;
        mob.setOnGround(false);
        WalkNodeEvaluator evaluator = new WalkNodeEvaluator();
        evaluator.prepare(region, mob);
        evaluator.getStart();
        evaluator.done();
        check(SeamFixture.data(level).reads < level.getHeight() + 100, "empty-ring ground scan terminates within one period");
        System.out.println("PASS: pathfinding " + level.getHeight() + " rows: real navigation up/down stairs, support/collision, fall limit, empty ring, non-looping control");
    }

    public static class TestMob extends PathfinderMob {
        boolean snapped;
        public TestMob(Level level) { super(EntityTypes.ZOMBIE, level); setYRot(0); }
        @Override protected PathNavigation createNavigation(Level level) { return new RecordingNavigation(this, level); }
        @Override public void leashTooFarBehaviour() { snapped = true; setLeashData(null); }
    }
    private static final class RecordingNavigation extends GroundPathNavigation {
        Vec3 wanted;
        RecordingNavigation(TestMob mob, Level level) { super(mob, level); }
        @Override public boolean moveTo(double x, double y, double z, double speed) { wanted = new Vec3(x, y, z); return true; }
    }
    private interface Check { void run() throws Exception; }
    private static void verify(java.util.List<Throwable> failures, Check check) {
        try { check.run(); } catch (Throwable failure) { failure.printStackTrace(); failures.add(failure); }
    }
    public static void near(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 1e-6) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    public static void check(boolean success, String message) { if (!success) throw new AssertionError(message); }
}
