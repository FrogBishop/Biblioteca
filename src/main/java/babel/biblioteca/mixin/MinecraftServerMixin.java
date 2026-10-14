package babel.biblioteca.mixin;

import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.world.LoopingDimensionManager;
import babel.biblioteca.PendingLoopingConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.stream.Collectors;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(at = @At("TAIL"), method = "loadLevel")
    private void biblioteca$onLevelLoaded(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        LoopingDimensionManager.onServerStarted(server);

        // Apply world-creation GUI choices on first load of a brand-new world (singleplayer).
        if (PendingLoopingConfig.hasPending()) {
            Set<ResourceKey<Level>> enabled = PendingLoopingConfig.getPendingEnabled();
            for (ResourceKey<Level> dim : LoopingDimensionManager.getLoopingDimensions()) {
                if (!enabled.contains(dim)) LoopingDimensionManager.setLooping(dim, false);
            }
            for (ResourceKey<Level> dim : enabled) {
                LoopingDimensionManager.setLooping(dim, true);
            }
            PendingLoopingConfig.consume();
        }

        // Broadcast to any players already connected (e.g. after /reload).
        Set<String> keys = LoopingDimensionManager.getLoopingDimensions().stream()
                .map(k -> k.identifier().toString())
                .collect(Collectors.toSet());
        LoopingStatePayload payload = new LoopingStatePayload(keys);
        server.getPlayerList().getPlayers()
                .forEach(p -> ServerPlayNetworking.send(p, payload));
    }

    @Inject(at = @At("HEAD"), method = "stopServer")
    private void biblioteca$onServerStop(CallbackInfo ci) {
        LoopingDimensionManager.onServerStopped();
    }
}
