package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PostSpawnProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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
}
