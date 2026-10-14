package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Folds out-of-bounds Y in block interaction methods BEFORE the prediction
 * system records the position, so the prediction key matches the server
 * confirmation key (both at the folded Y, e.g. y=-64).
 *
 * For placement we also shift the Vec3 hit location by the same delta —
 * withPosition() only replaces blockPos, leaving location at y=320, which
 * makes the server's "location close to blockPos" distance check reject the
 * packet with "too far away".
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow private Minecraft minecraft;

    private BlockPos biblioteca$fold(BlockPos pos, ClientLevel level) {
        int y = pos.getY();
        int minY = level.getMinY();
        int height = level.getHeight();
        if (y >= minY && y < minY + height) return pos;
        return new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
    }

    @ModifyVariable(method = "startDestroyBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldStartDestroyPos(BlockPos pos) {
        ClientLevel level = minecraft.level;
        if (level == null || !LoopingDimensionCache.isLooping(level.dimension())) return pos;
        return biblioteca$fold(pos, level);
    }

    @ModifyVariable(method = "continueDestroyBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldContinueDestroyPos(BlockPos pos) {
        ClientLevel level = minecraft.level;
        if (level == null || !LoopingDimensionCache.isLooping(level.dimension())) return pos;
        return biblioteca$fold(pos, level);
    }

    @ModifyVariable(method = "destroyBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldDestroyBlockPos(BlockPos pos) {
        ClientLevel level = minecraft.level;
        if (level == null || !LoopingDimensionCache.isLooping(level.dimension())) return pos;
        return biblioteca$fold(pos, level);
    }

    @ModifyVariable(method = "useItemOn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockHitResult biblioteca$foldUseItemOnHit(BlockHitResult hit) {
        ClientLevel level = minecraft.level;
        if (level == null || !LoopingDimensionCache.isLooping(level.dimension())) return hit;

        BlockPos pos = hit.getBlockPos();
        BlockPos folded = biblioteca$fold(pos, level);
        if (folded == pos) return hit;

        // Shift the Vec3 hit location by the same integer delta so the server's
        // "location within 1 block of blockPos" check doesn't reject the packet.
        double dy = folded.getY() - pos.getY();
        Vec3 loc = hit.getLocation();
        Vec3 shiftedLoc = new Vec3(loc.x, loc.y + dy, loc.z);

        return new BlockHitResult(shiftedLoc, hit.getDirection(), folded,
                hit.isInside(), hit.isWorldBorderHit());
    }
}

