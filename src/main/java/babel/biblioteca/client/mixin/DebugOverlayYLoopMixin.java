package babel.biblioteca.client.mixin;

import babel.biblioteca.client.ClientDiagnostics;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugOverlayYLoopMixin {
    //? if >=1.21.9 {
    @ModifyVariable(
            //? if >=26.1 {
            method = "extractLines",
            //?} else {
            /*method = "renderLines",
            *///?}
            at = @At("HEAD"), argsOnly = true)
    private List<String> biblioteca$debugLines(List<String> lines, @Local(argsOnly = true) boolean alignLeft) {
        return alignLeft ? ClientDiagnostics.append(lines) : lines;
    }
    //?} else {
    /*@Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void biblioteca$debugLines(CallbackInfoReturnable<List<String>> cir) {
        cir.setReturnValue(ClientDiagnostics.append(cir.getReturnValue()));
    }
    *///?}
}
