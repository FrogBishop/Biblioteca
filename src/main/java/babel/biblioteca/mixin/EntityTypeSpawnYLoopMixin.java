package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawns entities in the same coordinate frame as the collision query that places them.
 *
 * <p>Spawning on a block at the top row computes the spawn position one row above the world
 * ({@code spawnPos = y = 320}), then asks for the collision shapes under that position to find the
 * surface to stand on. The entity itself was already moved, and that move is folded into the stored
 * period (y = -63), while the block query still runs in the un-folded frame (y = 319..321). The two
 * frames are a whole world height apart, so the surface search finds no usable offset and the
 * entity is finally placed at the *unfolded* spawn row - inside the block it was meant to stand
 * on.
 *
 * <p>Folding the spawn position first puts the initial move, the surface search and the final
 * snapTo in one frame. Folding is a no-op for every position that was already inside the world, so
 * ordinary spawning is untouched.
 *
 * <p>The surface search itself is repeated here. {@code getYOffset} reads the block under the spawn
 * position through the chunk, which answers air for a row outside the world, and it compares that
 * frame with the entity's bounding box, which the move above may have folded. A spawn on top of a
 * block one row below the world top is exactly that case: the spawn position is in range, the
 * entity's temporary row is not, and the search reports no ground, so the entity is placed one row
 * too low and ends up inside the block it was placed on. A query that leaves the world is therefore
 * repeated here, reading the area through the level (which folds) and moving the entity's box into
 * the same frame; anything that stays inside the world is left to vanilla.
 */
@Mixin(EntityType.class)
public abstract class EntityTypeSpawnYLoopMixin {

    @ModifyVariable(
            method = "create(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/world/entity/PostSpawnProcessor;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/entity/EntitySpawnReason;ZZ)"
                    + "Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$foldSpawnPosition(
            BlockPos spawnPos, @Local(argsOnly = true) ServerLevel level) {
        return LoopingRegistry.isLooping(level) ? SeamPositions.foldBlockPos(level, spawnPos) : spawnPos;
    }

    @Inject(
            method = "getYOffset(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z"
                    + "Lnet/minecraft/world/phys/AABB;)D",
            at = @At("HEAD"), cancellable = true)
    private static void biblioteca$ringYOffset(
            LevelReader level, BlockPos spawnPos, boolean movedUp, AABB entityBox,
            CallbackInfoReturnable<Double> cir) {

        if (!(level instanceof Level realLevel) || !LoopingRegistry.isLooping(realLevel)) return;

        int period = level.getHeight();
        if (period <= 0) return;

        AABB area = new AABB(spawnPos);
        if (movedUp) area = area.expandTowards(0.0, -1.0, 0.0);

        // Only a query that leaves the world needs the ring; everything else runs vanilla.
        if (area.minY >= level.getMinY() && area.maxY <= level.getMaxY()) return;

        // The entity's box may already be folded by the move that preceded this query. Put it back
        // in the spawn position's frame so the estimate is measured in one coordinate window.
        long layers = Math.round((spawnPos.getY() - entityBox.getCenter().y) / (double) period);
        AABB box = layers == 0L ? entityBox : entityBox.move(0.0, (double) layers * period, 0.0);

        List<VoxelShape> shapes = new ArrayList<>(4);
        shapes.addAll(realLevel.getEntityCollisions(null, area));
        for (BlockPos pos : BlockPos.betweenClosed(area)) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                shapes.add(shape.move(pos.getX(), pos.getY(), pos.getZ()));
            }
        }

        double offset = Shapes.collide(Direction.Axis.Y, box, shapes, movedUp ? -2.0 : -1.0);
        cir.setReturnValue(1.0 + offset);
    }
}
