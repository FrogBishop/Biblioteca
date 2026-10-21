package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Measures distances on the ring.
 *
 * <p>This is the root of a whole family of bugs, not the fix for one. Anything that asks "how far
 * away is that" uses one of these three methods, and every answer is a straight line through the
 * world. Across the boundary the straight line is a whole world height long while the ring puts
 * the two things next to each other, so:
 *
 * <ul>
 *   <li>a mob whose player crossed the boundary counts as abandoned and is despawned;</li>
 *   <li>{@code getNearestPlayer} can pick a player on the far side of the boundary as the nearest
 *       one, and misses the one standing beside the mob;</li>
 *   <li>AI goals - following, tempting, breeding, attacking - all reject what they cannot reach
 *       because their distance checks fail by exactly one period;</li>
 *   <li>leashes break at a distance the ring does not have.</li>
 * </ul>
 *
 * <p>The fix is to compare with the copy of the other point nearest this entity, which is the same
 * "nearest copy" rule used for packets and rendering. It is a no-op for any two points that are
 * already on the same side, including every query in a dimension that does not loop.
 *
 * <p>Wide search boxes are a different matter: those are built in absolute coordinates by the
 * caller and are not corrected here, only the distance that filters their results.
 */
@Mixin(Entity.class)
public abstract class EntityDistanceYLoopMixin {

    @ModifyVariable(method = "distanceToSqr(DDD)D", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double biblioteca$foldDistanceY(double y) {
        Level level = ((Entity) (Object) this).level();
        if (level == null || !LoopingRegistry.isLooping(level)) return y;

        return SeamPositions.foldForDistance(level, y, ((Entity) (Object) this).getY());
    }

    @ModifyVariable(
            method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3 biblioteca$foldDistancePosition(Vec3 position) {
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (position == null || level == null || !LoopingRegistry.isLooping(level)) return position;

        double y = SeamPositions.foldForDistance(level, position.y, self.getY());
        return y == position.y ? position : new Vec3(position.x, y, position.z);
    }

    /**
     * The float distance only reads the other entity's row, so that one call is folded. It is the
     * second {@code getY()} in the method: the first is the observer's own.
     */
    @ModifyExpressionValue(
            method = "distanceTo(Lnet/minecraft/world/entity/Entity;)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D", ordinal = 1))
    private double biblioteca$foldOtherDistanceY(double otherY) {
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (level == null || !LoopingRegistry.isLooping(level)) return otherY;

        return SeamPositions.foldForDistance(level, otherY, self.getY());
    }
}
