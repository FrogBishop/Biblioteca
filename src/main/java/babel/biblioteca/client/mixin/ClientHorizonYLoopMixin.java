package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The lower-sky disc has no boundary-relative horizon in a periodic dimension. */
//? if >=1.16 {
@Mixin(ClientLevel.ClientLevelData.class)
//?} else {
/*@Mixin(ClientLevel.class)
*///?}
public abstract class ClientHorizonYLoopMixin {
    @Inject(method = "getHorizonHeight", at = @At("HEAD"), cancellable = true)
    private void biblioteca$noLowerSkyDisc(CallbackInfoReturnable<Double> cir) {
        ClientLevel level = Minecraft.getInstance().level;
        //? if >=1.16 {
        boolean current = level != null && level.getLevelData() == (Object) this;
        //?} else {
        /*boolean current = level == (Object) this;
        *///?}
        if (current && LoopingRegistry.isLooping(level)) {
            cir.setReturnValue(Double.NEGATIVE_INFINITY);
        }
    }
}
