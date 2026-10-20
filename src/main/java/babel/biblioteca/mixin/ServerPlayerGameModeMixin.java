package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps the reach-distance check inside handleBlockBreakAction so that breaking a block across
 * the Y seam (a player at y=319 targeting the row at y=-64, which the ring puts right above
 * them) passes.
 *
 * The original call is left untouched for the ordinary case; only a coordinate that is out of
 * range and lands back near the player once folded is retried.
 *
 * The handler takes the receiver first. MixinExtras derives the expected handler signature from
 * the wrapped call and insists on the receiver for an instance method, and the receiver is then
 * also the first argument of Operation.call.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow protected ServerLevel level;

    @WrapOperation(
            method = "handleBlockBreakAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z"))
    private boolean biblioteca$wrapReachCheck(
            ServerPlayer player, BlockPos pos, double delta, Operation<Boolean> original) {

        // Fold first, then ask once. Asking about the raw coordinate first spent the whole check
        // on a position that cannot be in reach by construction.
        ServerLevel level = this.level;
        BlockPos target = level != null && LoopingRegistry.isLooping(level)
                ? SeamPositions.foldBlockPos(level, pos)
                : pos;

        return original.call(player, target, delta);
    }
}
