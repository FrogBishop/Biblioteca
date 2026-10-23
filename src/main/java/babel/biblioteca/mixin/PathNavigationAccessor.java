package babel.biblioteca.mixin;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the level a navigation object was built for.
 *
 * The field is declared on the navigation base class, so a mixin on a subclass cannot read it
 * through a cast without an accessor.
 */
@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {

    @Accessor("level")
    Level biblioteca$level();
}
