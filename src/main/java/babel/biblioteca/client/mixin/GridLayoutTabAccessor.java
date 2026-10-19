package babel.biblioteca.client.mixin;

import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the protected layout of the world-creation tabs so the More tab can gain one row. */
@Mixin(GridLayoutTab.class)
public interface GridLayoutTabAccessor {

    @Accessor("layout")
    GridLayout biblioteca$getLayout();
}
