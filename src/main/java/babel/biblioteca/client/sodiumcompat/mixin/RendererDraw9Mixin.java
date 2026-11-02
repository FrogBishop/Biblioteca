package babel.biblioteca.client.sodiumcompat.mixin;

import babel.biblioteca.client.sodiumcompat.SodiumBridge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class RendererDraw9Mixin {
    @WrapMethod(method = "render")
    private void biblioteca$images(@Coerce Object matrices, @Coerce Object lists, @Coerce Object pass, @Coerce Object camera, @Coerce Object fog, boolean indexed, @Coerce Object sampler, @Coerce Object uniforms, @Coerce Object times, Operation<Void> original) {
        SodiumBridge.draw(camera, shifted -> original.call(matrices, lists, pass, shifted, fog, indexed, sampler, uniforms, times));
    }
}
