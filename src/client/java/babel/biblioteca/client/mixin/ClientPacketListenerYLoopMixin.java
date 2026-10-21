package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets remote movement across the seam interpolate like ordinary movement.
 *
 * <p>A movement packet that crosses the boundary names a position a whole world height away from
 * the one the client still shows, because the server stores the folded row. Both packet handlers
 * compare that distance against a threshold and, when it is "too large", skip interpolation and
 * snap the entity instead - which is exactly the single-tick jump a client renders as a stutter.
 * The ring makes that distance one block, so the same threshold is applied to the distance around
 * the ring.
 *
 * <p>The interpolation target itself is folded separately, in {@code EntityInterpolationYLoopMixin};
 * this only fixes the decision whether to interpolate at all.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerYLoopMixin {

    @Shadow private ClientLevel level;

    @WrapOperation(
            method = "handleEntityPositionSync",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double biblioteca$ringSyncDistance(Vec3 from, Vec3 to, Operation<Double> original) {
        return biblioteca$ringDistance(this.level, from, to, original);
    }

    @WrapOperation(
            method = "setValuesFromPositionPacket",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double biblioteca$ringTeleportDistance(
            Vec3 from, Vec3 to, Operation<Double> original,
            @Local(argsOnly = true) Entity entity) {

        return biblioteca$ringDistance(entity.level(), from, to, original);
    }

    private static double biblioteca$ringDistance(
            Level level, Vec3 from, Vec3 to, Operation<Double> original) {

        if (level == null || to == null || !LoopingRegistry.isLooping(level)) {
            return original.call(from, to);
        }

        return original.call(from, SeamPositions.foldToward(level, to, from.y));
    }
}
