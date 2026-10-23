package babel.biblioteca.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
//? if >=1.21.2 {
import net.minecraft.server.level.ParticleStatus;
//?} else {
/*import net.minecraft.client.ParticleStatus;
*///?}
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Small client API renames, kept separate from the renderer backends. */
public final class ClientCompat {
    private ClientCompat() {}

    public static net.minecraft.client.gui.components.Button button(net.minecraft.network.chat.Component label,
            int x, int y, int width, int height, net.minecraft.client.gui.components.Button.OnPress onPress) {
        //? if >=1.19.3 {
        return net.minecraft.client.gui.components.Button.builder(label, onPress).bounds(x, y, width, height).build();
        //?} else {
        /*return new net.minecraft.client.gui.components.Button(x, y, width, height, label, onPress);
        *///?}
    }

    public static ParticleStatus particles() {
        //? if >=1.19 {
        return Minecraft.getInstance().options.particles().get();
        //?} else {
        /*return Minecraft.getInstance().options.particles;
        *///?}
    }

    public static int renderDistance() {
        //? if >=1.18 {
        return Minecraft.getInstance().options.getEffectiveRenderDistance();
        //?} else {
        /*return Minecraft.getInstance().options.renderDistance;
        *///?}
    }


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

    //? if <1.21.5 {
    /*public static boolean empty(SectionRenderDispatcher.RenderSection section, net.minecraft.client.renderer.RenderType layer) {
        //? if >=1.20.2 {
        return section.getCompiled().isEmpty(layer);
        //?} else {
        return section.getCompiledChunk().isEmpty(layer);
        //?}
    }
    *///?}

    public static BlockPos origin(SectionRenderDispatcher.RenderSection section) {
        //? if >=1.21.5 {
        return section.getRenderOrigin();
        //?} else {
        /*return section.getOrigin();
        *///?}
    }
}
