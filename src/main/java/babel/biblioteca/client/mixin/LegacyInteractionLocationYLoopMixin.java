package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Older interaction packets carry a point relative to the target, computed on the client. */
@Mixin(MultiPlayerGameMode.class)
public abstract class LegacyInteractionLocationYLoopMixin {
    @ModifyExpressionValue(method = "interactAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/EntityHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$foldClick(Vec3 point, @Local(argsOnly = true) Entity target) {
        return LoopingRegistry.isLooping(Compat.level(target))
                ? SeamPositions.foldToward(Compat.level(target), point, target.getY() + target.getBbHeight() * 0.5) : point;
    }
}
