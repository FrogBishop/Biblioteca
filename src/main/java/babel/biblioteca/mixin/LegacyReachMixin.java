package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
 * Folds the target of 1.20.4's inline block-breaking reach check. Newer releases
 * route reach through the Player hooks instead and do not register this mixin.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class LegacyReachMixin {

    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;

    //? if >=1.19 {
    @WrapOperation(
            method = "handleBlockBreakAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$foldEffectCentre(Vec3i pos, Operation<Vec3> original) {
        Vec3 centre = original.call(pos);

        ServerLevel level = this.level;
        if (level == null || !LoopingRegistry.isLooping(level)) return centre;

        int period = Compat.height(level);
        if (period <= 0) return centre;

        double eyeY = this.player.getEyeY();
        double y = centre.y + Math.round((eyeY - centre.y) / period) * (double) period;
        return new Vec3(centre.x, y, centre.z);
    }
    //?} else {
    /*@ModifyExpressionValue(method = "handleBlockBreakAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 0))
    private int biblioteca$nearBreakTarget(int y) {
        return LoopingRegistry.isLooping(this.level)
                ? (int) SeamPositions.foldToward(y, this.player.getY(), Compat.height(this.level)) : y;
    }
    *///?}
}
