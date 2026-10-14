package babel.biblioteca.mixin;

import babel.biblioteca.world.LoopingDimensionManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * In a Y-looping dimension, suppress the "build limit exceeded" message so
 * the player can place blocks at Y=320 (which folds to Y=-64).
 * The server-side LevelBlockAccessMixin already folds the BlockPos before
 * the actual block placement, so the block goes to the correct position.
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
        if (LoopingDimensionManager.isLooping(self.level().dimension())) return;
        // Vanilla: send the translatable "build.tooHigh" / "build.tooLow" message.
        net.minecraft.network.chat.Component msg = tooHigh
                ? net.minecraft.network.chat.Component.translatable("build.tooHigh", limit)
                : net.minecraft.network.chat.Component.translatable("build.tooLow", limit);
        self.sendSystemMessage(msg);
    }
}
