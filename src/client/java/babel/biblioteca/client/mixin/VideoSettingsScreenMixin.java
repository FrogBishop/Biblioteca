package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingRenderSettings;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the "Loop Render Range" slider into the Video Settings screen
 * at the tail of the preferences section.
 */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin {

    @Shadow protected OptionsList list;

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void biblioteca$addLoopingRenderRange(CallbackInfo ci) {
        if (list != null) {
            list.addBig(LoopingRenderSettings.option());
        }
    }
}
