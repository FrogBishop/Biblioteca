package babel.biblioteca.mixin;

import babel.biblioteca.LoopingLight;
import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets block light cross the seam of a Y-looping dimension.
 *
 * The engine walks light outwards one neighbour at a time and looks every neighbour up in the
 * section storage. A neighbour just past a world edge lands in a section that does not exist, so
 * the walk dies at the seam and a torch on one side lights nothing on the other - the light field
 * is cut along with everything else.
 *
 * Folding the neighbour's Y into the world turns that dead end into the row on the other side of
 * the ring. Nothing else in the engine has to change: the neighbour node is in range from then on,
 * so opacity, shape occlusion and the stored level are all looked up normally, and the level is
 * written into the folded cell - which is the cell that block would have had anyway. Propagation
 * around the loop terminates on its own because each step loses at least one level.
 *
 * Applied to both directions of the walk: increases spread a new source, decreases retract one.
 * Sky light retains vanilla behaviour unless no_sky_light explicitly disables its channel.
 */
@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineYLoopMixin {

    @Unique private Level biblioteca$level;

    @WrapOperation(
            method = {"propagateIncrease", "propagateDecrease"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;offset(JLnet/minecraft/core/Direction;)J"))
    private long biblioteca$foldNeighbour(long fromNode, Direction direction, Operation<Long> original) {
        long toNode = original.call(fromNode, direction);

        Level level = this.biblioteca$level;
        if (level == null) {
            // chunkSource lives on LightEngine, so it is read through an accessor there.
            LightChunkGetter chunkSource =
                    ((LightEngineAccessor) (Object) this).biblioteca$chunkSource();
            level = LoopingLight.levelOf(chunkSource);
            if (level == null) return toNode;
            this.biblioteca$level = level;
        }

        if (!LoopingRegistry.isLooping(level)) return toNode;

        return LoopingLight.foldBlockNode(toNode, level);
    }
}
