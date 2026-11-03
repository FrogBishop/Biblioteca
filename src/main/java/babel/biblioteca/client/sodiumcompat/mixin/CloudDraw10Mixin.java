package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumClouds;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Sodium 0.5.8 for Minecraft 1.20.5 supplies both view and projection matrices. */
@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.render.immediate.CloudRenderer", "net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer"}, remap = false)
public abstract class CloudDraw10Mixin {
    @WrapMethod(method = "render")
    private void biblioteca$clouds(@Coerce Object world, @Coerce Object player, @Coerce Object poses,
            @Coerce Object view, @Coerce Object projection, float ticks, float partial,
            double x, double y, double z, Operation<Void> original) {
        SodiumClouds.draw((Level) world, y,
                () -> original.call(world, player, poses, view, projection, ticks, partial, x, y, z));
    }
}
