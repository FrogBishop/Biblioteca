package biblioteca.sodiumscene;

import babel.biblioteca.Compat;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.sodiumcompat.SodiumBridge;
import babel.biblioteca.client.sodiumcompat.SodiumClouds;
import java.lang.reflect.*;
import java.io.File;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/** Real native Sodium draws, boundary snapshots and unchanged particles in disposable worlds. */
public final class SceneProbe implements ClientModInitializer {
    public static final boolean SHORT = Boolean.getBoolean("biblioteca.scene.short");
    private static final String WORLD = SHORT ? "biblioteca:sodium_scene" : "minecraft:overworld";
    private static final Set<Integer> offsets = new HashSet<>();
    private static final Set<Float> cloudHeights = new HashSet<>();
    private static final Set<Object> cloudBuffers = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Particle, ParticleSample> particles = new IdentityHashMap<>();
    private static volatile boolean setup, done;
    private static volatile Throwable failure;
    private static int ticks, phase, draws, cloudDraws, openTicks, transitionTicks;
    private static int observedPhase = -1;
    private static boolean opened;
    private static Runnable pendingOpen;
    public static void deferOpen(Runnable proceed) { pendingOpen = proceed; }

    private static final class ParticleSample {
        String name;
        int renderedAge = -1, previousAge = -1;
        double y, cameraY;
        boolean optimized;
        ParticleSample(String name) { this.name = name; }
    }

    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            System.out.println("SCENE_SCREEN " + screen.getClass().getName() + " " + screen.getTitle().getString());
        });
        ClientTickEvents.END_CLIENT_TICK.register(SceneProbe::tick);
        ServerTickEvents.END_SERVER_TICK.register(SceneProbe::server);
    }
    public static boolean tracked(Particle particle) { return particles.containsKey(particle); }
    public static void particle(Particle p, double camera, double y, int age, boolean optimized) {
        ParticleSample sample = particles.get(p);
        sample.y = y; sample.cameraY = camera; sample.renderedAge = age;
        sample.optimized = optimized;
    }
    public static void drawn() {
        Level level = Minecraft.getInstance().level;
        if (level == null || !dimensionId(level).equals(WORLD)) return;
        draws++; offsets.add(SodiumBridge.imageOffset());
    }
    public static void cloud(Object renderer) {
        if (SodiumClouds.height() == null) return;
        cloudDraws++; cloudHeights.add(SodiumClouds.height());
        Object geometry = optionalField(renderer, "builtGeometry");
        Object buffer = geometry != null ? invoke(geometry, "vertexBuffer") : optionalField(renderer, "vertexBuffer");
        if (buffer != null) cloudBuffers.add(buffer);
    }
    private static void server(MinecraftServer server) {
        if (setup || failure != null || server.getPlayerList().getPlayers().isEmpty()) return;
        try {
            Level world = null;
            for (Object candidate : (Iterable<?>)invoke(server, "getAllLevels")) {
                if (dimensionId((Level)candidate).equals(WORLD)) { world = (Level)candidate; break; }
            }
            check(world != null, "test dimension missing " + WORLD);
            world.getChunk(-1, 1); world.getChunk(0, 1);
            command(server, "biblioteca looping set " + WORLD + " false");
            command(server, "execute in " + WORLD + " run biblioteca no_void_fog true");
            command(server, "gamemode creative @a"); command(server, "time set noon"); command(server, "weather clear");
            int min = Compat.minY(world), top = min + Compat.height(world);
            command(server, "execute in " + WORLD + " run fill -3 " + min + " 20 3 " + min + " 26 stone");
            command(server, "execute in " + WORLD + " run fill -3 " + (top - 1) + " 20 3 " + (top - 1) + " 26 glass");
            command(server, "execute in " + WORLD + " run setblock 2 " + (min + 1) + " 23 chest");
            command(server, "execute in " + WORLD + " run setblock -2 " + (top - 1) + " 23 glowstone");
            command(server, "execute in " + WORLD + " run tp @a 0.5 " + (SHORT ? 6 : top - 2) + " 0.5 0 0");
            setup = true;
        } catch (Throwable error) { failure = error; }
    }
    private static void tick(Minecraft mc) {
        if (done) return;
        try {
            if (pendingOpen != null) { Runnable proceed = pendingOpen; pendingOpen = null; proceed.run(); }
            if (!opened && ++openTicks == 10 && mc.level == null) {
                opened = true;
                if (has(mc, "loadLevel", 1)) invoke(mc, "loadLevel", "sodium-world");
                else if (System.getProperty("biblioteca.scene.open", "quick").equals("legacy"))
                    invoke(invoke(mc, "createWorldOpenFlows"), "loadLevel", optionalField(mc, "screen"), "sodium-world");
            }
            if (failure != null) throw new AssertionError("scene setup", failure);
            if (!setup || mc.level == null || !dimensionId(mc.level).equals(WORLD)) return;
            mc.mouseHandler.releaseMouse();
            Object abilities = has(mc.player, "getAbilities", 0) ? invoke(mc.player, "getAbilities") : field(mc.player, "abilities");
            set(abilities, "flying", true);
            mc.player.setDeltaMovement(0, 0, 0); mc.player.setYRot(0); mc.player.setXRot(0);
            mc.player.yRotO = 0; mc.player.xRotO = 0;
            boolean expectedLoop = phase >= 1 && phase <= 8;
            if (babel.biblioteca.LoopingRegistry.isLooping(mc.level) != expectedLoop) {
                if (++transitionTicks > 400) throw new AssertionError("looping state did not synchronize");
                ticks = 0; draws = cloudDraws = 0; offsets.clear(); cloudHeights.clear(); cloudBuffers.clear(); return;
            }
            transitionTicks = 0;
            if (observedPhase != phase) {
                observedPhase = phase;
                draws = cloudDraws = 0; offsets.clear(); cloudHeights.clear(); cloudBuffers.clear();
            }
            if (++ticks < 80) return;
            int min = Compat.minY(mc.level), top = min + Compat.height(mc.level);
            switch (phase) {
                case 0 -> { check(draws > 0 && offsets.equals(Set.of(0)), "ordinary native draws=" + draws + " offsets=" + offsets); capture(mc, "ordinary"); command(mc.getSingleplayerServer(), "biblioteca looping set " + WORLD + " true"); next(); }
                case 1 -> {
                    check(draws > 0 && offsets.stream().anyMatch(y -> y != 0), "missing native shifted terrain draws " + offsets);
                    if (SHORT) check(offsets.size() > 3, "too few native terrain copies " + offsets);
                    snapshots(mc);
                    if (SHORT && Boolean.getBoolean("biblioteca.scene.sodiumClouds")) {
                        check(cloudDraws > 3 && cloudHeights.size() > 3, "Sodium cloud layers missing " + cloudHeights);
                        check(cloudBuffers.size() <= 2, "cloud mesh allocated per image " + cloudBuffers.size());
                    }
                    capture(mc, "looped");
                    command(mc.getSingleplayerServer(), "execute in " + WORLD + " run fill -3 " + min + " 20 3 " + min + " 26 diamond_block");
                    next();
                }
                case 2 -> {
                    check(mc.level.getBlockState(new BlockPos(0, min, 23)).getBlock() == net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK, "client edit missing");
                    snapshots(mc); capture(mc, "updated");
                    command(mc.getSingleplayerServer(), "execute in " + WORLD + " run tp @a 0.5 " + (top + 0.5) + " 0.5 0 0"); next();
                }
                case 3 -> {
                    check(Math.abs(mc.player.getY() - (min + 0.5)) < 0.05, "top crossing failed");
                    check(offsets.stream().anyMatch(y -> y != 0), "terrain lost after top crossing"); capture(mc, "after-top");
                    command(mc.getSingleplayerServer(), "execute in " + WORLD + " run tp @a 0.5 " + (min - 1.5) + " 0.5 0 0"); next();
                }
                case 4 -> {
                    check(Math.abs(mc.player.getY() - (top - 1.5)) < 0.05, "bottom crossing failed");
                    check(offsets.stream().anyMatch(y -> y != 0), "terrain lost after bottom crossing"); capture(mc, "after-bottom");
                    if (!Boolean.getBoolean("biblioteca.scene.particles")) { phase = 8; ticks = 0; break; }
                    Particle smoke = mc.particleEngine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, -0.7, top - 0.1, 3, 0, 0, 0);
                    Particle portal = mc.particleEngine.createParticle(ParticleTypes.PORTAL, 1.7, top - 0.1, 3, 0, 0, 0);
                    check(smoke != null && portal != null, "native particle providers");
                    smoke.setLifetime(2000); portal.setLifetime(2000); smoke.scale(2); portal.scale(2);
                    particles.put(smoke, new ParticleSample("campfire")); particles.put(portal, new ParticleSample("portal")); next(); ticks = 50;
                }
                case 5, 6, 7 -> {
                    for (Map.Entry<Particle, ParticleSample> entry : particles.entrySet()) {
                        ParticleSample sample = entry.getValue();
                        if (Math.abs(sample.cameraY - ClientCompat.cameraPosition().y) > 0.1 || sample.renderedAge <= sample.previousAge) {
                            if (ticks > 400) throw new AssertionError("no fresh particle draw " + sample.name);
                            return;
                        }
                        check(entry.getKey().isAlive() && Math.abs(sample.y) < 12, "particle not beside camera " + sample.name + " y=" + sample.y);
                        check(sample.optimized == Boolean.parseBoolean(System.getProperty("biblioteca.scene.optimized", "true")), "unexpected particle writer path");
                        sample.previousAge = sample.renderedAge;
                        System.out.println("SCENE_PARTICLE phase=" + phase + " type=" + sample.name + " age=" + sample.renderedAge + " y=" + sample.y + " optimized=" + sample.optimized + " sameInstance=true");
                    }
                    capture(mc, "particles-" + phase);
                    if (phase < 7) command(mc.getSingleplayerServer(), "execute in " + WORLD + " run tp @a 0.5 " + (phase == 5 ? top + 0.5 : min - 1.5) + " 0.5 0 0");
                    next(); ticks = 50;
                }
                case 8 -> { particles.clear(); command(mc.getSingleplayerServer(), "biblioteca looping set " + WORLD + " false"); next(); }
                case 9 -> { check(offsets.equals(Set.of(0)), "ordinary drawing not restored " + offsets); capture(mc, "restored"); done = true; System.out.println("SCENE_DONE passed=true"); mc.stop(); }
                default -> throw new AssertionError("unknown phase " + phase);
            }
        } catch (Throwable error) { done = true; System.out.println("SCENE_FAILURE " + error); error.printStackTrace(); mc.stop(); }
    }
    private static void next() { phase++; ticks = 0; draws = cloudDraws = 0; offsets.clear(); cloudHeights.clear(); cloudBuffers.clear(); }
    private static void capture(Minecraft mc, String name) throws Exception {
        System.out.println("SCENE_SAMPLE name=" + name + " draws=" + draws + " offsets=" + new TreeSet<>(offsets)
                + " clouds=" + cloudHeights.size() + " cloudBuffers=" + cloudBuffers.size());
        Object target = has(mc, "getMainRenderTarget", 0) ? invoke(mc, "getMainRenderTarget") : invoke(mc.gameRenderer, "mainRenderTarget");
        Class<?> screenshot = Class.forName("net.minecraft.client.Screenshot");
        for (Method method : screenshot.getMethods()) if (method.getName().equals("grab") && Arrays.asList(method.getParameterTypes()).contains(String.class)) {
            Object[] args = new Object[method.getParameterCount()]; int integer = 0;
            long integerCount = Arrays.stream(method.getParameterTypes()).filter(t -> t == int.class).count();
            for (int i = 0; i < args.length; i++) {
                Class<?> type = method.getParameterTypes()[i];
                if (type == File.class) args[i] = mc.gameDirectory;
                else if (type == String.class) args[i] = name + ".png";
                else if (type.isInstance(target)) args[i] = target;
                else if (type == int.class) args[i] = integerCount > 1
                        ? invoke(mc.getWindow(), integer++ == 0 ? "getWidth" : "getHeight") : 1;
                else if (java.util.function.Consumer.class.isAssignableFrom(type)) args[i] = (java.util.function.Consumer<Object>) message -> {};
            }
            method.invoke(null, args); return;
        }
        throw new AssertionError("screenshot API missing");
    }
    private static void snapshots(Minecraft mc) throws Exception {
        String prefix = exists("me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache")
                ? "me.jellysquid.mods.sodium." : "net.caffeinemc.mods.sodium.";
        Object cache = Class.forName(prefix + "client.world.cloned.ClonedChunkSectionCache").getConstructor(Level.class).newInstance(mc.level);
        int min = Compat.minSection(mc.level), count = Compat.height(mc.level) >> 4;
        for (int requested : new int[]{min - 1, min + count}) {
            int canonical = SodiumBridge.foldSection(mc.level, requested);
            Object snapshot = invoke(cache, "acquire", 0, requested, 1);
            check(((SectionPos)invoke(snapshot, "getPosition")).y() == requested, "snapshot frame collapsed");
            if (has(snapshot, "getBiomeForNoiseGen", 3)) {
                Object actual = invoke(snapshot, "getBiomeForNoiseGen", 0, requested * 4, 5);
                Object expected = invoke(mc.level, "getNoiseBiome", 0, canonical * 4, 5);
                check(actual.equals(expected), "whole-column biome snapshot uses the wrong period");
            }
            for (int localY : new int[]{0, 15}) {
                Object state;
                if (has(snapshot, "getBlockState", 3)) state = invoke(snapshot, "getBlockState", 0, localY, 7);
                else if (has(snapshot, "getBlockPalette", 0)) {
                    int index = ((Number)invoke(invoke(snapshot, "getBlockData"), "get", (localY << 8) | (7 << 4))).intValue();
                    state = invoke(invoke(snapshot, "getBlockPalette"), "get", index);
                } else state = invoke(invoke(snapshot, "getBlockData"), "get", 0, localY, 7);
                check(state.equals(mc.level.getBlockState(new BlockPos(0, canonical * 16 + localY, 23))), "seam snapshot block mismatch");
                int light;
                if (has(snapshot, "getLightLevel", 4)) light = ((Number)invoke(snapshot, "getLightLevel", LightLayer.BLOCK, 0, localY, 7)).intValue();
                else {
                    Object layer = invoke(snapshot, "getLightArray", LightLayer.BLOCK);
                    light = layer == null ? 0 : ((Number)invoke(layer, "get", 0, localY, 7)).intValue();
                }
                check(light == mc.level.getBrightness(LightLayer.BLOCK, new BlockPos(0, canonical * 16 + localY, 23)), "seam snapshot light mismatch");
            }
            if (has(cache, "invalidate", 3)) invoke(cache, "invalidate", 0, canonical, 1);
            else invoke(cache, "invalidate", SectionPos.asLong(0, canonical, 1));
            Object replacement = invoke(cache, "acquire", 0, requested, 1);
            check(replacement != snapshot, "alias cache not invalidated");
        }
        System.out.println("SCENE_SNAPSHOTS both-boundaries=true canonical-blocks=true canonical-light=true requested-frame=true alias-invalidation=true");
    }
    private static void command(MinecraftServer server, String command) {
        if (!server.isSameThread()) { server.execute(() -> command(server, command)); return; }
        Object commands = server.getCommands(), source = server.createCommandSourceStack();
        invoke(commands, has(commands, "performPrefixedCommand", 2) ? "performPrefixedCommand" : "performCommand", source, command);
    }
    private static String dimensionId(Level level) {
        Object key = invoke(level, "dimension");
        return invoke(key, has(key, "location", 0) ? "location" : "identifier").toString();
    }
    public static boolean exists(String name) { try { Class.forName(name, false, SceneProbe.class.getClassLoader()); return true; } catch (ClassNotFoundException e) { return false; } }
    public static boolean has(Object target, String name, int count) {
        for (Method m : target.getClass().getMethods()) if (m.getName().equals(name) && m.getParameterCount() == count) return true;
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == count) return true;
        return false;
    }
    public static Object invoke(Object target, String name, Object... args) {
        try {
            List<Method> candidates = new ArrayList<>(Arrays.asList(target.getClass().getMethods()));
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) candidates.addAll(Arrays.asList(c.getDeclaredMethods()));
            for (Method m : candidates) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                boolean matches = true;
                for (int i = 0; i < args.length; i++) if (args[i] != null && !boxed(m.getParameterTypes()[i]).isInstance(args[i])) matches = false;
                if (!matches) continue;
                m.setAccessible(true); return m.invoke(target, args);
            }
            throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error instanceof InvocationTargetException e ? e.getCause() : error); }
    }
    private static Class<?> boxed(Class<?> type) {
        if (type == int.class) return Integer.class; if (type == double.class) return Double.class; if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class; if (type == long.class) return Long.class; return type;
    }
    public static Object optionalField(Object target, String name) { try { return field(target, name); } catch (RuntimeException missing) { return null; } }
    public static Object field(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (NoSuchFieldException ignored) {} catch (IllegalAccessException error) { throw new RuntimeException(error); }
        throw new IllegalArgumentException("missing field " + name);
    }
    public static void set(Object target, String name, Object value) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; }
        catch (NoSuchFieldException ignored) {} catch (IllegalAccessException error) { throw new RuntimeException(error); }
        throw new IllegalArgumentException("missing field " + name);
    }
    private static void check(boolean test, String message) { if (!test) throw new AssertionError(message); }
}
