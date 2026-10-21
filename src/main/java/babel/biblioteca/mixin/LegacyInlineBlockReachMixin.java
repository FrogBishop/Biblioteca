package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The two inline placement-range checks used before Player.canInteractWithBlock existed. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LegacyInlineBlockReachMixin {
    @Shadow public ServerPlayer player;

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double biblioteca$periodicEyeReach(Vec3 eye, Vec3 target, Operation<Double> original) {
        if (!LoopingRegistry.isLooping(player.level())) return original.call(eye, target);
        return original.call(eye, new Vec3(target.x,
                SeamPositions.foldToward(target.y, eye.y, player.level().getHeight()), target.z));
    }

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D"))
    private double biblioteca$periodicPlacementRange(ServerPlayer player, double x, double y, double z,
                                                     Operation<Double> original) {
        if (LoopingRegistry.isLooping(player.level())) {
            y = SeamPositions.foldToward(y, player.getY(), player.level().getHeight());
        }
        return original.call(player, x, y, z);
    }
}
