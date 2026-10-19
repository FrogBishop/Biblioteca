package babel.biblioteca.client.mixin;

import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the option list a vanilla options screen builds its rows into.
 *
 * The field is declared on OptionsSubScreen, so a @Shadow in a mixin targeting one of its
 * subclasses cannot reach it - @Shadow only resolves members declared in the target class itself.
 */
@Mixin(OptionsSubScreen.class)
public interface OptionsSubScreenAccessor {

    @Accessor("list")
    OptionsList biblioteca$optionsList();
}
