package babel.biblioteca.mixin;

import babel.biblioteca.LoopingLevel;
import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The snapshot keeps its collision iterator inside CollisionGetter's anonymous class. */
@Mixin(targets = "net.minecraft.world.level.CollisionGetter$1")
public abstract class InfiniteBlockCollisionsMixin {
    @WrapOperation(method = "tryAdvance", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/CollisionGetter;getChunkForCollisions(II)Lnet/minecraft/world/level/BlockGetter;"))
    private BlockGetter biblioteca$periodicLookup(CollisionGetter getter, int x, int z, Operation<BlockGetter> original) {
        BlockGetter chunk = original.call(getter, x, z);
        if (chunk == null) return null;
        if (getter instanceof PathNavigationRegion region && ((LoopingLevel) region).biblioteca$isLooping()) return region;
        return getter instanceof Level level && LoopingRegistry.isLooping(level) ? level : chunk;
    }
}
