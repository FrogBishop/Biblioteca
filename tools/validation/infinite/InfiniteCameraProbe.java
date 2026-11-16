package biblioteca.infiniteprobe;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.world.LoopingDimensionManager;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.server.ServerStartCallback;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual local-player movement and rendered Camera.setup samples, including ticks after crossing. */
public final class InfiniteCameraProbe implements ClientModInitializer {
    private static final Map<String, String> failures = new LinkedHashMap<>();
    private static final int CASES = 6, STEPS = 32;
    private static volatile Throwable serverFailure;
    private static boolean opened, done, pendingPose, sample;
    private static int ticks, worldTicks, scenario = -1, step, waiting, frames, partialFrames, checks, poseCount;
    private static int caseFrames, casePartialFrames;
    private static double expectedOldY, maxCameraError;

    @Override public void onInitializeClient() {
        ServerStartCallback.EVENT.register(server -> {
            try {
                var level = Compat.overworld(server);
                level.getChunk(0, 0);
                for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++)
                    for (int y = 0; y < 256; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++) if (x == 4 || x == 12 || z == 4 || z == 12) {
                    level.setBlock(new BlockPos(x, 255, z), Blocks.GLOWSTONE.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, 0, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
                }
                level.setDayTime(6000);
                LoopingDimensionManager.setLooping(Compat.dimension(level), true);
                System.out.println("INFINITE_CAMERA_PREPARED");
            } catch (Throwable error) { serverFailure = error; }
        });
    }

    public static void tick(Minecraft client) {
        if (done) return;
        try {
            if (!opened) {
                if (client.getOverlay() != null || !(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
                opened = true;
                client.selectLevel("infinite-world", "Biblioteca camera validation", null);
                return;
            }
            if (++ticks > 1600) throw new AssertionError("camera probe timed out");
            if (serverFailure != null) throw new AssertionError("server setup", serverFailure);
            if (client.level == null || client.player == null || client.screen != null) return;
            if (++worldTicks < 60) return;
            client.options.hideGui = true;
            for (var key : java.util.List.of(client.options.keyUp, client.options.keyDown, client.options.keyLeft,
                    client.options.keyRight, client.options.keyJump, client.options.keyShift)) key.setDown(false);
            client.player.setDeltaMovement(Vec3.ZERO);
            client.player.yRot = client.player.yRotO = 180;
            client.player.xRot = client.player.xRotO = 0;
            if (scenario < 0 || step >= STEPS) {
                if (scenario >= 0) {
                    check(caseFrames > 0 && casePartialFrames > 0, "render coverage " + scenario,
                            "frames=" + caseFrames + " fractional=" + casePartialFrames);
                    System.out.println("INFINITE_CAMERA_CASE_DONE scenario=" + scenario + " frames=" + caseFrames
                            + " partialFrames=" + casePartialFrames + " player=" + client.player.position());
                }
                if (++scenario == CASES) { finish(client); return; }
                sample = false; pendingPose = true; waiting = 0; step = 0; caseFrames = casePartialFrames = 0;
                // First person, rear third person and front third person, each in both directions.
                client.options.thirdPersonView = scenario / 2;
                var uuid = client.player.getUUID();
                double start = scenario % 2 == 0 ? 0.25 : 255.75;
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var server = client.getSingleplayerServer();
                        var player = server.getPlayerList().getPlayer(uuid);
                        player.setGameMode(GameType.SPECTATOR);
                        player.setDeltaMovement(Vec3.ZERO);
                        player.connection.teleport(8.5, start, 8.5, 180, 0);
                    } catch (Throwable error) { serverFailure = error; }
                });
                poseCount++;
                return;
            }
            if (pendingPose) {
                double start = scenario % 2 == 0 ? 0.25 : 255.75;
                if (Math.abs(client.player.getY() - start) > 0.01 || !client.player.isSpectator()) { waiting = 0; return; }
                if (++waiting < 20) return;
                pendingPose = false;
                check(LoopingRegistry.isLooping(client.level), "joined loop state", "disabled");
            }
            var player = client.player;
            int sign = scenario % 2 == 0 ? -1 : 1;
            double dy = step == 0 ? sign * 0.75 : step < 10 ? 0 : step < 18 ? sign * 0.125
                    : step < 26 ? -sign * 0.25 : 0;
            double before = player.getY();
            // Move through the actual player/Entity path. No teleport or history reset inside the sequence.
            player.move(MoverType.SELF, new Vec3(step < 10 ? 0.05 : 0, dy, 0));
            player.move(MoverType.SELF, new Vec3(0, 0, 0.025));
            double expected = before + dy - Math.floor((before + dy) / 256) * 256;
            expectedOldY = expected - dy;
            sample = true;
            near(player.getY(), expected, "position", scenario + "/" + step);
            near(player.getBoundingBox().minY, expected, "box", scenario + "/" + step);
            near(player.yo, expectedOldY, "camera history", scenario + "/" + step);
            near(player.yOld, expectedOldY, "render history", scenario + "/" + step);
            if (step == 0 || step == 1 || step == 9 || step == 25) System.out.println("INFINITE_CAMERA_MOVE scenario="
                    + scenario + " step=" + step + " y=" + player.getY() + " box=" + player.getBoundingBox().minY
                    + " yo=" + player.yo + " yOld=" + player.yOld + " expectedOld=" + expectedOldY);
            if (step == 9) shot(client, String.format("%02d-view-%d-%s", scenario + 1, scenario / 2, sign < 0 ? "down" : "up"));
            step++;
        } catch (Throwable error) {
            System.out.println("INFINITE_CAMERA_FAILED"); error.printStackTrace();
            done = true; client.stop();
        }
    }

    public static void camera(Camera camera, Entity entity, float partial) {
        Minecraft client = Minecraft.getInstance();
        if (done || !sample || entity != client.player) return;
        try {
            // Native third-person displacement is horizontal at zero pitch; eye-height easing is retained.
            double eye = field(camera, "eyeHeightOld").getFloat(camera) * (1 - partial)
                    + field(camera, "eyeHeight").getFloat(camera) * partial;
            double expected = expectedOldY + partial * (entity.getY() - expectedOldY) + eye;
            double error = Math.abs(camera.getPosition().y - expected);
            maxCameraError = Math.max(maxCameraError, error);
            near(camera.getPosition().y, expected, "rendered camera", scenario + "/" + step + " partial=" + partial);
            frames++; caseFrames++;
            if (partial > 0.05 && partial < 0.95) { partialFrames++; casePartialFrames++; }
        } catch (Throwable error) { serverFailure = error; }
    }

    private static void finish(Minecraft client) {
        check(poseCount == CASES, "movement was not replaced with repeated teleports", "poses=" + poseCount);
        System.out.println("INFINITE_CAMERA_METRICS checks=" + checks + " frames=" + frames
                + " partialFrames=" + partialFrames + " maxError=" + maxCameraError + " failures=" + failures);
        System.out.println(failures.isEmpty() ? "INFINITE_CAMERA_OK" : "INFINITE_CAMERA_FAILED");
        done = true; client.stop();
    }
    private static void near(double actual, double expected, String key, String context) {
        check(Math.abs(actual - expected) < 0.0001, key, context + " actual=" + actual + " expected=" + expected);
    }
    private static void check(boolean passed, String key, String detail) {
        checks++;
        if (!passed && failures.putIfAbsent(key, detail) == null) System.out.println("INFINITE_CAMERA_MISMATCH " + key + " " + detail);
    }
    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void shot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.getWindow().getWidth(), client.getWindow().getHeight(),
                client.getMainRenderTarget(), message -> System.out.println("INFINITE_CAMERA_SCREENSHOT " + name));
    }
}
