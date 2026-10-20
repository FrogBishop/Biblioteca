package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the "build limit exceeded" message in a looping dimension.
 *
 * The message is only a notification: the placement itself is folded by LevelBlockAccessMixin, so a
 * block placed at y = 320 lands at y = -64. Vanilla would still tell the player they are above the
 * build limit, which is not true in a ring.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerBuildLimitMixin {

    @Inject(method = "sendBuildLimitMessage", at = @At("HEAD"), cancellable = true)
    private void biblioteca$silenceAtTheSeam(boolean tooHigh, int limit, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (LoopingRegistry.isLooping(self.level())) ci.cancel();
    }
}
