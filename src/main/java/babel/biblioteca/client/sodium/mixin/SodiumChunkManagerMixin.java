package babel.biblioteca.client.sodium.mixin;

import babel.biblioteca.client.sodium.SodiumRenderContext;
import babel.biblioteca.client.sodium.SodiumDrawList;
import babel.biblioteca.client.sodium.SodiumDrawList.Copy;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.LoopingClientSettings;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderColumn;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import me.jellysquid.mods.sodium.client.render.chunk.cull.ChunkFaceFlags;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Discover periodic sources, then submit copies through Sodium's own buffers and shaders. */
@Mixin(value = ChunkRenderManager.class, remap = false)
public abstract class SodiumChunkManagerMixin<T extends ChunkGraphicsState> {
    @Shadow @Final private ClientLevel world;
    @Shadow @Final private Long2ObjectOpenHashMap<ChunkRenderColumn<T>> columns;
    @Shadow @Final private ChunkRenderBackend<T> backend;
    @Shadow @Final private boolean useBlockFaceCulling;
    @Shadow private void addChunk(ChunkRenderContainer<T> render) { throw new AssertionError(); }
    @Unique private final List<ChunkRenderContainer<T>> biblioteca$sources = new ArrayList<>();
    @Unique private FrustumExtended biblioteca$frustum;

    @Inject(method = "iterateChunks", at = @At("HEAD"), cancellable = true)
    private void biblioteca$periodicVisibility(Camera camera, FrustumExtended frustum, int frame, boolean spectator, CallbackInfo ci) {
        biblioteca$sources.clear(); biblioteca$frustum = frustum;
        if (!LoopingRegistry.isLooping(world)) return;
        var eye = camera.getPosition();
        int period = Compat.height(world);
        double slack = LoopingClientSettings.preloadBlocks(), reach = ClientCompat.renderDistance() * 16.0 + slack;
        for (ChunkRenderColumn<T> column : columns.values()) for (int sy = 0; sy < (period >> 4); sy++) {
            ChunkRenderContainer<T> section = column.getRender(sy);
            if (section == null) continue;
            int x = section.getOriginX(), y = section.getOriginY(), z = section.getOriginZ();
            if (Math.abs(x + 8.0 - eye.x) > reach + 16 || Math.abs(z + 8.0 - eye.z) > reach + 16) continue;
            int first = (int)Math.ceil((eye.y - reach - y - 8.0) / period);
            int last = (int)Math.floor((eye.y + reach - y - 8.0) / period);
            for (int copy = first; copy <= last; copy++) {
                if (!biblioteca$visible(x, y + copy * period, z, slack)) continue;
                biblioteca$sources.add(section);
                addChunk(section);
                break;
            }
        }
        ci.cancel();
    }

    @Inject(method = "computeVisibleFaces", at = @At("HEAD"), cancellable = true)
    private void biblioteca$periodicFaces(CallbackInfoReturnable<Integer> cir) {
        if (LoopingRegistry.isLooping(world)) cir.setReturnValue(ChunkFaceFlags.ALL);
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
    private void biblioteca$drawCopies(PoseStack poses, BlockRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        if (!LoopingRegistry.isLooping(world)) return;
        int period = Compat.height(world);
        double reach = ClientCompat.renderDistance() * 16.0;
        List<Copy<T>> copies = new ArrayList<>();
        for (ChunkRenderContainer<T> source : biblioteca$sources) {
            T state = source.getGraphicsState(pass);
            if (state == null) continue;
            int bx = source.getOriginX(), by = source.getOriginY(), bz = source.getOriginZ();
            int first = (int)Math.ceil((y - reach - by - 8.0) / period);
            int last = (int)Math.floor((y + reach - by - 8.0) / period);
            for (int copy = first; copy <= last; copy++) {
                int offset = copy * period;
                if (!biblioteca$visible(bx, by + offset, bz, 0)) continue;
                int faces = biblioteca$faces(source, x, y - offset, z) & source.getFacesWithData();
                if (faces == 0) continue;
                double dx = bx + 8 - x, dy = by + offset + 8 - y, dz = bz + 8 - z;
                copies.add(new Copy<>(state, offset, faces, dx * dx + dy * dy + dz * dz));
            }
        }
        // Keep Sodium's section-based transparency ordering, now across the displayed copies.
        Comparator<Copy<T>> order = Comparator.comparingDouble(Copy::distance);
        copies.sort(pass.isTranslucent() ? order.reversed() : order);
        var commands = RenderDevice.INSTANCE.createCommandList();
        backend.begin(poses);
        try {
            backend.render(commands, new SodiumDrawList<>(copies), new ChunkCameraContext(x, y, z));
        } finally {
            SodiumRenderContext.clear();
            backend.end(poses);
            commands.flush();
        }
        ci.cancel();
    }

    @Unique private boolean biblioteca$visible(int x, int y, int z, double slack) {
        return biblioteca$frustum == null || biblioteca$frustum.fastAabbTest((float)(x - slack), (float)(y - slack), (float)(z - slack),
                (float)(x + 16 + slack), (float)(y + 16 + slack), (float)(z + 16 + slack));
    }

    @Unique private int biblioteca$faces(ChunkRenderContainer<T> source, double x, double y, double z) {
        if (!useBlockFaceCulling) return ChunkFaceFlags.ALL;
        var bounds = source.getBounds();
        int faces = ChunkFaceFlags.UNASSIGNED;
        if (y > bounds.y1) faces |= ChunkFaceFlags.UP;
        if (y < bounds.y2) faces |= ChunkFaceFlags.DOWN;
        if (x > bounds.x1) faces |= ChunkFaceFlags.EAST;
        if (x < bounds.x2) faces |= ChunkFaceFlags.WEST;
        if (z > bounds.z1) faces |= ChunkFaceFlags.SOUTH;
        if (z < bounds.z2) faces |= ChunkFaceFlags.NORTH;
        return faces;
    }
}
