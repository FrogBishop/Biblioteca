package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
        ServerLevel level = (ServerLevel) Compat.level(player);
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
    //? if >=1.18.2 {
    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0))
    private Vec3 biblioteca$foldUseItemLocationSubtract(
            Vec3 loc, Vec3 center, Operation<Vec3> original) {
        ServerLevel level = (ServerLevel) Compat.level(player);
        if (!LoopingRegistry.isLooping(level)) {
            return original.call(loc, center);
        }

        double centerY = center.y;
        double fracOffset = loc.y - Math.floor(loc.y) - 0.5;
        Vec3 foldedLoc = new Vec3(loc.x, centerY + fracOffset, loc.z);
        return original.call(foldedLoc, center);
    }
    //?}

    /**
     * Hands block items the folded block position, not the copy the client clicked.
     *
     * <p>The checks above already fold the position, so a click on a row past the world edge is
     * accepted. The item itself, however, receives the packet's hit result unchanged: a painting or
     * item frame is then built from an out-of-range block, and every item that places a multi-block
     * structure (beds, doors, tall plants, chests, signs) computes its second half from that row.
     * The block writes fold, so the second half lands in the ring cell; but the item's own
     * pre-checks - whether the structure survives, which face it is against - compare the two
     * halves in absolute coordinates and see a whole world height between them.
     *
     * <p>Folding the hit result once, here, puts the item in the same frame as the world. The click
     * location moves with the position so the two stay consistent; both move by whole periods, so
     * the ring cell that was clicked is unchanged.
     */
    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItemOn("
                            + "Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;"
                            + "Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult biblioteca$foldUseItemOnHitResult(
            ServerPlayerGameMode gameMode, ServerPlayer player, Level level, ItemStack stack,
            InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {

        return original.call(gameMode, player, level, stack, hand, biblioteca$foldHitResult(player, hit));
    }

    private BlockHitResult biblioteca$foldHitResult(ServerPlayer player, BlockHitResult hit) {
        ServerLevel level = (ServerLevel) Compat.level(player);
        if (!LoopingRegistry.isLooping(level)) return hit;

        BlockPos raw = hit.getBlockPos();
        BlockPos folded = SeamPositions.foldBlockPos(level, raw);
        if (folded.equals(raw)) return hit;

        double shift = (double) (folded.getY() - raw.getY());
        Vec3 location = hit.getLocation();
        return new BlockHitResult(
                location.add(0.0, shift, 0.0),
                hit.getDirection(),
                folded,
                hit.isInside());
    }
}
