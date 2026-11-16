package biblioteca.runtimeprobe;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.client.CreateWorldScreenInjector;
import babel.biblioteca.world.LoopingDimensionManager;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real screens, integrated-server transport and physical camera crossings in a copied world. */
@net.neoforged.fml.common.Mod("biblioteca_runtime_probe")
public final class RuntimeProbe {
    private static final boolean NO_RENDER = Boolean.getBoolean("biblioteca.runtime.norender");
    private static volatile boolean prepared;
    private static volatile Throwable failure;
    private static boolean ready, done, sample, requested;
    private static int ticks, phase, wait, worldTicks, step, frames, fractional, checks;
    private static double expectedOldY, maxError;
    private static Screen title;
    private static Runnable pendingOpen;
    public static void deferOpen(Runnable proceed) { pendingOpen = proceed; }
    public static boolean isWorld(Object access) {
        try { return "runtime-world".equals(call(access, "getLevelId")); }
        catch (Exception error) { throw new AssertionError(error); }
    }

    public RuntimeProbe() {}

    public static void server(MinecraftServer server) {
        if (prepared || failure != null || server.getPlayerList().getPlayers().isEmpty()) return;
        try {
            var level = Compat.overworld(server); int min = Compat.minY(level), top = min + Compat.height(level);
            level.getChunk(0, 0);
            for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++)
                for (int y = min; y < top; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++) if (x == 4 || x == 12 || z == 4 || z == 12) {
                level.setBlock(new BlockPos(x, min, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, top - 1, z), Blocks.GLOWSTONE.defaultBlockState(), 2);
            }
            LoopingDimensionManager.setLooping(Compat.dimension(level), true);
            LoopingDimensionManager.setNoVoidFog(Compat.dimension(level), true);
            LoopingDimensionManager.setSkyLightMode(Compat.dimension(level), SkyLightMode.HYBRID);
            level.setDayTime(6000); Biblioteca.broadcastState(server);
            command(server, "gamemode spectator @a"); pose(server, min + 0.25);
            prepared = true;
            System.out.println("NEO_RUNTIME_PREPARED height=" + Compat.height(level));
        } catch (Throwable error) { failure = error; }
    }

    public static void tick(Minecraft client) {
        if (done) return;
        try {
            if (pendingOpen != null) { Runnable next = pendingOpen; pendingOpen = null; next.run(); }
            if (!ready) {
                if (client.getOverlay() != null || !(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
                ready = true; title = client.screen;
            }
            if (++ticks > 2400) throw new AssertionError("runtime probe timed out at phase " + phase);
            if (failure != null) throw new AssertionError("server/render checks", failure);
            if (phase < 5) { ui(client); return; }
            if (!prepared || client.level == null || client.player == null || client.screen != null) return;
            client.options.hideGui = true;
            client.player.setDeltaMovement(Vec3.ZERO);
            client.player.setYRot(0); client.player.yRotO = 0; client.player.setXRot(0); client.player.xRotO = 0;
            for (var key : List.of(client.options.keyUp, client.options.keyDown, client.options.keyLeft,
                    client.options.keyRight, client.options.keyJump, client.options.keyShift)) key.setDown(false);
            int min = Compat.minY(client.level), period = Compat.height(client.level);
            if (phase == 5) {
                if (++worldTicks < 160) return;
                check(LoopingRegistry.isLooping(client.level), "actual server update synchronized to the joined client");
                near(client.player.getY(), min + 0.25, "initial physical pose");
                shot(client, "03-bottom-ready"); phase = 6; step = 0;
            }
            if (phase == 6 || phase == 8) {
                int direction = phase == 6 ? -1 : 1;
                double dy = step == 0 ? direction * 0.75 : step < 16 ? 0 : direction * 0.1;
                double before = client.player.getY();
                client.player.move(MoverType.SELF, new Vec3(0.025, dy, 0));
                client.player.move(MoverType.SELF, new Vec3(0, 0, 0.015));
                double expected = before + dy - Math.floor((before + dy - min) / period) * period;
                expectedOldY = expected - dy; sample = true;
                near(client.player.getY(), expected, "physical position");
                near(client.player.getBoundingBox().minY, expected, "physical collision box");
                near(client.player.yo, expectedOldY, "physical camera history");
                if (++step == 30) {
                    shot(client, phase == 6 ? "04-down-and-moving" : "05-up-and-moving");
                    check(frames > 0 && fractional > 0, "native fractional camera frames observed");
                    sample = false; wait = 0; requested = false; phase++;
                }
                return;
            }
            if (phase == 7) {
                if (!requested) { requested = true; client.getSingleplayerServer().execute(() -> pose(client.getSingleplayerServer(), min + period - 0.25)); }
                if (Math.abs(client.player.getY() - (min + period - 0.25)) > 0.01) return;
                if (++wait >= 20) { phase = 8; step = 0; }
                return;
            }
            if (phase == 9) {
                if (!requested) {
                    requested = true;
                    client.getSingleplayerServer().execute(() -> {
                        var server = client.getSingleplayerServer();
                        LoopingDimensionManager.setLooping(Compat.dimension(Compat.overworld(server)), false);
                        Biblioteca.broadcastState(server); pose(server, min + 0.25);
                    });
                }
                if (LoopingRegistry.isLooping(client.level) || Math.abs(client.player.getY() - (min + 0.25)) > 0.01) return;
                if (++wait < 20) return;
                client.player.move(MoverType.SELF, new Vec3(0, -0.75, 0));
                near(client.player.getY(), min - 0.5, "disabling looping restores native unwrapped movement");
                shot(client, "06-ordinary-restored");
                phase = 10; wait = 0;
            }
            if (phase == 10 && ++wait >= 20 && new java.io.File(client.gameDirectory, "screenshots/06-ordinary-restored.png").isFile()) {
                System.out.println("NEO_RUNTIME_OK checks=" + checks + " frames=" + frames + " fractional=" + fractional + " maxCameraError=" + maxError);
                done = true; client.stop();
            }
        } catch (Throwable error) {
            System.out.println("NEO_RUNTIME_FAILED phase=" + phase); error.printStackTrace();
            done = true; client.stop();
        }
    }

    private static void ui(Minecraft client) throws Exception {
        if (phase == 0) {
            if (ticks < 10) return;
            Class<?> screen = Class.forName("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen");
            screen.getMethod("openFresh", Minecraft.class, Screen.class).invoke(null, client, title);
            phase = 1; wait = 0; return;
        }
        if (phase == 1) {
            if (client.screen == null || !client.screen.getClass().getSimpleName().equals("CreateWorldScreen")) return;
            if (wait == 0) call(field(client.screen, "tabNavigationBar").get(client.screen), "selectTab", 2, false);
            if (++wait < 15) return;
            check(messages(client.screen).stream().anyMatch(s -> s.contains(I18n.get("biblioteca.gui.looping_button"))), "world creation offers dimension attributes");
            CreateWorldScreenInjector.openConfig((net.minecraft.client.gui.screens.worldselection.CreateWorldScreen) client.screen);
            phase = 2; wait = 0; return;
        }
        if (++wait < 15) return;
        wait = 0;
        if (phase == 2) {
            check(client.screen != null && client.screen.getClass().getSimpleName().contains("DimensionConfigScreen"), "dimension editor opens on the actual client");
            shot(client, "01-dimension-settings");
            Class<?> video;
            try { video = Class.forName("net.minecraft.client.gui.screens.options.VideoSettingsScreen"); }
            catch (ClassNotFoundException older) { video = Class.forName("net.minecraft.client.gui.screens.VideoSettingsScreen"); }
            Constructor<?> constructor = video.getConstructors()[0];
            client.setScreen((Screen) (constructor.getParameterCount() == 3
                    ? constructor.newInstance(title, client, client.options) : constructor.newInstance(title, client.options)));
            try { Object list = field(client.screen, "list").get(client.screen); call(list, "setScrollAmount", 10000.0); } catch (NoSuchFieldException ignored) {}
            phase = 3; return;
        }
        if (phase == 3) {
            var messages = messages(client.screen); int found = 0;
            for (String key : List.of("preload", "entity_mirrors", "cloud_layers")) {
                String caption = I18n.get("biblioteca.options." + key);
                if (messages.stream().anyMatch(text -> text.startsWith(caption))) found++;
            }
            check(found == (NO_RENDER ? 0 : 3), "video controls=" + found + " noRender=" + NO_RENDER);
            shot(client, "02-video-options"); client.setScreen(title); phase = 4; return;
        }
        if (phase == 4) {
            Object flows = call(client, "createWorldOpenFlows");
            Method load = Arrays.stream(flows.getClass().getMethods()).filter(m -> Set.of("loadLevel", "openWorld", "checkForBackupAndLoad").contains(m.getName()) && m.getParameterCount() == 2).findFirst().orElseThrow();
            if (load.getParameterTypes()[0] == String.class) load.invoke(flows, "runtime-world", (Runnable) () -> client.setScreen(title));
            else load.invoke(flows, title, "runtime-world");
            phase = 5;
        }
    }

    public static void camera(Camera camera, Entity entity, float partial) {
        if (done || !sample || entity != Minecraft.getInstance().player) return;
        try {
            double eye = field(camera, "eyeHeightOld").getFloat(camera) * (1 - partial) + field(camera, "eyeHeight").getFloat(camera) * partial;
            double expected = expectedOldY + partial * (entity.getY() - expectedOldY) + eye;
            double error = Math.abs(camera.getPosition().y - expected); maxError = Math.max(maxError, error);
            near(camera.getPosition().y, expected, "native camera interpolation");
            frames++; if (partial > 0.05 && partial < 0.95) fractional++;
        } catch (Throwable error) { failure = error; }
    }
    private static void pose(MinecraftServer server, double y) { command(server, "tp @a 8.5 " + y + " 8.5 0 0"); }
    private static void command(MinecraftServer server, String command) { server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); System.out.println("NEO_RUNTIME_CHECK " + message); }
    private static void near(double value, double expected, String message) { checks++; if (Math.abs(value - expected) > 0.001) throw new AssertionError(message + ": " + value + " != " + expected); }
    private static Field field(Object value, String name) throws NoSuchFieldException {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object value, String name, Object... args) throws Exception {
        Method method = Arrays.stream(value.getClass().getMethods()).filter(m -> m.getName().equals(name) && m.getParameterCount() == args.length).findFirst().orElseThrow();
        method.setAccessible(true); return method.invoke(value, args);
    }
    private static Set<String> messages(Object screen) throws Exception {
        Set<String> messages = new HashSet<>(); Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Object>(); pending.add(screen);
        while (!pending.isEmpty()) {
            Object object = pending.remove(); if (!visited.add(object)) continue;
            for (Method method : object.getClass().getMethods()) {
                if (method.getParameterCount() != 0) continue;
                if (method.getName().equals("getMessage")) { Object message = method.invoke(object); if (message != null) messages.add((String) call(message, "getString")); }
                if (method.getName().equals("children")) { Object children = method.invoke(object); if (children instanceof Iterable<?> list) for (Object child : list) if (child != null) pending.add(child); }
            }
        }
        return messages;
    }
    private static void shot(Minecraft client, String name) throws Exception {
        java.util.function.Consumer<Object> result = value -> System.out.println("NEO_RUNTIME_SCREENSHOT " + name);
        Method method = Arrays.stream(Screenshot.class.getMethods()).filter(m -> m.getName().equals("grab") && (m.getParameterCount() == 4 || m.getParameterCount() == 5 || m.getParameterCount() == 6)).findFirst().orElseThrow();
        if (method.getParameterCount() == 4) method.invoke(null, client.gameDirectory, name + ".png", client.getMainRenderTarget(), result);
        else if (method.getParameterCount() == 5) method.invoke(null, client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1, result);
        else method.invoke(null, client.gameDirectory, name + ".png", client.getWindow().getWidth(), client.getWindow().getHeight(), client.getMainRenderTarget(), result);
    }
}
