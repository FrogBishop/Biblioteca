package babel.biblioteca;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;

/**
 * Coordinate folding for the light engine.
 *
 * The light engine works on packed block nodes, and it addresses everything through them: the
 * propagation step asks for a neighbour node, then looks that node up in the section storage. A
 * node just past a world edge therefore reads a section that does not exist, and propagation
 * stops there - which is why the light field is cut in half at the seam.
 *
 * Folding a node's Y into the world turns that dead end into the row on the other side of the
 * ring, and nothing else in the engine has to change: the level written into the folded cell is
 * the level that block would have received anyway, so light simply wraps.
 *
 * The fold is always a multiple of 16 (world heights are whole sections), so the low four bits of
 * Y - the index inside the section - are unchanged by it.
 */
public final class LoopingLight {

    private LoopingLight() {}

    /** The level behind a light engine's chunk source, or null when it cannot be resolved. */
    public static Level levelOf(LightChunkGetter chunkSource) {
        return chunkSource.getLevel() instanceof Level level ? level : null;
    }

    /** Folds a packed block node's Y into this level's rows. Returns it unchanged when in range. */
    public static long foldBlockNode(long blockNode, Level level) {
        int minY   = level.getMinY();
        int height = level.getHeight();
        int y      = BlockPos.getY(blockNode);
        if (y >= minY && y < minY + height) return blockNode;

        return BlockPos.asLong(
                BlockPos.getX(blockNode),
                Math.floorMod(y - minY, height) + minY,
                BlockPos.getZ(blockNode));
    }

    /**
     * Folds a block position's Y into this level's rows. Returns the same instance when the
     * position is already inside the world, so the in-range path allocates nothing.
     */
    public static BlockPos foldBlockPos(BlockPos pos, Level level) {
        int minY   = level.getMinY();
        int height = level.getHeight();
        int y      = pos.getY();
        if (y >= minY && y < minY + height) return pos;

        return new BlockPos(pos.getX(), Math.floorMod(y - minY, height) + minY, pos.getZ());
    }
}
