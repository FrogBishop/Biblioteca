package biblioteca.periodicprobe.mixin;

import biblioteca.periodicprobe.PeriodicVisualChecks;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityRenderDispatcher.class, priority = 900)
public abstract class VisualEntityObserver {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void probe(Entity entity, float partial, CallbackInfoReturnable<EntityRenderState> result) {
        PeriodicVisualChecks.extracted(entity, result.getReturnValue());
    }
}
