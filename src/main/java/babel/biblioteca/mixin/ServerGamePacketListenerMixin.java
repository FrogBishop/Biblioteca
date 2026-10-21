package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
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
 * Makes incoming player packets agree with the ring.
 *
 * The client legitimately names rows beyond the world: it clicks the block it can see, and the
 * renderer draws the row across the seam where it belongs. The action and block-placement handlers
 * therefore fold the position before the range and height checks, and the movement handlers fold
 * the requested Y onto the ring copy nearest the player before the "moved too quickly" test.
 * Without that fold a guest crossing the seam would arrive as a whole-period jump and be dragged
 * back; the host only escaped it because vanilla disables the check for the single-player owner.
 *
 * The movement step is also the one place where a seam crossing is observed, so it is where the
 * statistic is awarded. Folding here changes only the coordinate the handler validates - the
 * position that is finally committed is folded independently by EntityYLoopMixin.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow public ServerPlayer player;

    private BlockPos biblioteca$fold(BlockPos pos) {
        ServerLevel level = player.level();
        return LoopingRegistry.isLooping(level) ? SeamPositions.foldBlockPos(level, pos) : pos;
    }

    // --- handlePlayerAction: fold the BlockPos before the distance and height checks ---

    @WrapOperation(
            method = "handlePlayerAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;getPos()Lnet/minecraft/core/BlockPos;",
                    ordinal = 0))
    private BlockPos biblioteca$foldActionPos(
            ServerboundPlayerActionPacket packet, Operation<BlockPos> original) {
        return biblioteca$fold(original.call(packet));
    }

    // --- handleUseItemOn: fold both the BlockPos and the Vec3 hit location ---

    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/BlockHitResult;getBlockPos()Lnet/minecraft/core/BlockPos;",
                    ordinal = 0))
    private BlockPos biblioteca$foldUseItemBlockPos(
            BlockHitResult hit, Operation<BlockPos> original) {
        return biblioteca$fold(original.call(hit));
    }

    // --- handlePlayerPositionChange: fold the requested Y before the movement check ---

    /**
     * Moves the client's requested Y onto the ring copy nearest the player's current row.
     *
     * Crossing the seam changes the committed position by a whole world height in one step: the
     * client commits y = 320.0 and therefore sends the folded y = -64.0 (or the other way round).
     * The server would read that as a 384-block jump.
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

        // "Both rows are inside the world" is NOT a valid fast path here. The client folds its own
        // position before sending, so a crossing arrives as an in-range row on the opposite edge, a
        // whole period away from the player's current row. Skipping that case both dropped the
        // statistic and left the movement check with the full-period jump.
        long layer = SeamPositions.layer(requestedY, player.getY(), period);
        if (layer == 0L) return requestedY;

        SeamStats.awardCrossing(player);
        return requestedY + (double) layer * period;
    }

    // --- handleMoveVehicle: the same fold for the vehicle position packet ---

    /**
     * Folds the vehicle's requested position the same way as the player's.
     *
     * A guest riding through the seam has the identical whole-period jump in this packet, and the
     * vehicle branch of the movement check carries the same single-player-owner exemption.
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

        long layer = SeamPositions.layer(pos.y, referenceY, period);
        if (layer == 0L) return movingTo;

        SeamStats.awardCrossing(player);
        return PositionAndRotation.of(
                new Vec3(pos.x, pos.y + (double) layer * period, pos.z),
                movingTo.yRot(), movingTo.xRot());
    }

    /**
     * Compare the hit's fractional Y against the folded block centre so this validation does not
     * treat a periodic height difference as a distant hit. The packet's hit result is not rewritten.
     */
    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0))
    private Vec3 biblioteca$foldUseItemLocationSubtract(
            Vec3 loc, Vec3 center, Operation<Vec3> original) {
        ServerLevel level = player.level();
        if (!LoopingRegistry.isLooping(level)) {
            return original.call(loc, center);
        }

        double centerY = center.y;
        double fracOffset = loc.y - Math.floor(loc.y) - 0.5;
        Vec3 foldedLoc = new Vec3(loc.x, centerY + fracOffset, loc.z);
        return original.call(foldedLoc, center);
    }
}
