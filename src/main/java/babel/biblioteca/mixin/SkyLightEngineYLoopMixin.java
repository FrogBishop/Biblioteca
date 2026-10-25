package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingLight;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.LoopingSkyLight;
import babel.biblioteca.SeamPositions;
import babel.biblioteca.SkyLightMode;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Direct sky uses the selected exposure rule: a transparent full circuit, or vanilla's source
 * height map. Diffuse sky uses vanilla queues, opacity and face shapes, with all neighbours on
 * the ring. A cached lower source boundary represents either rule without scanning whole columns.
 * Clients retain the server's light layers; only the server computes sources and propagation.
 */
@Mixin(SkyLightEngine.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class SkyLightEngineYLoopMixin extends LightEngine implements LoopingSkyLight {
    @Unique private final LongSet biblioteca$columns = new LongOpenHashSet();
    @Unique private final LongSet biblioteca$rebuild = new LongOpenHashSet();
    @Unique private final LongSet biblioteca$dataSections = new LongOpenHashSet();
    @Unique private final Long2ObjectMap<int[]> biblioteca$sources = new Long2ObjectOpenHashMap<>();
    @Unique private volatile boolean biblioteca$periodic;
    @Unique private volatile SkyLightMode biblioteca$skyMode = SkyLightMode.DEFAULT;
    @Unique private volatile boolean biblioteca$pending;
    @Unique private Level biblioteca$level;

    protected SkyLightEngineYLoopMixin(LightChunkGetter source, LayerLightSectionStorage storage) { super(source, storage); }

    @Unique private Level biblioteca$level() {
        if (biblioteca$level == null) biblioteca$level = LoopingLight.levelOf(chunkSource);
        return biblioteca$level;
    }
    @Unique private boolean biblioteca$looping() {
        Level level = biblioteca$level();
        return level != null && LoopingRegistry.isLooping(level);
    }
    @Unique private LightStorageAccessor biblioteca$data() { return (LightStorageAccessor) storage; }

    @Override public boolean biblioteca$hasSkyWork() {
        boolean looping = biblioteca$looping();
        return biblioteca$pending || biblioteca$periodic != looping
                || looping && biblioteca$skyMode != LoopingRegistry.skyLightMode(biblioteca$level());
    }

    @Override public boolean biblioteca$skySectionEmpty(SectionPos section, boolean empty) {
        // Absence means physically empty; unloading must not retain a map entry per empty section.
        if (empty) biblioteca$dataSections.remove(section.asLong());
        else biblioteca$dataSections.add(section.asLong());
        return biblioteca$looping() && biblioteca$columns.contains(Compat.chunkKey(section.x(), section.z())) ? false : empty;
    }

    @Inject(method = "setLightEnabled", at = @At("HEAD"), cancellable = true)
    private void biblioteca$trackChunk(ChunkPos pos, boolean enabled, CallbackInfo ci) {
        boolean looping = biblioteca$looping();
        if (!enabled) {
            boolean tracked = biblioteca$columns.remove(Compat.chunkKey(pos));
            biblioteca$rebuild.remove(Compat.chunkKey(pos));
            biblioteca$sources.remove(Compat.chunkKey(pos));
            if (tracked && (looping || biblioteca$periodic)) biblioteca$sectionStatus(pos, true, true);
            // initializeLight(false) also disables a new, not-yet-lit chunk. Keep its section
            // records for the following propagateLightSources call and later vanilla restoration.
            if (tracked && biblioteca$level() != null) {
                for (int y = Compat.minSection(biblioteca$level()); y <= Compat.maxSection(biblioteca$level()); y++) {
                    biblioteca$dataSections.remove(SectionPos.asLong(Compat.chunkX(pos), y, Compat.chunkZ(pos)));
                }
            }
            return; // Vanilla disables the storage column.
        }
        boolean added = biblioteca$columns.add(Compat.chunkKey(pos));
        if (!looping) return;
        super.setLightEnabled(pos, true);
        biblioteca$sectionStatus(pos, false, false);
        if (added && !biblioteca$level().isClientSide()) biblioteca$requestRebuild(pos);
        ci.cancel(); // Do not fill sections above the vanilla heightmap with level 15.
    }

    @Inject(method = "propagateLightSources", at = @At("HEAD"), cancellable = true)
    private void biblioteca$periodicSources(ChunkPos pos, CallbackInfo ci) {
        biblioteca$columns.add(Compat.chunkKey(pos));
        if (!biblioteca$looping()) return;
        super.setLightEnabled(pos, true);
        biblioteca$sectionStatus(pos, false, false);
        if (!biblioteca$level().isClientSide()) biblioteca$requestRebuild(pos);
        ci.cancel();
    }

    @Unique private void biblioteca$requestRebuild(ChunkPos pos) {
        biblioteca$rebuild.add(Compat.chunkKey(pos));
        biblioteca$pending = true;
    }

    @Unique private void biblioteca$sectionStatus(ChunkPos pos, boolean restore, boolean unloading) {
        Level level = biblioteca$level();
        for (int y = Compat.minSection(level); y <= Compat.maxSection(level); y++) {
            long section = SectionPos.asLong(Compat.chunkX(pos), y, Compat.chunkZ(pos));
            boolean empty = unloading || restore && !biblioteca$dataSections.contains(section);
            biblioteca$data().biblioteca$sectionStatus(section, empty);
        }
    }

    @Override public void biblioteca$prepareSkyUpdates() {
        boolean looping = biblioteca$looping();
        Level level = biblioteca$level();
        if (level == null) return;
        SkyLightMode mode = LoopingRegistry.skyLightMode(level);
        boolean changed = looping != biblioteca$periodic || looping && mode != biblioteca$skyMode;
        if (!changed && !biblioteca$pending) return;
        biblioteca$periodic = looping;
        biblioteca$skyMode = mode;
        if (changed) {
            // Old vanilla source entries must not reintroduce level 15 after sources are removed.
            ((LightEngineAccessor) this).biblioteca$increaseQueue().clear();
            ((LightEngineAccessor) this).biblioteca$decreaseQueue().clear();
            biblioteca$rebuild.addAll(biblioteca$columns);
            biblioteca$sources.clear();
            for (long key : biblioteca$columns) biblioteca$sectionStatus(Compat.chunkPos(key), !looping, false);
        }
        if (!level.isClientSide()) {
            // First remove old stored/saved light in the entire batch, then seed its new sources.
            for (long key : biblioteca$rebuild) biblioteca$clearChunk(Compat.chunkPos(key));
            if (looping) {
                for (long key : biblioteca$rebuild) biblioteca$readSources(Compat.chunkPos(key));
                for (long key : biblioteca$rebuild) biblioteca$fillSources(Compat.chunkPos(key));
                for (long key : biblioteca$rebuild) biblioteca$seedSourcesAndBorders(Compat.chunkPos(key));
            } else {
                for (long key : biblioteca$rebuild) ((SkyLightEngine) (Object) this).propagateLightSources(Compat.chunkPos(key));
            }
        }
        biblioteca$rebuild.clear();
        biblioteca$pending = false;
    }

    @Unique private void biblioteca$clearChunk(ChunkPos pos) {
        Level level = biblioteca$level();
        for (int sy = Compat.minSection(level); sy <= Compat.maxSection(level); sy++) {
            long section = SectionPos.asLong(Compat.chunkX(pos), sy, Compat.chunkZ(pos));
            biblioteca$data().biblioteca$queue(section, null); // Discard saved vanilla/old-topology data.
            DataLayer data = biblioteca$data().biblioteca$writable(section);
            if (data == null || data.isEmpty()) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int old = data.get(x, y, z);
                if (old > 0) enqueueDecrease(BlockPos.asLong(Compat.chunkBlockX(pos, x), sy * 16 + y, Compat.chunkBlockZ(pos, z)), QueueEntry.decreaseAllDirections(old));
            }
            data.fill(0);
            biblioteca$data().biblioteca$dirty(section);
        }
    }

    @Unique private void biblioteca$readSources(ChunkPos pos) {
        int[] sources = new int[256];
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            sources[x + z * 16] = biblioteca$findSourceMinY(Compat.chunkBlockX(pos, x), Compat.chunkBlockZ(pos, z));
        }
        biblioteca$sources.put(Compat.chunkKey(pos), sources);
    }

    @Unique private void biblioteca$fillSources(ChunkPos pos) {
        int[] sources = biblioteca$sources.get(Compat.chunkKey(pos));
        for (int sy = Compat.minSection(biblioteca$level()); sy <= Compat.maxSection(biblioteca$level()); sy++) {
            int min = sy * 16, full = 0;
            boolean any = false;
            for (int sourceMin : sources) {
                if (sourceMin <= min) full++;
                if (sourceMin < min + 16) any = true;
            }
            if (!any) continue; // The rebuild already cleared the section.
            long section = SectionPos.asLong(Compat.chunkX(pos), sy, Compat.chunkZ(pos));
            DataLayer data = biblioteca$data().biblioteca$writable(section);
            if (data == null) continue;
            // Preserve compact homogeneous layers above all blockers, including in hybrid mode.
            if (full == 256) data.fill(15);
            else for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                for (int y = Math.max(0, sources[x + z * 16] - min); y < 16; y++) data.set(x, y, z, 15);
            }
            biblioteca$data().biblioteca$dirty(section);
        }
    }

    @Unique private int biblioteca$findSourceMinY(int x, int z) {
        Level level = biblioteca$level();
        int top = Compat.maxY(level) + 1;
        if (biblioteca$skyMode == SkyLightMode.DISABLED) return top;
        LightChunk chunk = chunkSource.getChunkForLighting(x >> 4, z >> 4);
        if (chunk == null) return top;
        int lowest = chunk.getSkyLightSources().getLowestSourceY(x & 15, z & 15);
        if (biblioteca$skyMode == SkyLightMode.HYBRID) return Math.max(Compat.minY(level), Math.min(top, lowest));
        if (lowest != Integer.MIN_VALUE) return top;
        // The vanilla cache checks all interior edges. Also check the edge joining minY to maxY.
        BlockState lower = chunk.getBlockState(new BlockPos(x, Compat.maxY(level), z));
        BlockState upper = chunk.getBlockState(new BlockPos(x, Compat.minY(level), z));
        return biblioteca$occludes(BlockPos.asLong(x, Compat.minY(level), z), upper, BlockPos.asLong(x, Compat.maxY(level), z), lower, Direction.DOWN) ? top : Compat.minY(level);
    }

    @Unique private int biblioteca$opacity(BlockState state, long node) {
        //? if >=1.21.2 {
        return getOpacity(state);
        //?} else {
        /*return getOpacity(state, BlockPos.of(node));
        *///?}
    }
    @Unique private boolean biblioteca$occludes(long from, BlockState a, long to, BlockState b, Direction direction) {
        //? if >=1.21.2 {
        return shapeOccludes(a, b, direction);
        //?} else {
        /*return shapeOccludes(from, a, to, b, direction);
        *///?}
    }

    @Unique private int biblioteca$sourceMinY(int x, int z) {
        int[] sources = biblioteca$sources.get(Compat.chunkKey(x >> 4, z >> 4));
        return sources == null ? Compat.maxY(biblioteca$level()) + 1 : sources[(x & 15) + (z & 15) * 16];
    }

    @Unique private boolean biblioteca$isSource(int x, int y, int z) {
        return y >= biblioteca$sourceMinY(x, z);
    }

    @Unique private boolean biblioteca$active(long node) {
        return SeamPositions.isInside(biblioteca$level(), BlockPos.getY(node))
                && biblioteca$columns.contains(Compat.chunkKey(BlockPos.getX(node) >> 4, BlockPos.getZ(node) >> 4))
                && biblioteca$data().biblioteca$stores(SectionPos.blockToSection(node));
    }

    @Unique private void biblioteca$changeSources(int x, int z, int previousMin, int sourceMin) {
        int min = Math.min(previousMin, sourceMin), top = Math.max(previousMin, sourceMin);
        for (int sy = min >> 4; sy <= (top - 1) >> 4; sy++) {
            long section = SectionPos.asLong(x >> 4, sy, z >> 4);
            DataLayer data = biblioteca$data().biblioteca$writable(section);
            if (data == null) continue;
            for (int y = Math.max(min, sy * 16); y < Math.min(top, sy * 16 + 16); y++) {
                int old = data.get(x & 15, y & 15, z & 15);
                int value = y >= sourceMin ? 15 : 0;
                if (old > value) enqueueDecrease(BlockPos.asLong(x, y, z), QueueEntry.decreaseAllDirections(old));
                data.set(x & 15, y & 15, z & 15, value);
            }
            biblioteca$data().biblioteca$dirty(section);
        }
    }

    @Unique private void biblioteca$seedSourcesAndBorders(ChunkPos pos) {
        Level level = biblioteca$level();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int bx = Compat.chunkBlockX(pos, x), bz = Compat.chunkBlockZ(pos, z);
            biblioteca$seedColumn(bx, bz);
            if (x != 0 && x != 15 && z != 0 && z != 15) continue;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int nx = bx + direction.getStepX(), nz = bz + direction.getStepZ();
                if (nx >> 4 == Compat.chunkX(pos) && nz >> 4 == Compat.chunkZ(pos)) continue;
                for (int y = Compat.minY(level); y <= Compat.maxY(level); y++) {
                    long neighbor = BlockPos.asLong(nx, y, nz);
                    if (!biblioteca$active(neighbor)) continue;
                    int value = biblioteca$data().biblioteca$get(neighbor);
                    if (value > 1) enqueueIncrease(neighbor, QueueEntry.increaseOnlyOneDirection(value, false, direction.getOpposite()));
                }
            }
        }
    }

    @Unique private void biblioteca$seedColumn(int x, int z) {
        int min = biblioteca$sourceMinY(x, z), top = Compat.maxY(biblioteca$level()) + 1;
        if (min >= top) return;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int nx = x + direction.getStepX(), nz = z + direction.getStepZ();
            if (!biblioteca$columns.contains(Compat.chunkKey(nx >> 4, nz >> 4))) continue;
            for (int y = min; y < biblioteca$sourceMinY(nx, nz); y++) {
                enqueueIncrease(BlockPos.asLong(x, y, z), QueueEntry.increaseOnlyOneDirection(15, false, direction));
            }
        }
        if (min > Compat.minY(biblioteca$level())) {
            // A partial source column illuminates below its lowest source and across the top seam.
            enqueueIncrease(BlockPos.asLong(x, min, z), QueueEntry.increaseOnlyOneDirection(15, false, Direction.DOWN));
            enqueueIncrease(BlockPos.asLong(x, top - 1, z), QueueEntry.increaseOnlyOneDirection(15, false, Direction.UP));
        }
    }

    @Inject(method = "checkNode", at = @At("HEAD"), cancellable = true)
    private void biblioteca$checkPeriodicNode(long node, CallbackInfo ci) {
        if (!biblioteca$looping()) return;
        ci.cancel();
        if (biblioteca$level().isClientSide()) return;
        node = LoopingLight.foldBlockNode(node, biblioteca$level());
        if (!biblioteca$active(node)) return;
        int x = BlockPos.getX(node), z = BlockPos.getZ(node);
        int[] sources = biblioteca$sources.get(Compat.chunkKey(x >> 4, z >> 4));
        if (sources == null) return; // This chunk will be seeded by its scheduled rebuild.
        int sourceMin = biblioteca$findSourceMinY(x, z);
        int index = (x & 15) + (z & 15) * 16;
        if (sourceMin != sources[index]) {
            int previousMin = sources[index];
            sources[index] = sourceMin;
            biblioteca$changeSources(x, z, previousMin, sourceMin);
            biblioteca$seedColumn(x, z);
            return;
        }
        boolean source = BlockPos.getY(node) >= sourceMin;
        int old = biblioteca$data().biblioteca$get(node);
        if (!source && old > 0) {
            biblioteca$data().biblioteca$set(node, 0);
            enqueueDecrease(node, QueueEntry.decreaseAllDirections(old));
        } else enqueueDecrease(node, PULL_LIGHT_IN_ENTRY);
        if (source) enqueueIncrease(node, QueueEntry.increaseLightFromEmission(15, false));
    }

    @Inject(method = "propagateIncrease", at = @At("HEAD"), cancellable = true)
    private void biblioteca$increaseAroundRing(long from, long entry, int value, CallbackInfo ci) {
        if (!biblioteca$looping()) return;
        ci.cancel();
        if (biblioteca$level().isClientSide() || !biblioteca$active(from)) return;
        BlockState fromState = getState(BlockPos.of(from));
        for (Direction direction : Direction.values()) {
            if (!QueueEntry.shouldPropagateInDirection(entry, direction)) continue;
            long to = LoopingLight.foldBlockNode(BlockPos.offset(from, direction), biblioteca$level());
            if (!biblioteca$active(to) || biblioteca$data().biblioteca$get(to) >= value - 1) continue;
            BlockState toState = getState(BlockPos.of(to));
            int next = value - biblioteca$opacity(toState, to);
            if (next <= biblioteca$data().biblioteca$get(to) || biblioteca$occludes(from, fromState, to, toState, direction)) continue;
            biblioteca$data().biblioteca$set(to, next);
            if (next > 1) enqueueIncrease(to, QueueEntry.increaseSkipOneDirection(next, false, direction.getOpposite()));
        }
    }

    @Inject(method = "propagateDecrease", at = @At("HEAD"), cancellable = true)
    private void biblioteca$decreaseAroundRing(long from, long entry, CallbackInfo ci) {
        if (!biblioteca$looping()) return;
        ci.cancel();
        if (biblioteca$level().isClientSide()) return;
        int previous = QueueEntry.getFromLevel(entry);
        for (Direction direction : Direction.values()) {
            if (!QueueEntry.shouldPropagateInDirection(entry, direction)) continue;
            long to = LoopingLight.foldBlockNode(BlockPos.offset(from, direction), biblioteca$level());
            if (!biblioteca$active(to)) continue;
            int current = biblioteca$data().biblioteca$get(to);
            if (current == 0) continue;
            if (current >= previous) {
                enqueueIncrease(to, QueueEntry.increaseOnlyOneDirection(current, false, direction.getOpposite()));
            } else {
                biblioteca$data().biblioteca$set(to, 0);
                boolean source = biblioteca$isSource(BlockPos.getX(to), BlockPos.getY(to), BlockPos.getZ(to));
                if (!source) enqueueDecrease(to, QueueEntry.decreaseSkipOneDirection(current, direction.getOpposite()));
                else enqueueIncrease(to, QueueEntry.increaseLightFromEmission(15, false));
            }
        }
    }
}
