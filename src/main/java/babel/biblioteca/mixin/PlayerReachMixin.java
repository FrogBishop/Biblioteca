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
 * Replaces isWithinBlockInteractionRange to use toroidal Y-distance in looping
 * dimensions.
 *
 * @Overwrite is used because there is no refMap in this project.
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
