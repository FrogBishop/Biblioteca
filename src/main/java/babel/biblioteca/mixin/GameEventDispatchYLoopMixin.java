package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamGameEvents;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameEventDispatcher.class)
public abstract class GameEventDispatchYLoopMixin {
    @Shadow @Final private ServerLevel level;

    @WrapMethod(method = "post")
    private void biblioteca$periodicDispatch(
            //? if >=1.20.5 {
            Holder<GameEvent> event,
            //?} else {
            /*GameEvent event,
            *///?}
            Vec3 source, GameEvent.Context context, Operation<Void> original) {
        SeamGameEvents.dispatch(level, () -> original.call(event, source, context));
    }

    @WrapOperation(method = "post", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getListenerRegistry(I)Lnet/minecraft/world/level/gameevent/GameEventListenerRegistry;"))
    private GameEventListenerRegistry biblioteca$canonicalRegistry(ChunkAccess chunk, int section,
            Operation<GameEventListenerRegistry> original) {
        return SeamGameEvents.once(level, original.call(chunk, SeamGameEvents.section(level, section)), GameEventListenerRegistry.NOOP);
    }

    // Match the exact listener call in both the immediate callback and distance-sorted delivery.
    // Synthetic callback names differ between Fabric mappings and NeoForge's recompiled game.
    @WrapOperation(method = "*", at = @At(value = "INVOKE",
            //? if >=1.20.5 {
            target = "Lnet/minecraft/world/level/gameevent/GameEventListener;handleGameEvent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;)Z"))
            //?} else {
            /*target = "Lnet/minecraft/world/level/gameevent/GameEventListener;handleGameEvent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;)Z"))
            *///?}
    private boolean biblioteca$sourceBesideListener(GameEventListener listener, ServerLevel world,
            //? if >=1.20.5 {
            Holder<GameEvent> event,
            //?} else {
            /*GameEvent event,
            *///?}
            GameEvent.Context context, Vec3 source, Operation<Boolean> original) {
        if (LoopingRegistry.isLooping(world)) {
            var destination = listener.getListenerSource().getPosition(world);
            if (destination.isPresent()) source = SeamPositions.foldToward(world, source, destination.get().y);
        }
        return original.call(listener, world, event, context, source);
    }
}
