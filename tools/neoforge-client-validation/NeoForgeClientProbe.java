package biblioteca.clientcheck;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SkyLightMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LightLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real Java-21 client: loaded-world rendering, light packets, seam movement and creation UI. */
@Mod(value = "biblioteca_client_check", dist = Dist.CLIENT)
public final class NeoForgeClientProbe {
    private int phase, ticks;
    private boolean pending, finished;
    private float pitch = 60;
    private CreateWorldScreen creation;
    private static final InputWithModifiers PRESS = new InputWithModifiers() {
        public int input() { return 0; }
        public int modifiers() { return 0; }
    };

    public NeoForgeClientProbe() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }
    private void tick(Minecraft client) {
        if (finished || client.getOverlay() != null) return;
        try {
            if (Boolean.getBoolean("biblioteca.clientCheck.ui")) { ui(client); return; }
            if (client.level == null || client.player == null) return;
            client.mouseHandler.releaseMouse(); KeyMapping.releaseAll();
            client.options.hideGui = true;
            client.player.setYRot(0); client.player.setXRot(pitch);
            client.player.yRotO = 0; client.player.xRotO = pitch;
            if (pending || ++ticks < 60) return;
            switch (phase) {
                case 0 -> {
                    check(LoopingRegistry.isLooping(client.level), "initial looping snapshot");
                    check(LoopingRegistry.skyLightMode(client.level) == SkyLightMode.HYBRID, "initial hybrid snapshot");
                    command(client, "gamemode spectator @a"); command(client, "time set noon"); command(client, "weather clear");
                    command(client, "tp @a 24 30 24 0 60"); next();
                }
                case 1 -> light(client, 15, "open", () -> {
                    frame(client, "ground"); command(client, "fill 4 -64 4 11 319 11 minecraft:stone"); next();
                });
                case 2 -> light(client, 0, "filled", () -> {
                    command(client, "fill 4 -64 4 11 319 11 minecraft:air"); next();
                });
                case 3 -> light(client, 15, "reopened", () -> {
                    pitch = -75; command(client, "tp @a 24 300 24 0 -75"); next();
                });
                case 4 -> {
                    check(Math.abs(client.player.getY() - 300) < 0.02, "ceiling camera position");
                    frame(client, "ceiling"); pitch = 60; command(client, "tp @a 24 325 24 0 60"); next();
                }
                case 5 -> {
                    check(Math.abs(client.player.getY() + 59) < 0.02, "client crosses the seam at the native teleport");
                    frame(client, "wrapped"); next();
                }
                case 6 -> finish(client, "ground", "ceiling", "wrapped");
                default -> throw new AssertionError("unknown phase");
            }
        } catch (Throwable failure) { fail(client, failure); }
    }
    private void light(Minecraft client, int expected, String name, Runnable success) {
        pending = true;
        client.getSingleplayerServer().execute(() -> {
            int server = client.getSingleplayerServer().overworld().getBrightness(LightLayer.SKY, new BlockPos(8, 16, 8));
            client.execute(() -> {
                pending = false;
                int local = client.level.getBrightness(LightLayer.SKY, new BlockPos(8, 16, 8));
                if (server == expected && local == expected) {
                    System.out.println("CLIENT_CHECK_LIGHT phase=" + name + " server=" + server + " client=" + local);
                    success.run();
                } else if (ticks > 240) fail(client, new AssertionError("Sky " + name + ": server=" + server + " client=" + local));
            });
        });
    }
    private void ui(Minecraft client) throws Exception {
        if (++ticks < 40) return;
        switch (phase) {
            case 0 -> {
                Screen previous = client.screen;
                CreateWorldScreen.openFresh(client, () -> client.setScreen(previous)); next();
            }
            case 1 -> {
                if (!(client.screen instanceof CreateWorldScreen screen)) return;
                creation = screen;
                var tabs = screen.children().stream().filter(TabNavigationBar.class::isInstance).map(TabNavigationBar.class::cast).findFirst().orElseThrow();
                tabs.selectTab(2, false); next();
            }
            case 2 -> {
                Button attributes = button(creation, "biblioteca.gui.looping_button");
                Button packs = button(creation, "selectWorld.dataPacks");
                check(attributes.getX() == packs.getX() && attributes.getWidth() == packs.getWidth()
                        && attributes.getY() == packs.getY() + packs.getHeight() + 8, "native More-tab layout");
                frame(client, "world-options"); attributes.onPress(PRESS); next();
            }
            case 3 -> {
                check(client.screen.getClass().getSimpleName().equals("LegacyDimensionConfigScreen"), "opens dimension settings");
                frame(client, "dimension-attributes"); next();
            }
            case 4 -> finish(client, "world-options", "dimension-attributes");
            default -> throw new AssertionError("unknown UI phase");
        }
    }
    private static Button button(Screen screen, String key) {
        String label = Component.translatable(key).getString();
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.visible && b.active && b.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private void next() { phase++; ticks = 0; }
    private static void command(Minecraft client, String command) {
        var server = client.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }
    private static void frame(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                message -> System.out.println("CLIENT_CHECK_FRAME " + name));
    }
    private void finish(Minecraft client, String... frames) {
        for (String frame : frames) if (!Files.isRegularFile(Path.of("screenshots", frame + ".png"))) {
            check(ticks < 200, "screenshot writer finished"); return;
        }
        finished = true; System.out.println("CLIENT_CHECK_DONE passed=true"); client.stop();
    }
    private void fail(Minecraft client, Throwable failure) {
        failure.printStackTrace(); finished = true; System.out.println("CLIENT_CHECK_FAILURE phase=" + phase); client.stop();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
