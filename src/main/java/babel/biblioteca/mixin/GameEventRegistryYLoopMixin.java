package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.PositionSource;
//? if >=1.19.3 {
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
//?} else {
/*import net.minecraft.world.level.gameevent.EuclideanGameEventDispatcher;
*///?}
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Optional;

//? if >=1.19.3 {
@Mixin(EuclideanGameEventListenerRegistry.class)
//?} else {
/*@Mixin(EuclideanGameEventDispatcher.class)
*///?}
public abstract class GameEventRegistryYLoopMixin {
    /** The native range filter and distance ordering use the same nearby listener image. */
    @WrapOperation(method = "getPostableListenerPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/gameevent/PositionSource;getPosition(Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private static Optional<Vec3> biblioteca$nearbyListener(PositionSource position, Level level,
            Operation<Optional<Vec3>> original, @Local(argsOnly = true) Vec3 source) {
        Optional<Vec3> result = original.call(position, level);
        return LoopingRegistry.isLooping(level) ? result.map(p -> SeamPositions.foldToward(level, p, source.y)) : result;
    }
}
