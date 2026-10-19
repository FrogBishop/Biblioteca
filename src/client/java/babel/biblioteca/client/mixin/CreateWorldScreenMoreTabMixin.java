package babel.biblioteca.client.mixin;

import babel.biblioteca.client.CreateWorldScreenInjector;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.Layout;
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
 *
 * The old implementation added a 120 px button to the screen's widget list at fixed footer
 * coordinates through ScreenEvents.AFTER_INIT, which is what left it below the Create/Cancel row
 * and misaligned with everything else.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$MoreTab")
public abstract class CreateWorldScreenMoreTabMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void biblioteca$addDimensionAttributesButton(CallbackInfo ci) {
        Layout layout = ((GridLayoutTab) (Object) this).getLayout();
        if (layout instanceof GridLayout grid) {
            grid.addChild(CreateWorldScreenInjector.createButton(), 3, 0);
        }
    }
}
