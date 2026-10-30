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
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.Path;

/** Live renderer regression on a copied world, including native draw calls and saved frames. */
public final class SodiumProbe implements ModInitializer, ClientModInitializer {
    public static int layers, draws, shifted;
    private int ticks, age;
    private boolean opened, failed;
    private final boolean baseline = Boolean.getBoolean("biblioteca.sodiumTest.baseline");

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
                System.out.println("SODIUM_DONE passed=" + !failed);
                client.stop();
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); System.out.println("SODIUM_FAILURE"); failed = true; client.stop();
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
