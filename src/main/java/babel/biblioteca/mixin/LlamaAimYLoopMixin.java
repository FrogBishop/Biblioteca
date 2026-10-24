package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Llama.class)
public abstract class LlamaAimYLoopMixin {
    @ModifyVariable(method = "spit", at = @At("STORE"), ordinal = 1)
    private double biblioteca$nearSpitDelta(double deltaY) {
        Level level = ((Llama)(Object)this).level();
        return LoopingRegistry.isLooping(level) ? SeamPositions.foldForDistance(level, deltaY, 0) : deltaY;
    }
}
