package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Folds out-of-bounds Y coordinates in incoming player action and block-placement packets, so the
 * server's range and height checks see the row the block actually lives at.
 *
 * The client legitimately names rows beyond the world here: it clicks the block it can see, and the
 * renderer draws the row across the seam where it belongs. Folding at the checks keeps those
 * packets valid without asking the client to send a coordinate it never displayed.
 *
 * Reach is the other half. Once the position is folded, a distance check against the player is a
 * whole world height off, so each era folds the reach target back to the copy the player clicked:
 * LegacyBlockReachMixin on 1.20.5 - 1.21.10, PlayerReachMixin from 1.21.11.
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

