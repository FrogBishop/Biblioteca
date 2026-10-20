package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.8 version of the build-height gate removal.
 *
 * The two interaction paths now read Level.getMaxY() instead of getMaxBuildHeight(), and both
 * comparisons are inclusive (y <= maxY on use, y > maxY on break). Answering Integer.MAX_VALUE
 * keeps them inert for looping dimensions while leaving the folded block access untouched.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MidBuildHeight1218Mixin {

    @Shadow public ServerPlayer player;

    @ModifyExpressionValue(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getMaxY()I"))
    private int biblioteca$noCeilingOnUse(int original) {
        return LoopingRegistry.isLooping(this.player.level()) ? Integer.MAX_VALUE : original;
    }

    @ModifyExpressionValue(
            method = "handlePlayerAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getMaxY()I"))
    private int biblioteca$noCeilingOnBreak(int original) {
        return LoopingRegistry.isLooping(this.player.level()) ? Integer.MAX_VALUE : original;
    }
}
