package babel.biblioteca.mixin;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.PendingLoopingConfig;
import babel.biblioteca.world.LoopingDimensionManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(at = @At("TAIL"), method = "loadLevel")
    private void biblioteca$onLevelLoaded(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        LoopingDimensionManager.onServerStarted(server);

        // Apply world-creation GUI choices on first load of a brand-new world (singleplayer).
        if (PendingLoopingConfig.hasPending()) {
            Set<ResourceKey<Level>> pendingLooping = PendingLoopingConfig.getPendingLooping();
            for (ResourceKey<Level> dim : LoopingDimensionManager.getLoopingDimensions()) {
                if (!pendingLooping.contains(dim)) LoopingDimensionManager.setLooping(dim, false);
            }
            for (ResourceKey<Level> dim : pendingLooping) {
                LoopingDimensionManager.setLooping(dim, true);
            }

            Set<ResourceKey<Level>> pendingBeacon = PendingLoopingConfig.getPendingBeaconIgnoresSky();
            for (ResourceKey<Level> dim : LoopingDimensionManager.getBeaconIgnoresSkyDimensions()) {
                if (!pendingBeacon.contains(dim)) LoopingDimensionManager.setBeaconIgnoresSky(dim, false);
            }
            for (ResourceKey<Level> dim : pendingBeacon) {
                LoopingDimensionManager.setBeaconIgnoresSky(dim, true);
            }

            Set<ResourceKey<Level>> pendingVoidFog = PendingLoopingConfig.getPendingVoidFog();
            for (ResourceKey<Level> dim : LoopingDimensionManager.getVoidFogDimensions()) {
                if (!pendingVoidFog.contains(dim)) LoopingDimensionManager.setVoidFog(dim, false);
            }
            for (ResourceKey<Level> dim : pendingVoidFog) {
                LoopingDimensionManager.setVoidFog(dim, true);
            }

            Set<ResourceKey<Level>> pendingSkyLight = PendingLoopingConfig.getPendingSkyLight();
            for (ResourceKey<Level> dim : LoopingDimensionManager.getSkyLightDimensions()) {
                if (!pendingSkyLight.contains(dim)) LoopingDimensionManager.setSkyLight(dim, false);
            }
            for (ResourceKey<Level> dim : pendingSkyLight) {
                LoopingDimensionManager.setSkyLight(dim, true);
            }

            PendingLoopingConfig.consume();
        }

        // Push the snapshot to any players already connected (e.g. after /reload).
        Biblioteca.broadcastState(server);
    }

    @Inject(at = @At("HEAD"), method = "stopServer")
    private void biblioteca$onServerStop(CallbackInfo ci) {
        LoopingDimensionManager.onServerStopped();
    }
}
