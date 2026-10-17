package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
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

        // Fold first, then ask once. The previous shape asked about the raw coordinate, and only
        // when that failed asked again about the folded one - which spent the whole check on a
        // coordinate that cannot be in reach by construction.
        BlockPos target = pos;

        ServerLevel level = this.level;
        if (level != null && LoopingRegistry.isLooping(level)) {
            int minY   = level.getMinY();
            int height = level.getHeight();
            int y      = pos.getY();

            if (y < minY || y >= minY + height) {
                target = new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
            }
        }

        return original.call(player, target, delta);
    }
}
