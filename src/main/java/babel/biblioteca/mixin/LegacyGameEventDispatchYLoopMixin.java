package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamGameEvents;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

//? if >=1.19 {
@Mixin(ServerLevel.class)
//?} else {
/*@Mixin(Level.class)
*///?}
public abstract class LegacyGameEventDispatchYLoopMixin {
    //? if >=1.19 {
    @WrapMethod(method = "gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V")
    private void biblioteca$periodicDispatch(GameEvent event, Vec3 source, GameEvent.Context context, Operation<Void> original) {
        SeamGameEvents.dispatch((Level) (Object) this, () -> original.call(event, source, context));
    }
    //?} else {
    /*@WrapMethod(method = "postGameEventInRadius")
    private void biblioteca$periodicDispatch(Entity entity, GameEvent event, BlockPos source, int radius, Operation<Void> original) {
        SeamGameEvents.dispatch((Level) (Object) this, () -> original.call(entity, event, source, radius));
    }
    *///?}

    //? if >=1.19 {
    @WrapOperation(method = "gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
    //?} else {
    /*@WrapOperation(method = "postGameEventInRadius",
    *///?}
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getEventDispatcher(I)Lnet/minecraft/world/level/gameevent/GameEventDispatcher;"))
    private GameEventDispatcher biblioteca$canonicalRegistry(ChunkAccess chunk, int section, Operation<GameEventDispatcher> original) {
        Level level = (Level) (Object) this;
        return SeamGameEvents.once(level, original.call(chunk, SeamGameEvents.section(level, section)), GameEventDispatcher.NOOP);
    }

    //? if >=1.19 {
    @ModifyArgs(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/gameevent/GameEvent$Message;<init>(Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/level/gameevent/GameEventListener;Lnet/minecraft/world/phys/Vec3;)V"))
    private void biblioteca$queuedSourceBesideListener(Args args) {
        Level level = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(level)) return;
        Vec3 source = args.get(1), listener = args.get(4);
        Vec3 canonical = new Vec3(listener.x, SeamPositions.foldY(level, listener.y), listener.z);
        args.set(1, SeamPositions.foldToward(level, source, canonical.y));
        args.set(4, canonical);
    }
    //?}
}
