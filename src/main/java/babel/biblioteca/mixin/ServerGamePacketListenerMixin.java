package babel.biblioteca.mixin;

import babel.biblioteca.world.LoopingDimensionManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Folds out-of-bounds Y coordinates in incoming player action and block-placement
 * packets before any server-side range or height checks run.
 *
 * Also folds vehicle coordinates after handleMoveVehicle processes them,
 * then sends a ClientboundMoveVehiclePacket to notify the client of the
 * corrected position (same pattern as the player position correction in
 * EntityYLoopMixin).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow public ServerPlayer player;

    private BlockPos biblioteca$fold(BlockPos pos, ServerLevel level) {
        int y      = pos.getY();
        int minY   = level.getMinY();
        int height = level.getHeight();
        if (y >= minY && y < minY + height) return pos;
        return new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
    }

    // --- handlePlayerAction: fold the BlockPos before distance + height checks ---

    @WrapOperation(
            method = "handlePlayerAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;getPos()Lnet/minecraft/core/BlockPos;",
                    ordinal = 0))
    private BlockPos biblioteca$foldActionPos(
            ServerboundPlayerActionPacket packet, Operation<BlockPos> original) {
        BlockPos pos  = original.call(packet);
        ServerLevel l = player.level() instanceof ServerLevel sl ? sl : null;
        if (l == null || !LoopingDimensionManager.isLooping(l.dimension())) return pos;
        return biblioteca$fold(pos, l);
    }

    // --- handleUseItemOn: fold both the BlockPos and the Vec3 hit location ---

    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/BlockHitResult;getBlockPos()Lnet/minecraft/core/BlockPos;",
                    ordinal = 0))
    private BlockPos biblioteca$foldUseItemBlockPos(
            BlockHitResult hit, Operation<BlockPos> original) {
        BlockPos pos  = original.call(hit);
        ServerLevel l = player.level() instanceof ServerLevel sl ? sl : null;
        if (l == null || !LoopingDimensionManager.isLooping(l.dimension())) return pos;
        return biblioteca$fold(pos, l);
    }

    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0))
    private Vec3 biblioteca$foldUseItemLocationSubtract(
            Vec3 loc, Vec3 center, Operation<Vec3> original) {
        ServerLevel l = player.level() instanceof ServerLevel sl ? sl : null;
        if (l == null || !LoopingDimensionManager.isLooping(l.dimension())) {
            return original.call(loc, center);
        }
        double centerY    = center.y;
        double fracOffset = loc.y - Math.floor(loc.y) - 0.5;
        Vec3 foldedLoc    = new Vec3(loc.x, centerY + fracOffset, loc.z);
        return original.call(foldedLoc, center);
    }

    // --- handleMoveVehicle: fold vehicle Y and notify client ---
    //
    // When a player rides a vehicle the client sends ServerboundMoveVehiclePacket
    // every tick.  The server applies the delta via Entity.move() and the result
    // may end up outside [-64, 320).  EntityYLoopMixin folds Entity.tick() TAIL,
    // but that runs one tick later and never sends a corrective packet for the
    // vehicle.  We fold the root vehicle's Y immediately after handleMoveVehicle
    // completes and send ClientboundMoveVehiclePacket so the client snaps back.

    @Inject(method = "handleMoveVehicle", at = @At("TAIL"), require = 0)
    private void biblioteca$foldVehicleY(
            net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket packet,
            CallbackInfo ci) {
        ServerLevel level = player.level() instanceof ServerLevel sl ? sl : null;
        if (level == null) return;
        if (!LoopingDimensionManager.isLooping(level.dimension())) return;

        Entity vehicle = player.getRootVehicle();
        if (vehicle == player) return; // not actually riding anything

        double y      = vehicle.getY();
        int    minY   = level.getMinY();
        int    height = level.getHeight();
        if (y >= minY && y < minY + height) return;

        double floorY  = Math.floor(y);
        double frac    = y - floorY;
        double foldedY = Math.floorMod((int) floorY - minY, height) + minY + frac;

        vehicle.setPos(vehicle.getX(), foldedY, vehicle.getZ());

        // Notify client of corrected vehicle position.
        player.connection.send(ClientboundMoveVehiclePacket.fromEntity(vehicle));
    }
}

