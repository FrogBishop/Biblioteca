package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = {"me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer", "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer"}, remap = false)
public abstract class RendererDraw7Mixin {
    @WrapMethod(method = "render")
    private void biblioteca$images(@Coerce Object matrices, @Coerce Object commands, @Coerce Object lists, @Coerce Object pass, @Coerce Object camera, @Coerce Object fog, boolean indexed, Operation<Void> original) {
        SodiumBridge.draw(camera, shifted -> original.call(matrices, commands, lists, pass, shifted, fog, indexed));
    }
}
