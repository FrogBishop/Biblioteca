package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.EuclideanGameEventDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EuclideanGameEventDispatcher.class)
public abstract class LegacyGameEventRegistryYLoopMixin {
    @ModifyVariable(method = "postToListener", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$sourceBesideListener(BlockPos source, @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) GameEventListener listener) {
        if (!LoopingRegistry.isLooping(level)) return source;
        var position = listener.getListenerSource().getPosition(level);
        if (position.isEmpty()) return source;
        int y = (int) SeamPositions.foldToward(source.getY(), position.get().getY(), Compat.height(level));
        return y == source.getY() ? source : new BlockPos(source.getX(), y, source.getZ());
    }
}
