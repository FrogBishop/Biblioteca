package biblioteca.sodiumtest;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.world.LoopingDimensionManager;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

/** Live renderer regression on a copied world, including native draw calls and saved frames. */
public final class SodiumProbe implements ModInitializer, ClientModInitializer {
    public static int layers, draws, shifted;
    private static int chestFrames, previousChestFrames;
    private static double chestCameraY, chestRenderY;
    public static void chestRendered(double cameraY, double y) { chestCameraY = cameraY; chestRenderY = y; chestFrames++; }
    private static final Map<Particle, ParticleSample> particles = new IdentityHashMap<>();
    private static final class ParticleSample {
        final String name;
        int renderedAge = -1, previousAge = -1;
        double cameraY, renderY;
        boolean optimized;
        ParticleSample(String name) { this.name = name; }
    }
    private int ticks, age;
    private int particlePhase, particleTicks;
    private boolean opened, failed;
    private final boolean baseline = Boolean.getBoolean("biblioteca.sodiumTest.baseline");

    public static boolean tracked(Particle particle) { return particles.containsKey(particle); }
    public static void particleRendered(Particle particle, double cameraY, double y, boolean optimized, int age) {
        ParticleSample sample = particles.get(particle);
        sample.cameraY = cameraY; sample.renderY = y; sample.optimized = optimized; sample.renderedAge = age;
    }

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LoopingDimensionManager.setLooping(Level.OVERWORLD, false);
            LoopingDimensionManager.setSkyLightMode(Level.OVERWORLD, SkyLightMode.HYBRID);
            server.overworld().setDayTime(6000);
            server.overworld().setBlock(new BlockPos(88, 255, -56), Blocks.GLASS.defaultBlockState(), 3);
        });
    }
    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(Minecraft client) {
        try {
            if (!opened && ++ticks == 10) { opened = true; client.loadLevel("sodium-world"); }
            if (client.level == null || client.player == null) return;
            age++;
            if (age == 1) { client.options.hideGui = true; pose(client, false, 110, 25); }
            if (age == 60 || age == 180 || age == 340 || age == 500) layers = draws = shifted = 0;
            if (age == 120) { sample(client, "ordinary", false); pose(client, true, 110, 25); }
            if (age == 280) { sample(client, "loop-ground", true); snapshots(client); pose(client, true, 235, -75); }
            if (age == 440) { sample(client, "loop-ceiling", true); pose(client, false, 110, 25); }
            if (age == 600) {
                sample(client, "restored", false);
                if (baseline) { finish(client); return; }
                client.getSingleplayerServer().execute(() -> {
                    var level = client.getSingleplayerServer().overworld();
                    for (int x = 85; x <= 91; x++) for (int z = -58; z <= -49; z++) for (int y = 0; y < 4; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(x, 252 + y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                });
                pose(client, true, 254.5, 0);
            }
            if (!baseline && age >= 640) particles(client);
        } catch (Throwable failure) {
            failure.printStackTrace(); System.out.println("SODIUM_FAILURE"); failed = true; client.stop();
        }
    }
    private void particles(Minecraft client) throws Exception {
        if (++particleTicks < 30) return;
        if (particlePhase == 0) {
            client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().overworld()
                    .setBlock(new BlockPos(90, 0, -52), Blocks.CHEST.defaultBlockState(), 3));
            Particle smoke = client.particleEngine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, 87.5, 255.9, -52, 0, 0, 0);
            Particle portal = client.particleEngine.createParticle(ParticleTypes.PORTAL, 89.5, 255.9, -52, 0, 0, 0);
            if (smoke == null || portal == null) throw new AssertionError("Native particle provider");
            for (Particle particle : new Particle[]{smoke, portal}) { particle.setLifetime(2000); particle.scale(3); }
            particles.put(smoke, new ParticleSample("campfire")); particles.put(portal, new ParticleSample("portal"));
        } else {
            double expectedY = particlePhase == 2 ? 0.5 : 254.5;
            if (Math.abs(client.player.getY() - expectedY) > 0.05) throw new AssertionError("Camera did not cross to " + expectedY);
            if (chestFrames <= previousChestFrames || Math.abs(chestCameraY - client.player.getEyeY()) > 0.1) {
                if (particleTicks > 400) throw new AssertionError("No chest render after crossing");
                return;
            }
            if (Math.abs(chestRenderY) > 12) throw new AssertionError("Chest received a duplicated seam offset: " + chestRenderY);
            previousChestFrames = chestFrames;
            System.out.println("SODIUM_CHEST phase=" + particlePhase + " renderY=" + chestRenderY);
            for (ParticleSample sample : particles.values()) {
                if (Math.abs(sample.cameraY - client.player.getEyeY()) > 0.1 || sample.renderedAge <= sample.previousAge) {
                    if (particleTicks > 400) throw new AssertionError("No particle draw after crossing: " + sample.name);
                    return;
                }
            }
            for (var entry : particles.entrySet()) {
                ParticleSample sample = entry.getValue();
                if (!entry.getKey().isAlive() || Math.abs(sample.renderY) > 10) throw new AssertionError("Particle lost at seam: " + sample.name + " y=" + sample.renderY);
                if (sample.optimized == Boolean.getBoolean("biblioteca.sodiumTest.vanillaParticles")) throw new AssertionError("Wrong particle renderer exercised");
                sample.previousAge = sample.renderedAge;
                System.out.println("SODIUM_PARTICLE phase=" + particlePhase + " type=" + sample.name + " age=" + sample.renderedAge
                        + " renderY=" + sample.renderY + " optimized=" + sample.optimized + " sameInstance=true");
            }
            frame(client, "particles-" + particlePhase);
            if (particlePhase == 1) pose(client, true, 256.5, 0);
            else if (particlePhase == 2) pose(client, true, -1.5, 0);
            else { finish(client); return; }
        }
        particlePhase++; particleTicks = 0;
    }
    private void finish(Minecraft client) {
        System.out.println("SODIUM_DONE passed=" + !failed); client.stop();
    }
    private void frame(Minecraft client, String name) throws Exception {
        var window = client.getWindow();
        try (var image = Screenshot.takeScreenshot(window.getWidth(), window.getHeight(), client.getMainRenderTarget())) {
            image.writeToFile(Path.of(name + ".png"));
        }
    }
    private void pose(Minecraft client, boolean looping, double y, float pitch) {
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            LoopingDimensionManager.setLooping(Level.OVERWORLD, looping);
            Biblioteca.broadcastState(server);
            var player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.CREATIVE);
            player.abilities.flying = true;
            player.onUpdateAbilities();
            player.teleportTo(server.overworld(), 88, y, -56, 0, pitch);
        });
    }
    private void sample(Minecraft client, String name, boolean looping) throws Exception {
        boolean passed = baseline && looping ? layers == 0 : layers > 0 && draws > 0;
        if (name.equals("loop-ceiling") && !baseline) passed &= shifted > 0;
        failed |= !passed;
        System.out.println("SODIUM_SAMPLE name=" + name + " layers=" + layers + " draws=" + draws
                + " shifted=" + shifted + " backend=" + SodiumWorldRenderer.getInstance().getChunkRenderer().getRendererName()
                + " passed=" + passed);
        var window = client.getWindow();
        try (var image = Screenshot.takeScreenshot(window.getWidth(), window.getHeight(), client.getMainRenderTarget())) {
            image.writeToFile(Path.of(name + ".png"));
        }
    }
    private void snapshots(Minecraft client) {
        var cache = new ClonedChunkSectionCache(client.level);
        var slice = new WorldSlice(client.level);
        var upper = WorldSlice.prepare(client.level, SectionPos.of(5, 15, -4), cache);
        var lower = WorldSlice.prepare(client.level, SectionPos.of(5, 0, -4), cache);
        boolean correct;
        try {
            slice.copyData(upper);
            correct = slice.getBlockState(88, 256, -56).is(Blocks.BEDROCK);
            slice.copyData(lower);
            correct &= slice.getBlockState(88, -1, -56).is(Blocks.GLASS);
            int sky = client.level.getBrightness(LightLayer.SKY, new BlockPos(88, 255, -56));
            correct &= sky > 0 && slice.getBrightness(LightLayer.SKY, new BlockPos(88, -1, -56)) == sky;
            var alias = cache.acquire(5, 16, -4);
            cache.invalidate(5, 0, -4);
            var refreshed = cache.acquire(5, 16, -4);
            correct &= alias != refreshed;
            cache.release(alias); cache.release(refreshed);
        } finally { upper.releaseResources(); lower.releaseResources(); }
        boolean passed = baseline ? !correct : correct;
        failed |= !passed;
        System.out.println("SODIUM_SNAPSHOT periodic=" + correct + " passed=" + passed);
    }
}
