package biblioteca.visibility;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Real-client diagnostics; all teleports and screenshots use an isolated copied world. */
public final class VisibilityChecks {
    public record Pose(int fov, int y, int yaw, int pitch) {}
    private static final List<Pose> POSES = new ArrayList<>();
    private static int ticks, index = -1, poseTicks;
    private static boolean failed;
    public static volatile String frame;
    public static volatile int missing;
    public static volatile boolean poseMatches;

    static {
        for (int fov : new int[]{70, 110}) for (int yaw : new int[]{0, 45, 90, 135})
            for (int pitch : new int[]{-5, -15, -30, -45, -60, -75, -89})
                POSES.add(new Pose(fov, 145, yaw, pitch));
        for (int y : new int[]{136, 160, 200}) for (int fov : new int[]{70, 110})
            for (int pitch : new int[]{-30, -60, -89}) POSES.add(new Pose(fov, y, 0, pitch));
    }

    public static Pose pose() { return index >= 0 && index < POSES.size() ? POSES.get(index) : null; }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ticks++;
        mc.mouseHandler.releaseMouse();
        if (ticks == 30) {
            mc.getSingleplayerServer().execute(() -> {
                command("gamemode spectator @a");
                command("time set noon");
                command("weather clear");
                command("tp @a 0.5 145 0.5 0 -89");
            });
        }
        Pose pose = pose();
        if (pose != null) {
            // Keep mouse motion and interpolation from changing the measured view.
            mc.player.setYRot(pose.yaw());
            mc.player.setXRot(pose.pitch());
            mc.player.yRotO = pose.yaw();
            mc.player.xRotO = pose.pitch();
        }
        if (ticks < 200 || (index >= 0 && ++poseTicks < 30)) return;
        if (pose != null) {
            System.out.println("VISIBILITY_SAMPLE " + pose + " " + frame);
            if (frame == null || missing > 0 || !poseMatches) failed = true;
            if (pose.y() == 145 && pose.yaw() == 0
                    && (pose.pitch() == -5 || pose.pitch() == -30 || pose.pitch() == -89)) {
                String name = "fov" + pose.fov() + "-y" + pose.y() + "-pitch" + pose.pitch() + ".png";
                Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
                        message -> System.out.println("VISIBILITY_SCREENSHOT " + name + " " + message.getString()));
            }
        }
        poseTicks = 0;
        if (++index == POSES.size()) {
            System.out.println("VISIBILITY_DONE failed=" + failed + " cases=" + POSES.size());
            // The last screenshot is taken well before the final pose.
            mc.stop();
            return;
        }
        Pose next = pose();
        frame = null;
        missing = 0;
        poseMatches = false;
        mc.options.fov().set(next.fov());
        mc.getSingleplayerServer().execute(() -> command("tp @a 0.5 " + next.y() + " 0.5 " + next.yaw() + " " + next.pitch()));
    }

    private static void command(String text) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), text);
    }
}
