package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

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

    /**
     * @author biblioteca
     * @reason suppress build-limit message in Y-looping dimensions
     */
    @Overwrite
    public void sendBuildLimitMessage(boolean tooHigh, int limit) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (LoopingRegistry.isLooping(self.level())) return;
        // Vanilla: send the translatable "build.tooHigh" / "build.tooLow" message.
        net.minecraft.network.chat.Component msg = tooHigh
                ? net.minecraft.network.chat.Component.translatable("build.tooHigh", limit)
                : net.minecraft.network.chat.Component.translatable("build.tooLow", limit);
        self.sendSystemMessage(msg);
    }
}
