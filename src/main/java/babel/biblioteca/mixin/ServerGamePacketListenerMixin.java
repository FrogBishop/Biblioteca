package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamStats;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.PositionAndRotation;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Folds out-of-bounds Y coordinates in incoming player action and block-placement packets, so the
 * server's range and height checks see the row the block actually lives at.
 *
 * The client legitimately names rows beyond the world here: it clicks the block it can see, and the
 * renderer draws the row across the seam where it belongs. Folding at the checks keeps those
 * packets valid without asking the client to send a coordinate it never displayed.
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
        if (l == null || !LoopingRegistry.isLooping(l)) return pos;
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
        if (l == null || !LoopingRegistry.isLooping(l)) return pos;
        return biblioteca$fold(pos, l);
    }

    // --- handlePlayerPositionChange: fold the requested Y before the movement check ---

    /**
     * Moves the client's requested Y onto the ring copy nearest the player's current row.
     *
     * Crossing the seam changes the committed position by a whole world height in one step: the
     * client commits y = 320.0 and therefore sends the folded y = -64.0 (or the other way round).
     * The server would read that as a 384-block jump. The vanilla "moved too quickly" test is
     * disabled for the single-player owner, which is why the host can cross and guests are
     * teleported back; shifting the packet by whole periods first keeps the delta a normal step
     * on every connection. The position that is committed is still folded into the world by
     * EntityYLoopMixin.
     */
    @ModifyVariable(
            method = "handlePlayerPositionChange",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1)
    private double biblioteca$foldRequestedY(double requestedY) {
        ServerLevel level = player.level();
        if (!LoopingRegistry.isLooping(level)) return requestedY;

        int period = level.getHeight();
        if (period <= 0) return requestedY;

        int minY = level.getMinY();
        double currentY = player.getY();

        // Fast path: both rows are already inside the world, so this packet cannot be a crossing.
        if (requestedY >= minY && requestedY < minY + period
                && currentY >= minY && currentY < minY + period) {
            return requestedY;
        }

        long layer = Math.round((currentY - requestedY) / period);
        if (layer == 0L) return requestedY;

        SeamStats.awardCrossing(player);
        return requestedY + (double) layer * period;
    }

    // --- handleMoveVehicle: same fold for the vehicle position packet ---

    /**
     * Folds the vehicle's requested position the same way as the player's.
     *
     * A guest riding through the seam has the identical 384-block jump in this packet, and the
     * vehicle branch of the movement check carries the same single-player-owner exemption for the
     * host. Folding here keeps the delta one period smaller for every connection; the committed
     * position is folded into the world by EntityYLoopMixin.
     */
    @ModifyExpressionValue(
            method = "handleMoveVehicle",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;"
                            + "movingTo()Lnet/minecraft/core/PositionAndRotation;"))
    private PositionAndRotation biblioteca$foldVehiclePosition(PositionAndRotation movingTo) {
        ServerLevel level = player.level();
        if (!LoopingRegistry.isLooping(level)) return movingTo;

        int period = level.getHeight();
        if (period <= 0) return movingTo;

        Entity vehicle = player.getRootVehicle();
        double referenceY = vehicle != player ? vehicle.getY() : player.getY();
        Vec3 pos = movingTo.position();

        int minY = level.getMinY();
        if (pos.y >= minY && pos.y < minY + period
                && referenceY >= minY && referenceY < minY + period) {
            return movingTo;
        }

        long layer = Math.round((referenceY - pos.y) / period);
        if (layer == 0L) return movingTo;

        SeamStats.awardCrossing(player);
        return PositionAndRotation.of(
                new Vec3(pos.x, pos.y + (double) layer * period, pos.z),
                movingTo.yRot(), movingTo.xRot());
    }

    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0))
    private Vec3 biblioteca$foldUseItemLocationSubtract(
            Vec3 loc, Vec3 center, Operation<Vec3> original) {
        ServerLevel l = player.level() instanceof ServerLevel sl ? sl : null;
        if (l == null || !LoopingRegistry.isLooping(l)) {
            return original.call(loc, center);
        }
        double centerY    = center.y;
        double fracOffset = loc.y - Math.floor(loc.y) - 0.5;
        Vec3 foldedLoc    = new Vec3(loc.x, centerY + fracOffset, loc.z);
        return original.call(foldedLoc, center);
    }
}

