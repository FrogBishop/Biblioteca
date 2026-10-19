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
 * Reach checks in 1.20.4 - 1.21.11 compare the eye position with
 * {@code Vec3.atCenterOf(pos)}. Folding that centre to the periodic copy
 * nearest the player makes a block on the far side of the seam reachable
 * without changing the coordinate the rest of the action uses.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class LegacyReachMixin {

    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;

    @WrapOperation(
            method = "handleBlockBreakAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$foldReachCentre(Vec3i pos, Operation<Vec3> original) {
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
