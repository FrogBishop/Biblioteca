package biblioteca.infiniteprobe;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.client.CloudLayers;
import babel.biblioteca.client.CreateWorldScreenInjector;
import babel.biblioteca.client.LoopingClientSettings;
import babel.biblioteca.world.LoopingDimensionManager;
import java.lang.reflect.Field;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.server.ServerStartCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Real client screens, server-to-client state synchronization, seam drawing and surviving particles. */
public final class InfiniteSceneProbe implements ClientModInitializer {
    private static final boolean NO_RENDER = Boolean.getBoolean("biblioteca.infinite.norender");
    private static final Map<Particle, Sample> particles = new IdentityHashMap<>();
    private static final Set<Integer> terrainOffsets = new HashSet<>();
    private static final Set<Long> entityRows = new HashSet<>();
    private static final Set<Object> cloudBuffers = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int terrainDraws, cloudDraws, maxCloudCopies, sharedCloudFrames, cloudFrameDraws;
    private static Object cloudFrameBuffer;
    private static volatile Throwable serverFailure;
    private static volatile net.minecraft.world.entity.monster.Ghast sceneGhast;
    private static int ticks, worldTicks;
    private static boolean done, opened, ready;
    private static float wantedPitch;
    private static CreateWorldScreen createScreen;

    private static final class Sample {
        final String name;
        int renders, previousAge = -1, age = -1;
        double cameraY;
        Sample(String name) { this.name = name; }
    }

    @Override public void onInitializeClient() {
        ServerStartCallback.EVENT.register(server -> {
            try { prepare(server); } catch (Throwable error) { serverFailure = error; }
        });
    }

    private static void prepare(MinecraftServer server) {
        var level = Compat.overworld(server);
        level.getChunk(0, 0);
        for (int x = 5; x <= 11; x++) for (int z = 5; z <= 11; z++)
            for (int y = 0; y < 256; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++) if (x == 4 || x == 12 || z == 4 || z == 12) {
            level.setBlock(new BlockPos(x, 255, z), Blocks.GLOWSTONE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, 0, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        }
        level.getChunk(0, -4);
        var ghast = new net.minecraft.world.entity.monster.Ghast(net.minecraft.world.entity.EntityType.GHAST, level);
        ghast.setNoAi(true); ghast.setNoGravity(true); ghast.setInvulnerable(true); ghast.setSilent(true); ghast.setPersistenceRequired();
        ghast.setPos(8.5, 128, -53.5); sceneGhast = ghast;
        if (!level.addFreshEntity(ghast)) throw new AssertionError("scene entity rejected");
        level.setDayTime(6000);
        LoopingDimensionManager.setSkyLightMode(Compat.dimension(level), SkyLightMode.HYBRID);
        LoopingDimensionManager.setLooping(Compat.dimension(level), true);
        LoopingDimensionManager.setNoVoidFog(Compat.dimension(level), true);
        System.out.println("INFINITE_SCENE_PREPARED");
    }

    public static void tick(Minecraft client) {
        if (done) return;
        try {
            if (!ready) {
                if (client.getOverlay() != null || !(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
                ready = true;
            }
            ticks++;
            if (serverFailure != null) throw new AssertionError("server scene setup", serverFailure);
            if (ticks == 10) { createScreen = new CreateWorldScreen(null); client.setScreen(createScreen); }
            if (ticks == 25) {
                String caption = I18n.get("biblioteca.gui.looping_button");
                check(!caption.equals("biblioteca.gui.looping_button"), "mod translations loaded");
                check(messages(client.screen).contains(caption), "world attribute entry present");
                shot(client, "01-create-world");
            }
            if (ticks == 30) CreateWorldScreenInjector.openConfig(createScreen);
            if (ticks == 45) { check(messages(client.screen).size() >= 4, "dimension settings populated"); shot(client, "02-dimensions"); }
            if (ticks == 50) client.setScreen(new VideoSettingsScreen(null, client.options));
            if (ticks == 55) {
                Object list = field(client.screen, "list").get(client.screen);
                list.getClass().getMethod("setScrollAmount", double.class).invoke(list, 10000.0);
            }
            if (ticks == 65) {
                List<String> messages = messages(client.screen);
                int found = 0;
                for (String key : List.of("preload", "entity_mirrors", "cloud_layers")) {
                    String caption = I18n.get("biblioteca.options." + key);
                    if (messages.stream().anyMatch(message -> message.replaceAll("\u00a7.", "").startsWith(caption))) found++;
                }
                System.out.println("INFINITE_SCENE_OPTIONS " + messages);
                check(found == (NO_RENDER ? 0 : 3), "video settings count=" + found + " noRender=" + NO_RENDER);
                shot(client, "03-video-settings");
            }
            if (ticks == 70) { opened = true; client.selectLevel("infinite-world", "Biblioteca Infinity validation", null); }
            if (ticks > 1800) throw new AssertionError("scene timed out");
            if (!opened || client.level == null || client.player == null) return;
            worldTicks++;
            if (worldTicks >= 10) {
                client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                client.player.yRot = client.player.yRotO = 180;
                client.player.xRot = client.player.xRotO = wantedPitch;
                for (var key : java.util.List.of(client.options.keyUp, client.options.keyDown, client.options.keyLeft,
                        client.options.keyRight, client.options.keyJump, client.options.keyShift)) key.setDown(false);
            }
            if (worldTicks == 10) {
                check(LoopingRegistry.isLooping(client.level), "join packet synchronized looping state");
                LoopingClientSettings.setMultipleCloudLayers(true);
                LoopingClientSettings.setEntityMirrors(true);
                pose(client, 0.4, 15);
            }
            if (worldTicks == 240) {
                track(client.particleEngine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, 8.5, 1.4, 7.5, 0, 0.002, 0), "campfire");
                track(client.particleEngine.createParticle(ParticleTypes.PORTAL, 8.8, 1.4, 7.5, 0, 0, 0), "portal");
            }
            if (worldTicks == 310) {
                if (!NO_RENDER) check(terrainOffsets.stream().anyMatch(value -> value != 0), "native terrain draws an adjacent periodic layer");
                sample(client, "04-bottom", false); resetDraws();
            }
            if (worldTicks == 320) pose(client, -0.4, 0);
            if (worldTicks == 385) {
                check(client.player.getY() > 250, "downward crossing canonical position");
                sample(client, "05-downward-crossing", true); resetDraws();
            }
            if (worldTicks == 395) pose(client, 256.4, 0);
            if (worldTicks == 460) {
                check(client.player.getY() < 5, "upward crossing canonical position");
                sample(client, "06-upward-crossing", true); resetDraws();
            }
            if (worldTicks == 470) { client.options.fov = 110; pose(client, 127.38, -38); }
            if (worldTicks == 520) System.out.println("INFINITE_SCENE_ENTITY serverAlive=" + sceneGhast.isAlive()
                    + " serverPos=" + sceneGhast.position() + " clientCount=" + client.level.getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Ghast.class, new net.minecraft.world.phys.AABB(-256, 0, -256, 256, 256, 256)).size());
            if (worldTicks == 540) {
                if (!NO_RENDER) {
                    check(cloudDraws > 0 && maxCloudCopies >= 2, "multiple native cloud layers count=" + maxCloudCopies);
                    check(sharedCloudFrames > 0, "cloud copies share their native buffer within each draw pass");
                    check(entityRows.size() >= 2 && Collections.max(entityRows) - Collections.min(entityRows) == 256,
                            "native entity copies have one-period separation " + entityRows);
                }
                shot(client, "07-cloud-layers");
                client.getSingleplayerServer().execute(() -> {
                    var server = client.getSingleplayerServer();
                    LoopingDimensionManager.setLooping(Compat.dimension(Compat.overworld(server)), false);
                    Biblioteca.broadcastState(server);
                });
            }
            if (worldTicks == 555) resetDraws();
            if (worldTicks == 595) {
                check(!LoopingRegistry.isLooping(client.level), "updated packet restores ordinary dimension");
                check(terrainDraws == 0, "ordinary world stops periodic terrain drawing");
                shot(client, "08-ordinary-restored");
            }
            if (worldTicks == 610) {
                System.out.println("INFINITE_SCENE_OK noRender=" + NO_RENDER);
                done = true; client.stop();
            }
        } catch (Throwable error) {
            System.out.println("INFINITE_SCENE_FAILED"); error.printStackTrace();
            if (ready) shot(client, "failure-" + ticks);
            done = true; client.stop();
        }
    }

    private static void pose(Minecraft client, double y, float pitch) {
        client.options.hideGui = true;
        wantedPitch = pitch;
        client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        client.getSingleplayerServer().execute(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
            player.setGameMode(GameType.SPECTATOR);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.connection.teleport(8.5, SeamPositions.foldY(Compat.level(player), y), 10.5, 180, pitch);
        });
    }
    private static void track(Particle particle, String name) throws Exception {
        if (particle == null) throw new AssertionError("missing particle " + name);
        field(particle, "lifetime").setInt(particle, 1800);
        particles.put(particle, new Sample(name));
    }
    public static void renderParticle(Particle particle, com.mojang.blaze3d.vertex.VertexConsumer writer,
            net.minecraft.client.Camera camera, float partial,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        Sample sample = particles.get(particle);
        if (sample == null) { original.call(writer, camera, partial); return; }
        try {
            double beforeY = field(particle, "y").getDouble(particle);
            double beforeYo = field(particle, "yo").getDouble(particle);
            int beforeAge = field(particle, "age").getInt(particle);
            double[] vertices = new double[2];
            var capture = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    writer.getClass().getClassLoader(), new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("vertex") && args.length == 3) {
                            vertices[0] += ((Number)args[1]).doubleValue(); vertices[1]++;
                        }
                        try { Object result = method.invoke(writer, args); return result == writer ? proxy : result; }
                        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                    });
            original.call(capture, camera, partial);
            if (field(particle, "y").getDouble(particle) != beforeY || field(particle, "yo").getDouble(particle) != beforeYo
                    || field(particle, "age").getInt(particle) != beforeAge) throw new AssertionError("render changed particle simulation");
            if (vertices[1] != 4) throw new AssertionError("particle vertex count " + vertices[1]);
            double cameraY = camera.getPosition().y;
            double y = beforeYo + partial * (beforeY - beforeYo);
            boolean periodic = !NO_RENDER && LoopingRegistry.isLooping(Minecraft.getInstance().level);
            double expected = (periodic ? SeamPositions.foldToward(y, cameraY, 256) : y) - cameraY;
            if (Math.abs(vertices[0] / 4 - expected) > 0.001) throw new AssertionError("particle drawn in wrong layer: " + vertices[0] / 4 + " expected " + expected);
            sample.age = beforeAge; sample.renders++; sample.cameraY = cameraY;
        } catch (Throwable error) { throw new AssertionError(error); }
    }
    public static void entity(net.minecraft.world.entity.Entity entity, double renderedY) {
        if (worldTicks >= 510 && worldTicks < 540 && entity instanceof net.minecraft.world.entity.monster.Ghast)
            entityRows.add(Math.round(renderedY));
    }
    public static void terrain(int offset) { terrainDraws++; terrainOffsets.add(offset); }
    public static void beginCloudFrame() { cloudFrameBuffer = null; cloudFrameDraws = 0; }
    public static void cloud(Object buffer) {
        if (NO_RENDER) return;
        try {
            CloudLayers layers = (CloudLayers) field(Minecraft.getInstance().levelRenderer, "biblioteca$layers").get(Minecraft.getInstance().levelRenderer);
            if (cloudFrameBuffer != null && cloudFrameBuffer != buffer) throw new AssertionError("different cloud buffers in one native draw pass");
            cloudFrameBuffer = buffer;
            if (++cloudFrameDraws == 2 && layers.count() > 1) sharedCloudFrames++;
            cloudDraws++; cloudBuffers.add(buffer); maxCloudCopies = Math.max(maxCloudCopies, layers.count());
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static void sample(Minecraft client, String name, boolean crossed) {
        if (!NO_RENDER) for (Sample sample : particles.values()) {
            check(sample.renders > 0 && sample.age > sample.previousAge, "same " + sample.name + " particle survives " + name + " age=" + sample.age);
            if (crossed) check(Math.abs(sample.cameraY - client.gameRenderer.getMainCamera().getPosition().y) < 4, "particle rendered in current camera layer");
            sample.previousAge = sample.age; sample.renders = 0;
        }
        System.out.println("INFINITE_SCENE_SAMPLE " + name + " player=" + client.player.position()
                + " camera=" + client.gameRenderer.getMainCamera().getPosition() + " offsets=" + terrainOffsets + " draws=" + terrainDraws);
        shot(client, name);
    }
    private static void resetDraws() { terrainDraws = 0; terrainOffsets.clear(); cloudDraws = 0; cloudBuffers.clear(); maxCloudCopies = 0; sharedCloudFrames = 0; }
    private static void shot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.getWindow().getWidth(), client.getWindow().getHeight(),
                client.getMainRenderTarget(), message -> System.out.println("INFINITE_SCENE_SCREENSHOT " + name));
    }
    private static Field field(Object object, String name) throws NoSuchFieldException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static List<String> messages(Screen screen) throws IllegalAccessException {
        List<String> result = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Object> queue = new ArrayDeque<>(); queue.add(screen);
        while (!queue.isEmpty()) {
            Object object = queue.remove();
            if (!visited.add(object)) continue;
            if (object instanceof AbstractWidget widget) result.add(widget.getMessage());
            for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass())
                for (Field field : type.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    if (!Collection.class.isAssignableFrom(field.getType()) && !Map.class.isAssignableFrom(field.getType()) && !AbstractWidget.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true); Object value = field.get(object);
                    Collection<?> children = value instanceof Map<?, ?> map ? map.values() : value instanceof Collection<?> collection ? collection : null;
                    if (children != null) {
                        for (Object child : children) if (child instanceof AbstractWidget
                                || child != null && child.getClass().getName().startsWith("net.minecraft.client.gui")) queue.add(child);
                    } else if (value != null) queue.add(value);
                }
        }
        return result;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("INFINITE_SCENE_CASE_OK " + message);
    }
}
