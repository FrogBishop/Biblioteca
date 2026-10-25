package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class LeashRenderStateYLoopMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void biblioteca$nearLeashEnd(Entity entity, EntityRenderState state, float partial, CallbackInfo ci) {
        if (!LoopingRegistry.isLooping(Compat.level(entity))) return;
        //? if >=1.21.6 {
        if (state.leashStates != null) for (var leash : state.leashStates)
            leash.end = SeamPositions.foldToward(Compat.level(entity), leash.end, leash.start.y);
        //?} else {
        /*if (state.leashState != null) state.leashState.end = SeamPositions.foldToward(
                Compat.level(entity), state.leashState.end, state.leashState.start.y);
        *///?}
    }
}
