package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Measures block reach around the ring instead of a straight line.
 *
 * 26.1+ routes the reach check through Player.isWithinBlockInteractionRange. A
 * player standing at the top of a 384-high world who clicks the rendered copy of
 * the bottom row is 384 blocks away from the real row, so the straight-line
 * check would drop the interaction. The answer here is the smallest distance
 * over the copies at y, y + height and y - height, which is the same block in
 * the ring.
 */
@Mixin(Player.class)
public abstract class PlayerReachMixin {

    /**
     * @author biblioteca
     * @reason toroidal Y-distance for Y-looping dimensions
     */
    @Overwrite
    public boolean isWithinBlockInteractionRange(BlockPos pos, double delta) {
        Player self = (Player) (Object) this;
        Level level = self.level();
        double range = self.blockInteractionRange() + delta;

        if (!LoopingRegistry.isLooping(level)) {
            return new AABB(pos).distanceToSqr(self.getEyePosition()) < range * range;
        }

        int height = level.getHeight();
        Vec3 eye   = self.getEyePosition();
        double minDistSq = Math.min(
                new AABB(pos).distanceToSqr(eye),
                Math.min(
                        aabbDistSq(pos, height, eye),
                        aabbDistSq(pos, -height, eye)
                )
        );
        return minDistSq < range * range;
    }

    private static double aabbDistSq(BlockPos pos, int yShift, Vec3 eye) {
        return new AABB(
                pos.getX(), pos.getY() + yShift, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0 + yShift, pos.getZ() + 1.0
        ).distanceToSqr(eye);
    }
}
