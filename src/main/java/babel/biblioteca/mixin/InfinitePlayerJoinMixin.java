package babel.biblioteca.mixin;

import babel.biblioteca.Biblioteca;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The snapshot's Fabric networking API predates the JOIN event. */
@Mixin(PlayerList.class)
public abstract class InfinitePlayerJoinMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void biblioteca$sendSnapshot(Connection connection, ServerPlayer player, CallbackInfo ci) {
        Biblioteca.sendState(player, Biblioteca.currentPayload());
    }
}
