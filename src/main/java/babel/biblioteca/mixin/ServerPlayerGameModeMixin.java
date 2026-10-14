package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps the reach-distance check inside handleBlockBreakAction so that
 * interactions across the Y seam (e.g. player near y=319 targeting y=-64) pass.
 *
 * Uses @WrapOperation (MixinExtras) to intercept exactly the
 * isWithinBlockInteractionRange call inside handleBlockBreakAction without
 * touching any other call site.  If the dimension has Y-looping enabled and
 * the block Y is outside the valid range, we fold the Y first and re-evaluate.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow protected ServerLevel level;

    @WrapOperation(
            method = "handleBlockBreakAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z"))
    private boolean biblioteca$wrapReachCheck(
            Player player, BlockPos pos, double delta, Operation<Boolean> original) {

        // Standard check first — if it passes, no need to fold.
        if (original.call(player, pos, delta)) return true;

        // Only fold when the dimension has Y-looping and the coordinate is out of range.
        if (!LoopingRegistry.isLoopingClient(level.dimension())) {
            // Use server delegate explicitly: level is a ServerLevel (server-side only mixin).
            if (!babel.biblioteca.world.LoopingDimensionManager.isLooping(level.dimension())) {
                return false;
            }
        }

        int y      = pos.getY();
        int minY   = level.getMinY();
        int height = level.getHeight();
        if (y >= minY && y < minY + height) return false; // already in range, original check failed for another reason

        BlockPos folded = new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
        return original.call(player, folded, delta);
    }
}
