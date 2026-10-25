package babel.biblioteca.validation.main100;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.entity.SteppedInterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static babel.biblioteca.validation.main100.SeamChecks.check;
import static babel.biblioteca.validation.main100.SeamChecks.near;

public final class SeamClientChecks {
    public static void run() throws Exception {
        for (int period : new int[]{16, 384}) {
            Level level = SeamFixture.create(-64, period);
            for (Entity entity : List.of(new SeamChecks.TestMob(level), new TestPlayer(level))) {
                for (int direction : new int[]{1, -1}) {
                    checkInterpolation(entity, direction, false);
                    checkInterpolation(entity, direction, true);
                }
            }
            checkRenderStates(level);
            System.out.println("PASS: client " + period + " rows: players/mobs, linear and queued stepped paths, both directions, rendered body/leash states");
        }
    }

    private static void checkInterpolation(Entity entity, int direction, boolean stepped) throws Exception {
        int min = entity.level().getMinY(), top = min + entity.level().getHeight();
        double seam = direction > 0 ? top : min;
        entity.setPos(0, seam - direction * 0.3, 0);
        InterpolationHandler handler = stepped ? SteppedInterpolationHandler.create(entity) : LinearInterpolationHandler.create(entity, 3);
        Field field = Entity.class.getDeclaredField("interpolationHandler"); field.setAccessible(true); field.set(entity, handler);
        if (stepped) {
            entity.moveOrInterpolateTo(new PositionPath.Stepped(point(seam + direction * 0.4, min, top), List.of(
                    new PositionStep(point(seam - direction * 0.1, min, top), 2),
                    new PositionStep(point(seam + direction * 0.1, min, top), 2),
                    new PositionStep(point(seam + direction * 0.4, min, top), 2))), 0, 0, true);
        } else {
            entity.moveOrInterpolateTo(PositionPath.of(point(seam + direction * 0.3, min, top)), 0, 0, true);
        }
        for (int tick = 0; tick < (stepped ? 16 : 3); tick++) {
            if (stepped && tick == 4) {
                entity.moveOrInterpolateTo(new PositionPath.Stepped(point(seam + direction, min, top), List.of(
                        new PositionStep(point(seam + direction * 0.7, min, top), 2),
                        new PositionStep(point(seam + direction, min, top), 2))), 0, 0, true);
            }
            double previous = entity.getY(); entity.yOld = entity.yo = previous;
            handler.interpolate();
            double movement = entity.getY() - previous;
            movement -= Math.rint(movement / (top - min)) * (top - min);
            check(movement * direction >= -1e-6 && Math.abs(movement) < 0.8, "buffered movement stays short and follows packet direction at tick " + tick);
            check(Math.abs(entity.yOld - entity.getY()) < 0.8 && Math.abs(entity.yo - entity.getY()) < 0.8,
                    "body and camera do not interpolate across the world at tick " + tick);
        }
        near(entity.getY(), point(seam + direction * (stepped ? 1 : 0.3), min, top).y, "interpolation reaches packet destination");
    }

    private static Vec3 point(double y, int min, int top) {
        return new Vec3(0, y < min ? y + top - min : y >= top ? y - (top - min) : y, 0);
    }

    private static void checkRenderStates(Level level) throws Exception {
        int min = level.getMinY(), top = min + level.getHeight();
        EntityRenderDispatcher dispatcher = (EntityRenderDispatcher) SeamFixture.allocate(EntityRenderDispatcher.class);
        TestRenderer renderer = (TestRenderer) SeamFixture.allocate(TestRenderer.class);
        SeamChecks.TestMob mob = new SeamChecks.TestMob(level);
        Field renderers = EntityRenderDispatcher.class.getDeclaredField("renderers"); renderers.setAccessible(true);
        renderers.set(dispatcher, Map.of(mob.getType(), renderer));
        TestCamera camera = new TestCamera(); dispatcher.camera = camera;
        // The renderer fixture supplies only the native render-state layout. The real dispatcher
        // extracts/folds it, so test both camera layers and all four lead attachment endpoints.
        for (int direction : new int[]{1, -1}) for (boolean shiftedCamera : new boolean[]{false, true}) {
            double start = direction > 0 ? top - 0.5 : min + 0.5;
            double end = direction > 0 ? min + 0.5 : top - 0.5;
            mob.setPos(0, start, 0); mob.yOld = start;
            double cameraY = shiftedCamera ? end : start;
            camera.at(cameraY);
            renderer.leashEnd = end;
            EntityRenderState state = dispatcher.extractEntity(mob, 0.5f);
            check(Math.abs(state.y - cameraY) <= 1, "rendered entity stays beside camera across seam");
            for (var leash : state.leashStates) near(leash.end.y - leash.start.y, direction, "rendered leash takes the short seam path");
            check(mob.shouldRender(0, cameraY, 0), "seam entity passes native distance culling");
        }
    }

    public static final class TestPlayer extends Player {
        TestPlayer(Level level) { super(level, new GameProfile(UUID.randomUUID(), "seam-test")); }
        @Override public GameType gameMode() { return GameType.SURVIVAL; }
    }
    private static final class TestCamera extends Camera {
        void at(double y) { setPosition(0, y, 0); }
    }
    private static final class TestRenderer extends EntityRenderer<Entity, EntityRenderState> {
        double leashEnd;
        private TestRenderer() { super(null); }
        @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
        @Override public void extractRenderState(Entity entity, EntityRenderState state, float partial) {
            state.y = entity.yOld + (entity.getY() - entity.yOld) * partial;
            state.leashStates = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                var leash = new EntityRenderState.LeashState();
                leash.start = new Vec3(i, state.y, 0); leash.end = new Vec3(i, leashEnd, 0);
                state.leashStates.add(leash);
            }
        }
        @Override protected void finalizeRenderState(Entity entity, EntityRenderState state) { }
    }
}
