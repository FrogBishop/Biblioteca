package babel.biblioteca.validation.save.mixin;

import babel.biblioteca.validation.save.SaveLifecycleChecks;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes native boundaries; it neither replaces saving nor changes shutdown behavior. */
@Mixin(MinecraftServer.class)
public abstract class SaveLifecycleMixin {
    @Inject(method = "stopServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketProcessor;close()V"))
    private void beforeChunkDrain(CallbackInfo ci) {
        SaveLifecycleChecks.beforeShutdown();
    }

    @Inject(method = "stopServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;saveAllChunks(ZZZ)Z", shift = At.Shift.AFTER))
    private void afterChunkFlush(CallbackInfo ci) {
        SaveLifecycleChecks.afterChunkFlush();
    }

    // Fabric's SERVER_STOPPED and Biblioteca's cleanup both inject at TAIL. Observe
    // after all default-order callbacks so mod discovery order cannot fail this check.
    @Inject(method = "stopServer", at = @At("TAIL"), order = 2000)
    private void afterShutdown(CallbackInfo ci) {
        SaveLifecycleChecks.stopped((MinecraftServer) (Object) this);
    }
}
