package babel.biblioteca.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Screen navigation behind reflection.
 *
 * The 26.2 release moved the current screen from {@code Minecraft.screen} /
 * {@code setScreen} onto {@code Minecraft.gui} ({@code screen()} /
 * {@code setScreenAndShow}). A jar compiled for 26.1 must still open the
 * configuration screen on 26.3, so neither spelling can be linked directly.
 */
public final class ClientScreens {

    private ClientScreens() {}

    public static Screen current(Minecraft minecraft) {
        try {
            // 26.2+ exposes the screen through the public gui field.
            Object gui = Minecraft.class.getField("gui").get(minecraft);
            Method screen = gui.getClass().getMethod("screen");
            return (Screen) screen.invoke(gui);
        } catch (ReflectiveOperationException ignored) {
            // 26.1 and older keep the screen on Minecraft itself.
        }
        try {
            Field screen = Minecraft.class.getDeclaredField("screen");
            screen.setAccessible(true);
            return (Screen) screen.get(minecraft);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read the current Minecraft screen", e);
        }
    }

    public static void show(Minecraft minecraft, Screen screen) {
        try {
            Method show = Minecraft.class.getMethod("setScreenAndShow", Screen.class);
            show.invoke(minecraft, screen);
            return;
        } catch (ReflectiveOperationException ignored) {
            // 26.1 and older expose setScreen on Minecraft.
        }
        try {
            Method setScreen = Minecraft.class.getMethod("setScreen", Screen.class);
            setScreen.invoke(minecraft, screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot open a Minecraft screen", e);
        }
    }
}
