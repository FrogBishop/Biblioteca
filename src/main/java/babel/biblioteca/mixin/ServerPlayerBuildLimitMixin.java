package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the build-limit message in 26.1+ looping dimensions.
 *
 * The packet handler folds the clicked position before any check runs, so the
 * height branches that raise this message never see an out-of-range Y. The one
 * remaining caller is the "may not interact" branch (world border or spawn
 * protection), where "too high" is simply the wrong explanation; dropping it is
 * better than showing a message that does not describe the failure.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerBuildLimitMixin {

    @Inject(method = "sendBuildLimitMessage", at = @At("HEAD"), cancellable = true)
    private void biblioteca$silenceAtTheBoundary(boolean tooHigh, int limit, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (LoopingRegistry.isLooping(Compat.level(self))) ci.cancel();
    }
}
