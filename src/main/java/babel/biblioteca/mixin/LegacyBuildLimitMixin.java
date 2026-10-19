package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Suppresses the "build limit exceeded" chat message before 26.1.
 *
 * Those releases do not have ServerPlayer.sendBuildLimitMessage; the message is
 * sent inline from handleUseItemOn after a placement attempt at the top of the
 * world fails. The server-side LevelBlockAccessMixin has already folded the
 * position, so the block itself is placed at the bottom row; only the error
 * message is wrong here.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LegacyBuildLimitMixin {

    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"))
    private void biblioteca$skipBuildLimit(
            ServerPlayer player, Component message, Operation<Void> original) {
        if (LoopingRegistry.isLooping(player.level())) return;
        original.call(player, message);
    }
}