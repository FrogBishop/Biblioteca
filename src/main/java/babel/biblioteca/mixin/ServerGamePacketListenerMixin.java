package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
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
 * Folds block-action packet positions before range/height validation and compares the hit's
 * fractional location against that canonical block. Movement validation and the crossing
 * statistic are handled separately by ServerMovementYLoopMixin.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow public ServerPlayer player;

    private BlockPos biblioteca$fold(BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
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
        ServerLevel level = (ServerLevel) player.level();
        if (!LoopingRegistry.isLooping(level)) {
            return original.call(loc, center);
        }

        double centerY = center.y;
        double fracOffset = loc.y - Math.floor(loc.y) - 0.5;
        Vec3 foldedLoc = new Vec3(loc.x, centerY + fracOffset, loc.z);
        return original.call(foldedLoc, center);
    }
}
