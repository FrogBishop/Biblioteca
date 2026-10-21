package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives both seam faces of a Y-looping dimension a collision the vanilla pass cannot see.
 *
 * Block collisions are not gathered through Level.getBlockState: BlockCollisions reads
 * through a cached ChunkAccess (CollisionGetter.getChunkForCollisions), and a chunk
 * answers AIR for every Y outside the build height. LevelBlockAccessMixin's folding
 * therefore never reaches this path, so the row above the world top and the row below
 * the world bottom are empty. That is what leaves the top-layer block without a top
 * face to stand on, and the bottom layer without ground: the surface at minY and the
 * surface at minY + height are the same plane, but only one of the two copies existed.
 *
 * Folding the Y of the lookup — while leaving the position the shape is placed at
 * alone — makes the out-of-range rows resolve to their periodic equivalents:
 *
 *   y = minY - 1  ->  reads the top row, collision shape still placed at minY - 1,
 *                     which is the floor a player at y = minY stands on;
 *   y = minY + height -> reads the bottom row, shape still placed above the world,
 *                     which is the ceiling/floor a player at minY + height hits.
 *
 * Runs on both sides: collision is part of movement prediction, so the client and the
 * server must agree about it.
 */
@Mixin(BlockCollisions.class)
public abstract class BlockCollisionsYLoopMixin {

    @Shadow @Final private CollisionGetter collisionGetter;

    @WrapOperation(
            method = "computeNext",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState biblioteca$foldCollisionLookup(
            BlockGetter chunk, BlockPos pos, Operation<BlockState> original) {

        if (!(collisionGetter instanceof Level level)) {
            return original.call(chunk, pos);
        }

        // Fast path: the queried row is inside the world, which is almost every block.
        if (SeamPositions.isInside(level, pos.getY())) {
            return original.call(chunk, pos);
        }
        if (!LoopingRegistry.isLooping(level)) {
            return original.call(chunk, pos);
        }

        return original.call(chunk, SeamPositions.foldBlockPos(level, pos));
    }
}
