package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import babel.biblioteca.client.ClientCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.VibrationSignalParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Optional;

/** A vibration's destination stays beside its current trajectory in the particle's coordinate frame. */
@Mixin(VibrationSignalParticle.class)
public abstract class VibrationParticleYLoopMixin extends Particle {
    protected VibrationParticleYLoopMixin(ClientLevel level, double x, double y, double z) { super(level, x, y, z); }

    // Before 1.21 the signal draws its own quads instead of calling SingleQuadParticle's helper.
    //? if <1.21 {
    /*@ModifyExpressionValue(method = "renderSignal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;lerp(DDD)D", ordinal = 1))
    private double biblioteca$signalDisplayLayer(double y, @Local(argsOnly = true) Camera camera) {
        return LoopingRegistry.isLooping(this.level)
                ? SeamPositions.foldToward(y, ClientCompat.position(camera).y, Compat.height(this.level)) : y;
    }
    *///?}

    @WrapOperation(method = {"<init>", "tick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/gameevent/PositionSource;getPosition(Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    //? if >=1.19 {
    private Optional<Vec3> biblioteca$nearbyDestination(PositionSource source, Level level, Operation<Optional<Vec3>> original) {
        Optional<Vec3> position = original.call(source, level);
        return LoopingRegistry.isLooping(level) ? position.map(p -> SeamPositions.foldToward(level, p, this.y)) : position;
    }
    //?} else {
    /*private Optional<BlockPos> biblioteca$nearbyDestination(PositionSource source, Level level, Operation<Optional<BlockPos>> original) {
        Optional<BlockPos> position = original.call(source, level);
        return LoopingRegistry.isLooping(level) ? position.map(p -> new BlockPos(p.getX(),
                (int) SeamPositions.foldToward(p.getY(), this.y, Compat.height(level)), p.getZ())) : position;
    }
    *///?}
}
