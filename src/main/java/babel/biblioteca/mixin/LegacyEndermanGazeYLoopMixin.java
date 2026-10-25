package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnderMan.class)
public abstract class LegacyEndermanGazeYLoopMixin {
    @ModifyExpressionValue(method = "isLookingAtMe", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/EnderMan;getEyeY()D"))
    private double biblioteca$nearEye(double y, @Local(argsOnly = true) Player observer) {
        var level = Compat.level((EnderMan)(Object)this);
        return LoopingRegistry.isLooping(level) ? SeamPositions.foldForDistance(level, y, observer.getEyeY()) : y;
    }
}
