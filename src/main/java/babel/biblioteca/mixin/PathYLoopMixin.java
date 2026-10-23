package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Places a path node beside the mob that is walking it.
 *
 * <p>A node keeps the row it was found at, which can be just past the world top while the mob is
 * still on the stored side. Once the mob crosses the seam its own position is folded, and the node
 * is then a whole world height away by plain subtraction: the follower decides it has not arrived,
 * the move control aims at a point 384 blocks away, and the mob stops.
 *
 * <p>The node positions handed to the follower are therefore chosen in the copy nearest the mob,
 * the same way movement packets and render states are. The node indices and the path itself are
 * untouched.
 */
@Mixin(Path.class)
public abstract class PathYLoopMixin {

    @ModifyReturnValue(method = "getEntityPosAtNode", at = @At("RETURN"))
    private Vec3 biblioteca$foldNodeY(Vec3 position, @Local(argsOnly = true) Entity entity) {
        if (position == null || entity == null) return position;

        Level level = Compat.level(entity);
        if (level == null || !LoopingRegistry.isLooping(level)) return position;

        return SeamPositions.foldToward(level, position, entity.getY());
    }
}
