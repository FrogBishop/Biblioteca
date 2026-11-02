package babel.biblioteca.client.sodiumcompat.mixin;
import babel.biblioteca.client.sodiumcompat.SodiumClouds;
import babel.biblioteca.client.ClientCompat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.render.immediate.CloudRenderer", "net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer"}, remap = false)
public abstract class CloudDraw7Mixin {
    @WrapMethod(method = "render")
    private void biblioteca$clouds(Camera camera, @Coerce Object world, @Coerce Object projection,
            @Coerce Object view, float ticks, float partial, int color, Operation<Void> original) {
        SodiumClouds.draw((Level)world, ClientCompat.position(camera).y,
                () -> original.call(camera, world, projection, view, ticks, partial, color));
    }
}
