package babel.biblioteca.client.mixin;

import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the option list a vanilla options screen builds its rows into.
 *
 * The accessor targets the declaring class so video-settings hooks can use the inherited list.
 */
@Mixin(OptionsSubScreen.class)
public interface OptionsSubScreenAccessor {

    @Accessor("list")
    OptionsList biblioteca$optionsList();
}
