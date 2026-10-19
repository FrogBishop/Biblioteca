package babel.biblioteca.client.mixin;

import babel.biblioteca.client.CreateWorldScreenInjector;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the "Dimension Attributes…" button in the More tab, as the next row of its grid.
 *
 * MoreTab builds its rows through GridLayout.RowHelper: Game Rules, Experiments, Data Packs, one
 * per row, each 210 px wide, with 8 px of row spacing. Appending a fourth child at row 3, column 0
 * makes the new button share that column and spacing, and because it belongs to the tab's layout it
 * is added to the screen only while the More tab is selected - exactly like its neighbours.
 */
@Mixin(targets = "net/minecraft/client/gui/screens/worldselection/CreateWorldScreen$MoreTab")
public abstract class CreateWorldScreenMoreTabMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void biblioteca$addDimensionAttributesButton(CreateWorldScreen parent, CallbackInfo ci) {
        GridLayout layout = ((GridLayoutTabAccessor) (Object) this).biblioteca$getLayout();
        layout.addChild(CreateWorldScreenInjector.createButton(parent), 3, 0);
    }
}