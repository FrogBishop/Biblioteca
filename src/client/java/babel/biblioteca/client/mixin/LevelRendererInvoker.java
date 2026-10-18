package babel.biblioteca.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reach into the renderer's translucent re-sort scheduling.
 *
 * Vanilla calls this whenever the camera has moved, which is why the mirrored rows only ever got
 * their correct sorting after the player walked around: the mapping that says which row a section is
 * drawn at is published by the extraction pass, after the section meshes have already been compiled.
 * Being able to ask for a re-sort when that mapping changes makes the water correct on the first
 * frame after joining a world, instead of waiting for the camera to move.
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererInvoker {

    @Invoker("scheduleTranslucentSectionResort")
    void biblioteca$scheduleTranslucentSectionResort(Vec3 camera);
}
