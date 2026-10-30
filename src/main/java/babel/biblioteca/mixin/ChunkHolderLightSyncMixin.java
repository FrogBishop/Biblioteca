package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.world.LoopingDimensionManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
//? if <1.20 {
/*import net.minecraft.network.protocol.Packet;
*///?}

/** Periodic sky is server-authoritative, so interior watchers need the native light packets too. */
@Mixin(ChunkHolder.class)
public abstract class ChunkHolderLightSyncMixin {
    //? if >=1.20 {
    @WrapOperation(method = "broadcastChanges", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkHolder$PlayerProvider;getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;",
            ordinal = 0))
    private List<ServerPlayer> biblioteca$syncInteriorLight(
            ChunkHolder.PlayerProvider provider, ChunkPos pos, boolean borderOnly,
            Operation<List<ServerPlayer>> original, @Local(argsOnly = true) LevelChunk chunk) {
        return original.call(provider, pos, biblioteca$borderOnly(borderOnly, chunk));
    }
    //?} else {
    /*@WrapOperation(method = "broadcastChanges", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkHolder;broadcast(Lnet/minecraft/network/protocol/Packet;Z)V",
            ordinal = 0))
    private void biblioteca$syncInteriorLight(
            ChunkHolder holder, Packet<?> packet, boolean borderOnly,
            Operation<Void> original, @Local(argsOnly = true) LevelChunk chunk) {
        original.call(holder, packet, biblioteca$borderOnly(borderOnly, chunk));
    }
    *///?}

    @org.spongepowered.asm.mixin.Unique
    private static boolean biblioteca$borderOnly(boolean borderOnly, LevelChunk chunk) {
        var level = chunk.getLevel();
        boolean authoritative = LoopingRegistry.isLooping(level)
                || LoopingDimensionManager.needsServerLightSync(level.dimension());
        return borderOnly && !authoritative;
    }
}
