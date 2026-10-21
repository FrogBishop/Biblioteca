package babel.biblioteca.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Small client API renames, kept separate from the renderer backends. */
public final class ClientCompat {
    private ClientCompat() {}

    public static Camera camera() {
        //? if >=26.2 {
        return Minecraft.getInstance().gameRenderer.mainCamera();
        //?} else {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera();
        *///?}
    }

    public static Vec3 position(Camera camera) {
        //? if >=1.21.11 {
        return camera.position();
        //?} else {
        /*return camera.getPosition();
        *///?}
    }

    public static Vec3 cameraPosition() {
        return position(camera());
    }

    public static BlockPos origin(SectionRenderDispatcher.RenderSection section) {
        //? if >=1.21.5 {
        return section.getRenderOrigin();
        //?} else {
        /*return section.getOrigin();
        *///?}
    }
}
