package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Direct lookAt is also used by breeze AI; unlike LookControl it bypasses the mob look target. */
@Mixin(Entity.class)
public abstract class EntityLookAtYLoopMixin {
    @ModifyVariable(method = "lookAt", at = @At("HEAD"), argsOnly = true)
    private Vec3 biblioteca$nearLookPoint(Vec3 target, @Local(argsOnly = true) Anchor anchor) {
        Entity self = (Entity)(Object)this;
        return LoopingRegistry.isLooping(Compat.level(self)) ? SeamPositions.foldToward(Compat.level(self), target, anchor.apply(self).y) : target;
    }
}
