package biblioteca.periodicprobe;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.world.LoopingDimensionManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.IdentityHashMap;
import java.util.Map;

/** Actual integrated-server sensors and persistent native particles, using only an isolated world copy. */
public final class PeriodicEffectsChecks {
    private static final Map<Particle, Sample> particles = new IdentityHashMap<>();
    private static volatile boolean joined, sensorsDone;
    private static volatile Throwable failure;
    private static int serverTicks, sensorCase, phase, ticks, clientTicks, phaseStartTick;
    private static BlockPos sensor;
    private static boolean finished;
    private static final class Sample {
        final String type;
        int lastSeen, previousAge = -1;
        float y;
        double cameraY;
        Sample(String type) { this.type = type; }
    }
    private PeriodicEffectsChecks() {}

    public static void rendered(Particle particle, float y) {
        Sample sample = particles.get(particle);
        if (sample != null) {
            sample.y = y; sample.lastSeen = clientTicks;
            sample.cameraY = babel.biblioteca.client.ClientCompat.cameraPosition().y;
        }
    }

    public static void serverTick(MinecraftServer server) {
        if (!joined || sensorsDone || failure != null) return;
        try {
            ServerLevel level = server.overworld();
            int min = Compat.minY(level), max = Compat.maxY(level);
            if (++serverTicks == 1) {
                LoopingDimensionManager.setLooping(Level.OVERWORLD, sensorCase != 3);
                command(server, "gamemode spectator @a");
                command(server, "time set noon");
                command(server, "weather clear");
                if (sensorCase == 0) {
                    command(server, "fill -6 " + min + " -6 12 " + (min + 4) + " 12 air");
                    command(server, "fill -6 " + (max - 4) + " -6 12 " + max + " 12 air");
                    command(server, "tp @a 0.5 " + (max - 0.5) + " 0.5 0 0");
                    level.setBlockAndUpdate(new BlockPos(-2, max - 1, 4), Blocks.CAMPFIRE.defaultBlockState());
                    level.setBlock(new BlockPos(2, max - 1, 4), Blocks.NETHER_PORTAL.defaultBlockState(), 2);
                }
                if (sensor != null) level.setBlockAndUpdate(sensor, Blocks.AIR.defaultBlockState());
                sensor = new BlockPos(8, sensorCase == 1 ? max - 1 : min + 1, 8);
                level.setBlockAndUpdate(new BlockPos(8, min, 8), sensorCase == 2 ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Compat.id("minecraft", "white_wool")).defaultBlockState() : Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(8, max, 8), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(sensor, Blocks.SCULK_SENSOR.defaultBlockState());
            }
            if (serverTicks == 20) {
                BlockPos source = new BlockPos(8, sensorCase == 1 ? min + 1 : max - 1, 8);
                level.gameEvent(GameEvent.STEP, Vec3.atCenterOf(source), GameEvent.Context.of((net.minecraft.world.entity.Entity) null));
            }
            if (serverTicks == 30) {
                int power = level.getBlockState(sensor).getValue(SculkSensorBlock.POWER);
                boolean expected = sensorCase < 2;
                check((power > 0) == expected, "sensor case " + sensorCase + " power=" + power + " expected active=" + expected);
                System.out.println("PERIODIC_SENSOR case=" + sensorCase + " power=" + power + " passed=true");
                serverTicks = 0;
                if (++sensorCase == 4) {
                    LoopingDimensionManager.setLooping(Level.OVERWORLD, true);
                    sensorsDone = true;
                }
            }
        } catch (Throwable error) { failure = error; }
    }

    public static void clientTick(Minecraft client) {
        if (finished || client.level == null || client.player == null) return;
        joined = true; clientTicks++;
        client.mouseHandler.releaseMouse(); KeyMapping.releaseAll();
        client.player.setYRot(0); client.player.setXRot(0); client.player.yRotO = 0; client.player.xRotO = 0;
        try {
            if (failure != null) throw new AssertionError("server fixture", failure);
            if (!sensorsDone || !LoopingRegistry.isLooping(client.level) || ++ticks < 30) return;
            int min = Compat.minY(client.level), max = Compat.maxY(client.level);
            switch (phase) {
                case 0 -> {
                    Particle smoke = client.particleEngine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            -0.5, max + 0.9, 4.5, 0, 0, 0);
                    Particle portal = client.particleEngine.createParticle(ParticleTypes.PORTAL,
                            1.5, max + 0.9, 4.5, 0, 0, 0);
                    track(smoke, "campfire"); track(portal, "portal"); next();
                }
                case 1 -> {
                    if (!sample(client, "before")) return;
                    frame(client, "particles-before");
                    command(client.getSingleplayerServer(), "tp @a 0.5 " + (max + 1.5) + " 0.5 0 0"); next();
                }
                case 2 -> {
                    check(Math.abs(client.player.getY() - (min + 0.5)) < 0.05, "top-to-bottom camera wrap");
                    if (!sample(client, "after-top")) return;
                    frame(client, "particles-after-top");
                    command(client.getSingleplayerServer(), "tp @a 0.5 " + (min - 1.5) + " 0.5 0 0"); next();
                }
                case 3 -> {
                    check(Math.abs(client.player.getY() - (max - 0.5)) < 0.05, "bottom-to-top camera wrap");
                    if (!sample(client, "after-bottom")) return;
                    frame(client, "particles-after-bottom"); next();
                }
                case 4 -> {
                    command(client.getSingleplayerServer(), "execute as @a at @s run biblioteca diagnostics");
                    command(client.getSingleplayerServer(), "execute as @a at @s run biblioteca diagnostics export");
                    client.debugEntries.setOverlayVisible(true);
                    client.options.debugGuiScale().set(1);
                    next();
                }
                case 5 -> {
                    var directory = client.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("biblioteca-diagnostics");
                    try (var files = Files.list(directory)) {
                        var comparison = files.filter(p -> p.getFileName().toString().endsWith("-client.json")).findFirst().orElseThrow();
                        var json = new com.google.gson.JsonParser().parse(Files.readString(comparison)).getAsJsonObject();
                        check(json.get("comparedSamples").getAsInt() > 0, "client/server diagnostic comparison");
                        System.out.println("PERIODIC_DIAGNOSTICS samples=" + json.get("comparedSamples") + " mismatches=" + json.get("lightMismatches"));
                    }
                    check(babel.biblioteca.client.ClientDiagnostics.append(java.util.List.of()).stream().anyMatch(s -> s.contains("FOV")), "debug text");
                    frame(client, "diagnostics-f3"); next();
                }
                case 6 -> {
                    for (String name : new String[]{"particles-before", "particles-after-top", "particles-after-bottom", "diagnostics-f3"}) {
                        check(Files.isRegularFile(client.gameDirectory.toPath().resolve("screenshots/" + name + ".png")), "missing screenshot " + name);
                    }
                    finished = true; System.out.println("PERIODIC_DONE failed=false"); client.stop();
                }
                default -> throw new AssertionError("unknown phase");
            }
        } catch (Throwable error) {
            finished = true; System.out.println("PERIODIC_FAILURE " + error); error.printStackTrace(); client.stop();
        }
    }

    private static void track(Particle particle, String type) {
        check(particle != null, "native particle provider " + type);
        particle.setLifetime(2000); particle.scale(3);
        particles.put(particle, new Sample(type));
    }
    private static boolean sample(Minecraft client, String phase) throws Exception {
        // Slow frames may run many game ticks before extraction. Require an
        // actual particle draw in the new camera frame, not one draw per tick.
        for (Sample sample : particles.values()) {
            if (sample.lastSeen <= phaseStartTick || Math.abs(sample.cameraY - client.player.getEyeY()) > 4) {
                check(ticks < 400, "no particle extraction in the new camera frame: " + sample.type);
                return false;
            }
        }
        for (var entry : particles.entrySet()) {
            Particle particle = entry.getKey(); Sample sample = entry.getValue();
            Field age = Particle.class.getDeclaredField("age"); age.setAccessible(true); int value = age.getInt(particle);
            check(particle.isAlive() && value > sample.previousAge, "particle identity/lifetime " + sample.type);
            check(Math.abs(sample.y) < 16, "particle disappeared at seam: " + sample.type + " y=" + sample.y);
            sample.previousAge = value;
            System.out.println("PERIODIC_PARTICLE phase=" + phase + " type=" + sample.type + " age=" + value + " y=" + sample.y + " sameInstance=true");
        }
        return true;
    }
    private static void next() { phase++; ticks = 0; phaseStartTick = clientTicks; }
    private static void command(MinecraftServer server, String text) {
        if (!server.isSameThread()) { server.execute(() -> command(server, text)); return; }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), text);
    }
    private static void frame(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("PERIODIC_FRAME " + name));
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
