package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingLight;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.LoopingSkyLight;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.SeamPositions;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.phys.shapes.Shapes;
import org.apache.commons.lang3.mutable.MutableInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The pre-1.20 graph stores inverse light levels. Keep that scheduler and give it periodic edges. */
@Mixin(SkyLightEngine.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class LegacySkyLightEngineYLoopMixin extends LayerLightEngine implements LoopingSkyLight {
    @Unique private final LongSet biblioteca$columns = new LongOpenHashSet();
    @Unique private final LongSet biblioteca$rebuild = new LongOpenHashSet();
    @Unique private final LongSet biblioteca$blocks = new LongOpenHashSet();
    @Unique private final LongSet biblioteca$nonempty = new LongOpenHashSet();
    @Unique private final Long2ObjectMap<int[]> biblioteca$sources = new Long2ObjectOpenHashMap<>();
    @Unique private volatile boolean biblioteca$periodic, biblioteca$pending;
    @Unique private volatile SkyLightMode biblioteca$mode = SkyLightMode.DEFAULT;
    @Unique private Level biblioteca$level;

    protected LegacySkyLightEngineYLoopMixin(LightChunkGetter source, LightLayer layer, LayerLightSectionStorage data) {
        super(source, layer, data);
    }
    @Unique private Level biblioteca$level() {
        if (biblioteca$level == null) biblioteca$level = LoopingLight.levelOf(chunkSource);
        return biblioteca$level;
    }
    @Unique private boolean biblioteca$looping() { return biblioteca$level() != null && LoopingRegistry.isLooping(biblioteca$level()); }
    @Unique private LightStorageAccessor biblioteca$data() { return (LightStorageAccessor)storage; }
    @Unique private long biblioteca$fold(long node) { return LoopingLight.foldBlockNode(node, biblioteca$level()); }
    @Unique private long biblioteca$chunk(long node) { return Compat.chunkKey(BlockPos.getX(node) >> 4, BlockPos.getZ(node) >> 4); }
    @Unique private boolean biblioteca$active(long node) {
        int y = BlockPos.getY(node);
        return y >= Compat.minY(biblioteca$level()) && y <= Compat.maxY(biblioteca$level())
                && biblioteca$columns.contains(biblioteca$chunk(node)) && biblioteca$data().biblioteca$stores(SectionPos.blockToSection(node));
    }
    @Override public boolean biblioteca$hasSkyWork() {
        boolean looping = biblioteca$looping();
        return biblioteca$pending || looping != biblioteca$periodic || looping && biblioteca$mode != LoopingRegistry.skyLightMode(biblioteca$level());
    }
    @Override public boolean biblioteca$skySectionEmpty(SectionPos section, boolean empty) {
        if (empty) biblioteca$nonempty.remove(section.asLong()); else biblioteca$nonempty.add(section.asLong());
        return biblioteca$looping() && biblioteca$columns.contains(Compat.chunkKey(section.x(), section.z())) ? false : empty;
    }

    @Override public void enableLightSources(ChunkPos pos, boolean enabled) {
        super.enableLightSources(pos, enabled);
        long key = Compat.chunkKey(pos);
        if (!enabled) {
            boolean tracked = biblioteca$columns.remove(key);
            biblioteca$rebuild.remove(key); biblioteca$sources.remove(key);
            if (tracked && (biblioteca$periodic || biblioteca$looping())) biblioteca$sections(pos, true, true);
            if (tracked) for (int y = Compat.minSection(biblioteca$level()); y <= Compat.maxSection(biblioteca$level()); y++)
                biblioteca$nonempty.remove(SectionPos.asLong(Compat.chunkX(pos), y, Compat.chunkZ(pos)));
        } else {
            boolean added = biblioteca$columns.add(key);
            if (biblioteca$looping()) {
                biblioteca$sections(pos, false, false);
                if (added) { biblioteca$rebuild.add(key); biblioteca$pending = true; }
            }
        }
    }
    @Override public void checkBlock(BlockPos pos) {
        if (!biblioteca$looping()) { super.checkBlock(pos); return; }
        if (!biblioteca$level().isClientSide()) { biblioteca$blocks.add(biblioteca$fold(pos.asLong())); biblioteca$pending = true; }
    }
    @Unique private void biblioteca$sections(ChunkPos pos, boolean restore, boolean unload) {
        for (int y = Compat.minSection(biblioteca$level()); y <= Compat.maxSection(biblioteca$level()); y++) {
            long section = SectionPos.asLong(Compat.chunkX(pos), y, Compat.chunkZ(pos));
            biblioteca$data().biblioteca$sectionStatus(section, unload || restore && !biblioteca$nonempty.contains(section));
        }
    }

    @Override public void biblioteca$prepareSkyUpdates() {
        Level level = biblioteca$level(); if (level == null) return;
        boolean looping = biblioteca$looping(); SkyLightMode mode = LoopingRegistry.skyLightMode(level);
        boolean changed = looping != biblioteca$periodic || looping && mode != biblioteca$mode;
        if (!changed && !biblioteca$pending) return;
        biblioteca$periodic = looping; biblioteca$mode = mode;
        if (changed) {
            removeIf(node -> true);
            biblioteca$rebuild.addAll(biblioteca$columns); biblioteca$sources.clear();
            for (long key : biblioteca$columns) biblioteca$sections(Compat.chunkPos(key), !looping, false);
        }
        biblioteca$data().biblioteca$allocateSections();
        if (!level.isClientSide()) {
            removeIf(node -> biblioteca$rebuild.contains(biblioteca$chunk(node)));
            for (long key : biblioteca$rebuild) biblioteca$clear(Compat.chunkPos(key));
            if (looping) {
                for (long key : biblioteca$rebuild) {
                    ChunkPos pos = Compat.chunkPos(key); int[] sources = new int[256];
                    for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) sources[x + z * 16] = biblioteca$findSource(Compat.chunkBlockX(pos, x), Compat.chunkBlockZ(pos, z));
                    biblioteca$sources.put(key, sources);
                    biblioteca$fillSources(pos, sources);
                }
                for (long key : biblioteca$rebuild) biblioteca$seedChunk(Compat.chunkPos(key));
                LongSet checkedColumns = new LongOpenHashSet();
                for (long node : biblioteca$blocks) {
                    if (!biblioteca$active(node)) continue;
                    int x = BlockPos.getX(node), z = BlockPos.getZ(node);
                    if (checkedColumns.add(Compat.chunkKey(x, z))) biblioteca$refreshColumn(x, z);
                    super.checkNode(node);
                    for (Direction direction : Direction.values()) {
                        long neighbor = biblioteca$fold(BlockPos.offset(node, direction));
                        if (biblioteca$active(neighbor)) super.checkNode(neighbor);
                    }
                }
            }
        }
        if (changed && !looping) for (long key : biblioteca$columns) {
            ChunkPos pos = Compat.chunkPos(key); super.enableLightSources(pos, false); super.enableLightSources(pos, true);
        }
        biblioteca$rebuild.clear(); biblioteca$blocks.clear(); biblioteca$pending = false;
    }
    @Unique private void biblioteca$clear(ChunkPos pos) {
        for (int y = Compat.minSection(biblioteca$level()); y <= Compat.maxSection(biblioteca$level()); y++) {
            long section = SectionPos.asLong(Compat.chunkX(pos), y, Compat.chunkZ(pos));
            biblioteca$data().biblioteca$queue(section, null);
            DataLayer layer = biblioteca$data().biblioteca$writable(section);
            if (layer != null) {
                if (!layer.isEmpty()) java.util.Arrays.fill(layer.getData(), (byte)0);
                biblioteca$data().biblioteca$dirty(section);
            }
        }
    }
    @Unique private void biblioteca$fillSources(ChunkPos pos, int[] sources) {
        for (int sy = Compat.minSection(biblioteca$level()); sy <= Compat.maxSection(biblioteca$level()); sy++) {
            int min = sy * 16, full = 0; boolean any = false;
            for (int value : sources) { if (value <= min) full++; if (value < min + 16) any = true; }
            if (!any) continue;
            long section = SectionPos.asLong(Compat.chunkX(pos), sy, Compat.chunkZ(pos));
            DataLayer data = biblioteca$data().biblioteca$writable(section); if (data == null) continue;
            if (full == 256) java.util.Arrays.fill(data.getData(), (byte)0xff);
            else for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                for (int y = Math.max(0, sources[x + z * 16] - min); y < 16; y++) data.set(x, y, z, 15);
            biblioteca$data().biblioteca$dirty(section);
        }
    }
    @Unique private int biblioteca$findSource(int x, int z) {
        Level level = biblioteca$level(); int min = Compat.minY(level), top = Compat.maxY(level) + 1;
        if (biblioteca$mode == SkyLightMode.DISABLED) return top;
        BlockGetter chunk = chunkSource.getChunkForLighting(x >> 4, z >> 4); if (chunk == null) return top;
        BlockState above = Blocks.AIR.defaultBlockState(); BlockPos abovePos = new BlockPos(x, top, z);
        for (int y = top - 1; y >= min; y--) {
            BlockPos pos = new BlockPos(x, y, z); BlockState state = chunk.getBlockState(pos);
            int opacity = getLightBlockInto(level, above, abovePos, state, pos, Direction.DOWN, state.getLightBlock(level, pos));
            if (opacity > 0) return biblioteca$mode == SkyLightMode.HYBRID ? y + 1 : top;
            above = state; abovePos = pos;
        }
        if (biblioteca$mode == SkyLightMode.LOOPING) {
            BlockPos last = new BlockPos(x, top - 1, z);
            if (getLightBlockInto(level, above, abovePos, chunk.getBlockState(last), last, Direction.DOWN, 0) > 0) return top;
        }
        return min;
    }
    @Unique private int biblioteca$sourceMin(int x, int z) {
        int[] sources = biblioteca$sources.get(Compat.chunkKey(x >> 4, z >> 4));
        return sources == null ? Compat.maxY(biblioteca$level()) + 1 : sources[(x & 15) + (z & 15) * 16];
    }
    @Unique private void biblioteca$refreshColumn(int x, int z) {
        int[] sources = biblioteca$sources.get(Compat.chunkKey(x >> 4, z >> 4)); if (sources == null) return;
        int index = (x & 15) + (z & 15) * 16, old = sources[index], next = biblioteca$findSource(x, z);
        if (old == next) return;
        sources[index] = next;
        for (int y = Math.min(old, next); y < Math.max(old, next); y++) super.checkNode(BlockPos.asLong(x, y, z));
    }
    @Unique private void biblioteca$seedColumn(int x, int z) {
        int min = biblioteca$sourceMin(x, z), top = Compat.maxY(biblioteca$level()) + 1; if (min >= top) return;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int nx = x + direction.getStepX(), nz = z + direction.getStepZ();
            if (!biblioteca$columns.contains(Compat.chunkKey(nx >> 4, nz >> 4))) continue;
            for (int y = min; y < biblioteca$sourceMin(nx, nz); y++) biblioteca$seedEdge(BlockPos.asLong(x, y, z), BlockPos.asLong(nx, y, nz), 0);
        }
        if (min > Compat.minY(biblioteca$level())) {
            biblioteca$seedEdge(BlockPos.asLong(x, min, z), BlockPos.asLong(x, min - 1, z), 0);
            biblioteca$seedEdge(BlockPos.asLong(x, top - 1, z), BlockPos.asLong(x, Compat.minY(biblioteca$level()), z), 0);
        }
    }
    @Unique private void biblioteca$seedChunk(ChunkPos pos) {
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int bx = Compat.chunkBlockX(pos, x), bz = Compat.chunkBlockZ(pos, z); biblioteca$seedColumn(bx, bz);
            if (x != 0 && x != 15 && z != 0 && z != 15) continue;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int nx = bx + direction.getStepX(), nz = bz + direction.getStepZ();
                if (nx >> 4 == Compat.chunkX(pos) && nz >> 4 == Compat.chunkZ(pos)) continue;
                for (int y = Compat.minY(biblioteca$level()); y <= Compat.maxY(biblioteca$level()); y++) {
                    long neighbor = BlockPos.asLong(nx, y, nz); if (!biblioteca$active(neighbor)) continue;
                    super.checkNode(neighbor);
                    biblioteca$seedEdge(neighbor, BlockPos.asLong(bx, y, bz), 15 - biblioteca$data().biblioteca$get(neighbor));
                }
            }
        }
    }
    @Unique private void biblioteca$seedEdge(long from, long to, int level) {
        if (!biblioteca$active(from) || !biblioteca$active(to)) return;
        // checkNeighbor is internal to runUpdates and does not publish the graph's hasWork flag.
        // New sources and loaded borders must enter through checkEdge so the scheduler sees them.
        checkEdge(from, to, computeLevelFromNeighbor(from, to, level), true);
    }

    @Inject(method = "computeLevelFromNeighbor", at = @At("HEAD"), cancellable = true)
    private void biblioteca$periodicEdge(long from, long to, int level, CallbackInfoReturnable<Integer> cir) {
        if (!biblioteca$looping()) return;
        if (!biblioteca$active(to)) { cir.setReturnValue(15); return; }
        if (biblioteca$level().isClientSide()) { cir.setReturnValue(15 - biblioteca$data().biblioteca$get(to)); return; }
        if (from == Long.MAX_VALUE) { cir.setReturnValue(BlockPos.getY(to) >= biblioteca$sourceMin(BlockPos.getX(to), BlockPos.getZ(to)) ? 0 : 15); return; }
        if (!biblioteca$active(from) || level >= 15) { cir.setReturnValue(15); return; }
        MutableInt opacity = new MutableInt(); BlockState target = getStateAndOpacity(to, opacity);
        if (opacity.intValue() >= 15) { cir.setReturnValue(15); return; }
        int dy = (int)SeamPositions.foldForDistance(biblioteca$level(), BlockPos.getY(to), BlockPos.getY(from)) - BlockPos.getY(from);
        Direction direction = Direction.fromNormal(Integer.signum(BlockPos.getX(to) - BlockPos.getX(from)), Integer.signum(dy), Integer.signum(BlockPos.getZ(to) - BlockPos.getZ(from)));
        BlockState source = getStateAndOpacity(from, null);
        boolean blocked = direction != null && Shapes.faceShapeOccludes(getShape(source, from, direction), getShape(target, to, direction.getOpposite()));
        cir.setReturnValue(blocked ? 15 : Math.min(15, level + Math.max(1, opacity.intValue())));
    }
    @Inject(method = "getComputedLevel", at = @At("HEAD"), cancellable = true)
    private void biblioteca$periodicValue(long node, long excluded, int limit, CallbackInfoReturnable<Integer> cir) {
        if (!biblioteca$looping()) return;
        if (!biblioteca$active(node)) { cir.setReturnValue(15); return; }
        if (biblioteca$level().isClientSide()) { cir.setReturnValue(15 - biblioteca$data().biblioteca$get(node)); return; }
        int value = Math.min(limit, computeLevelFromNeighbor(Long.MAX_VALUE, node, 0));
        for (Direction direction : Direction.values()) {
            long neighbor = biblioteca$fold(BlockPos.offset(node, direction));
            if (neighbor != excluded && biblioteca$active(neighbor)) value = Math.min(value, computeLevelFromNeighbor(neighbor, node, 15 - biblioteca$data().biblioteca$get(neighbor)));
        }
        cir.setReturnValue(value);
    }
    @Inject(method = "checkNeighborsAfterUpdate", at = @At("HEAD"), cancellable = true)
    private void biblioteca$periodicNeighbors(long node, int level, boolean decreasing, CallbackInfo ci) {
        if (!biblioteca$looping()) return; ci.cancel();
        if (biblioteca$level().isClientSide() || !biblioteca$active(node)) return;
        for (Direction direction : Direction.values()) {
            long neighbor = biblioteca$fold(BlockPos.offset(node, direction));
            if (biblioteca$active(neighbor)) checkNeighbor(node, neighbor, level, decreasing);
        }
    }
}
