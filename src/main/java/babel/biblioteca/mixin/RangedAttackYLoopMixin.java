package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Fold the raw target-minus-origin difference before vanilla adds ballistic compensation. */
@Mixin({AbstractSkeleton.class, Illusioner.class, Drowned.class, Witch.class})
public abstract class RangedAttackYLoopMixin {
    @ModifyVariable(method = "performRangedAttack", at = @At("STORE"), ordinal = 1)
    private double biblioteca$nearAimDelta(double deltaY) {
        Level level = ((Entity)(Object)this).level();
        return LoopingRegistry.isLooping(level) ? SeamPositions.foldForDistance(level, deltaY, 0) : deltaY;
    }
}
