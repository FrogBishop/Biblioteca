package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.CreateWorldScreenInjector;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses the row vacated by Game Rules while the classic More World Options pane is open. */
@Mixin(CreateWorldScreen.class)
public abstract class LegacyCreateWorldScreenMixin extends Screen {
    @Shadow private Button gameRulesButton;
    @Unique private Button biblioteca$attributes;
    protected LegacyCreateWorldScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void biblioteca$addAttributes(CallbackInfo ci) {
        this.biblioteca$attributes = ClientCompat.button(Compat.translatable("biblioteca.gui.looping_button"),
                this.width / 2 - 155, 185, 150, 20,
                button -> CreateWorldScreenInjector.openConfig((CreateWorldScreen) (Object) this));
        //? if >=1.17 {
        this.addRenderableWidget(this.biblioteca$attributes);
        //?} else {
        /*this.addButton(this.biblioteca$attributes);
        *///?}
        this.biblioteca$attributes.visible = !this.gameRulesButton.visible;
    }

//? if >=1.17 {
    @Inject(method = "setWorldGenSettingsVisible", at = @At("TAIL"))
//?} else {
/*    @Inject(method = "updateDisplayOptions", at = @At("TAIL"))
*///?}

    private void biblioteca$showAttributes(CallbackInfo ci) {
        if (this.biblioteca$attributes != null) this.biblioteca$attributes.visible = !this.gameRulesButton.visible;
    }
}
