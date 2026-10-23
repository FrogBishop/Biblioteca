package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Old living entities consume their stored interpolation coordinate directly in aiStep. */
@Mixin(LivingEntity.class)
public abstract class LegacyLivingInterpolationYLoopMixin {
    @Shadow protected double lerpY;
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void biblioteca$nearTarget(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (LoopingRegistry.isLooping(Compat.level(self)))
            this.lerpY = SeamPositions.foldForDistance(Compat.level(self), this.lerpY, self.getY());
    }
}
