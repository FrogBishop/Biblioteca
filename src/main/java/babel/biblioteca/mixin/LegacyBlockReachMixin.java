package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Measures the reach to the copy of the block nearest the player, not to its
 * canonical row.
 *
 * ServerGamePacketListenerMixin folds an out-of-range packet position onto the
 * canonical row before the handlers run, so the height checks and the block
 * lookups see the row the block really lives at. That is right for the world,
 * and wrong for the distance check: a player standing at the top of a 384-high
 * world and clicking the copied bottom row at y = 320 is compared against the
 * real row at y = -64, 384 blocks away. The interaction was dropped there,
 * before BlockPlaceContext ever saw it, with no message and no block update -
 * which is exactly the "clicking the copied bottom row does nothing" symptom.
 *
 * Shifting by whole periods only changes coordinates that are outside the
 * build range; an in-bounds position is returned untouched, so ordinary
 * interactions are unaffected.
 */
@Mixin(Player.class)
public abstract class LegacyBlockReachMixin {

    @ModifyVariable(method = "canInteractWithBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$nearestCopy(BlockPos pos) {
        Player self = (Player) (Object) this;
        Level level = Compat.level(self);
        if (!LoopingRegistry.isLooping(level)) return pos;

        int period = Compat.height(level);
        if (period <= 0) return pos;

        double eyeY = self.getEyePosition().y;
        long shift = Math.round((eyeY - pos.getY()) / (double) period);
        if (shift == 0L) return pos;

        return new BlockPos(pos.getX(), pos.getY() + (int) (shift * period), pos.getZ());
    }
}
