package biblioteca.sodiumtest.mixin;

import biblioteca.sodiumtest.SodiumProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState", remap = false, priority = 500)
public abstract class SodiumOriginObserver {
    @WrapMethod(method = "getY")
    private int count(Operation<Integer> original) {
        int y = original.call();
        SodiumProbe.draws++;
        if (y < -8 || y > 232) SodiumProbe.shifted++;
        return y;
    }
}
