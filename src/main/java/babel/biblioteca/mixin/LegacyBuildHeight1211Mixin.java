package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removes the vanilla build-height ceiling from the 1.21.1 interaction packets.
 *
 * handleUseItemOn refuses the whole interaction when the clicked block is at or above
 * getMaxBuildHeight(), and handlePlayerAction hands the same limit to
 * ServerPlayerGameMode.handleBlockBreakAction, which rejects a break above it. In a Y-looping
 * dimension every row is an ordinary row: the position is folded by LevelBlockAccessMixin, so the
 * limit has no meaning. Suppressing only the chat message (LegacyBuildLimitMixin) left the action
 * itself rejected, which is why a player at the top of a short world could see the copied terrain
 * but not place or break anything on it.
 *
 * Returning Integer.MAX_VALUE makes both comparisons inert; the packet's position is still the
 * real one, so the reach check and the folded block access keep working on the row the player
 * actually clicked.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LegacyBuildHeight1211Mixin {

    @Shadow public ServerPlayer player;

    @ModifyExpressionValue(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getMaxBuildHeight()I"))
    private int biblioteca$noCeilingOnUse(int original) {
        return LoopingRegistry.isLooping(this.player.level()) ? Integer.MAX_VALUE : original;
    }

    @ModifyExpressionValue(
            method = "handlePlayerAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getMaxBuildHeight()I"))
    private int biblioteca$noCeilingOnBreak(int original) {
        return LoopingRegistry.isLooping(this.player.level()) ? Integer.MAX_VALUE : original;
    }
}
