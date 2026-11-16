package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingServer;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The snapshot's integrated server overrides loadLevel without invoking its base implementation. */
@Mixin(IntegratedServer.class)
public abstract class InfiniteIntegratedServerMixin {
    @Inject(method = "loadLevel", at = @At("TAIL"))
    private void biblioteca$initializeSingleplayer(CallbackInfo ci) {
        ((LoopingServer) this).biblioteca$loadLoopingState();
    }
}
