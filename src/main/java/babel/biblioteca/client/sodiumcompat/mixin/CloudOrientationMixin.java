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
import net.minecraft.world.phys.Vec3;
@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.render.immediate.CloudRenderer$ViewOrientation",
        "net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer$ViewOrientation"}, remap = false)
public abstract class CloudOrientationMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapMethod(method = "getOrientation") @Coerce
    private static Object biblioteca$allCloudFaces(Vec3 camera, float minimum, float maximum, Operation<Object> original) {
        Object orientation = original.call(camera, minimum, maximum);
        return SodiumClouds.multiple() ? Enum.valueOf((Class)orientation.getClass(), "INSIDE_CLOUDS") : orientation;
    }
}
