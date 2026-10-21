package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Puts the click point of an entity interaction in the target's coordinate frame.
 *
 * <p>The client names the point it clicked on the entity, and it names it near the camera, because
 * that is where the renderer draws the entity: another player or a villager on the far side of the
 * seam is drawn beside the player. The target itself still lives in the stored period, a whole world
 * height away, and interactions that use the point - placing an item in a frame slot, where exactly
 * an armour stand was clicked - compare it with the target's own box. Without this fold the point is
 * 384 blocks off and the interaction either misses or picks the wrong spot.
 *
 * <p>The fold moves the point by whole periods, so the ring position of the click is unchanged.
 */
@Mixin(Player.class)
public abstract class PlayerInteractionLocationYLoopMixin {

    @ModifyVariable(
            method = "interactOn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3 biblioteca$foldInteractionLocation(
            Vec3 location, @Local(argsOnly = true) Entity target) {

        if (location == null || target == null) return location;

        Player self = (Player) (Object) this;
        Level level = self.level();
        if (!LoopingRegistry.isLooping(level)) return location;

        return SeamPositions.foldToward(level, location, target.getY() + (target.getBbHeight() * 0.5));
    }
}
