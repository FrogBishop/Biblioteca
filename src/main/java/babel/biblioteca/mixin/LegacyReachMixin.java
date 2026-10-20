package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Folds the position handed to block-break enchantment effects onto the
 * periodic copy nearest the player.
 *
 * ServerGamePacketListenerMixin folds the packet position to the canonical row,
 * so a break started on the rendered copy of the bottom row is processed at
 * y = -64 while the player stands at the top. Passing the unfurled centre to
 * EnchantmentHelper.onHitBlock would place its effects a whole world height away
 * from the block the player actually hit; this hook moves the centre back to the
 * copy the player can see.
 *
 * The reach check itself is not here: Player.canInteractWithBlock is folded by
 * LegacyBlockReachMixin.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class LegacyReachMixin {

    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;

    @WrapOperation(
            method = "handleBlockBreakAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$foldEffectCentre(Vec3i pos, Operation<Vec3> original) {
        Vec3 centre = original.call(pos);

        ServerLevel level = this.level;
        if (level == null || !LoopingRegistry.isLooping(level)) return centre;

        int period = level.getHeight();
        if (period <= 0) return centre;

        double eyeY = this.player.getEyePosition().y;
        double y = centre.y + Math.round((eyeY - centre.y) / period) * (double) period;
        return new Vec3(centre.x, y, centre.z);
    }
}
