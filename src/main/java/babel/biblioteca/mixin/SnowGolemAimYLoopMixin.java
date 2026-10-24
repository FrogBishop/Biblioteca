package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The snowball's muzzle position has already wrapped when the spawn callback computes its aim. */
@Mixin(SnowGolem.class)
public abstract class SnowGolemAimYLoopMixin {
    @ModifyVariable(method = "lambda$performRangedAttack$0", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double biblioteca$nearSnowballTarget(double targetY, @Local(argsOnly = true) Snowball projectile) {
        return LoopingRegistry.isLooping(projectile.level())
                ? SeamPositions.foldForDistance(projectile.level(), targetY, projectile.getY()) : targetY;
    }
}
