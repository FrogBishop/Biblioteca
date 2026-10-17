package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingClientSettings;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the seam preload slider to the video settings screen.
 *
 * It is appended to the option list rather than placed at a fixed position, so it scrolls with the
 * other rows and the screen's layout takes care of it. A save-and-reload is not needed: the value
 * is read every time the far side of the seam is warmed, so dragging the slider takes effect as
 * soon as the next frustum update runs.
 */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin {

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void biblioteca$addPreloadOption(CallbackInfo ci) {
        OptionsList list = ((OptionsSubScreenAccessor) this).biblioteca$optionsList();
        if (list == null) return;

        list.addBig(LoopingClientSettings.createPreloadOption());
    }
}
