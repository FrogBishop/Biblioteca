package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Canonicalizes a breaking target before the version's Player reach implementation checks it. */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow protected ServerLevel level;

    @WrapOperation(method = "handleBlockBreakAction", at = @At(value = "INVOKE",
            //? if >=1.21.11 {
            target = "Lnet/minecraft/server/level/ServerPlayer;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z"))
            //?} else {
            /*target = "Lnet/minecraft/server/level/ServerPlayer;canInteractWithBlock(Lnet/minecraft/core/BlockPos;D)Z"))
            *///?}
    private boolean biblioteca$canonicalTarget(ServerPlayer player, BlockPos pos, double margin, Operation<Boolean> original) {
        BlockPos target = level != null && LoopingRegistry.isLooping(level) ? SeamPositions.foldBlockPos(level, pos) : pos;
        return original.call(player, target, margin);
    }
}
