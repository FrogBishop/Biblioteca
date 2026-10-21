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
import java.util.function.BiConsumer;

/**
 * Hooks the server lifecycle: loads the per-world attributes when the world starts, applies the
 * choices made on the create-world screen the first time a new world loads, and pushes the state to
 * connected clients.
 *
 * The attributes live in the overworld's SavedData, which is only readable once the server has a
 * level; that is why this is attached to loadLevel rather than to a static initializer.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(at = @At("TAIL"), method = "loadLevel")
    private void biblioteca$onLevelLoaded(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        LoopingDimensionManager.onServerStarted(server);

        // Apply world-creation GUI choices on first load of a brand-new world (singleplayer).
        if (PendingLoopingConfig.hasPending()) {
            biblioteca$applyPending(
                    PendingLoopingConfig.getPendingLooping(),
                    LoopingDimensionManager.getLoopingDimensions(),
                    LoopingDimensionManager::setLooping);
            biblioteca$applyPending(
                    PendingLoopingConfig.getPendingBeaconIgnoresSky(),
                    LoopingDimensionManager.getBeaconIgnoresSkyDimensions(),
                    LoopingDimensionManager::setBeaconIgnoresSky);
            biblioteca$applyPending(
                    PendingLoopingConfig.getPendingNoVoidFog(),
                    LoopingDimensionManager.getNoVoidFogDimensions(),
                    LoopingDimensionManager::setNoVoidFog);
            biblioteca$applyPending(
                    PendingLoopingConfig.getPendingNoSkyLight(),
                    LoopingDimensionManager.getNoSkyLightDimensions(),
                    LoopingDimensionManager::setNoSkyLight);

            PendingLoopingConfig.consume();
        }

        // Send the loaded snapshot to any players already connected at this lifecycle point.
        Biblioteca.broadcastState(server);
    }

    @Inject(at = @At("HEAD"), method = "stopServer")
    private void biblioteca$onServerStop(CallbackInfo ci) {
        LoopingDimensionManager.onServerStopped();
    }

    /**
     * Makes the stored set match the pending set. The pending set always wins, including removals:
     * a dimension that was looping before must stop if the player left the toggle off.
     */
    private static void biblioteca$applyPending(
            Set<ResourceKey<Level>> wanted,
            Set<ResourceKey<Level>> current,
            BiConsumer<ResourceKey<Level>, Boolean> setter) {

        for (ResourceKey<Level> dimension : current) {
            if (!wanted.contains(dimension)) setter.accept(dimension, false);
        }
        for (ResourceKey<Level> dimension : wanted) {
            setter.accept(dimension, true);
        }
    }
}
