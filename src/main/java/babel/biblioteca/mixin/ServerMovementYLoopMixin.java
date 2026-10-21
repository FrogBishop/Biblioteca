package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import babel.biblioteca.SeamStats;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Measures movement on the ring before vanilla applies speed/collision validation. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerMovementYLoopMixin {
    @Shadow public ServerPlayer player;

    @ModifyArg(
            //? if >=26.3 {
            method = "handlePlayerPositionChange",
            //?} else {
            /*method = "handleMovePlayer",
            *///?}
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clampVertical(D)D"), index = 0)
    private double biblioteca$foldRequestedPlayerY(double requestedY) {
        if (this.player.isPassenger()) return requestedY;
        return biblioteca$requestedY(requestedY, this.player);
    }

    @ModifyArg(method = "handleMoveVehicle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clampVertical(D)D"), index = 0)
    private double biblioteca$foldRequestedVehicleY(double requestedY) {
        return biblioteca$requestedY(requestedY, this.player.getRootVehicle());
    }

    private double biblioteca$requestedY(double requestedY, Entity reference) {
        Level level = reference.level();
        if (!LoopingRegistry.isLooping(level)) return requestedY;
        double folded = SeamPositions.foldToward(requestedY, reference.getY(), level.getHeight());
        if (folded != requestedY) SeamStats.awardCrossing(this.player);
        return folded;
    }

    @ModifyExpressionValue(
            //? if >=26.3 {
            method = "handlePlayerPositionChange",
            //?} else {
            /*method = "handleMovePlayer",
            *///?}
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;firstGoodY:D"))
    private double biblioteca$nearestPlayerBaseline(double y) {
        return biblioteca$baselineY(y, this.player);
    }

    @ModifyExpressionValue(method = "handleMoveVehicle", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;vehicleFirstGoodY:D"))
    private double biblioteca$nearestVehicleBaseline(double y) {
        return biblioteca$baselineY(y, this.player.getRootVehicle());
    }

    /** More than one packet can arrive in a tick, after the committed position has wrapped. */
    private double biblioteca$baselineY(double y, Entity reference) {
        Level level = reference.level();
        return LoopingRegistry.isLooping(level)
                ? SeamPositions.foldToward(y, reference.getY(), level.getHeight()) : y;
    }
}
