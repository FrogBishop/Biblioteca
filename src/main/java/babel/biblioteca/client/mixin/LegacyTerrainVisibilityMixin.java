package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.LegacyTerrainSources;
import babel.biblioteca.client.LoopingClientSettings;
import babel.biblioteca.client.SeamCull;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Adds visible periodic sources to the same list vanilla compiles and renders. */
@Mixin(LevelRenderer.class)
public abstract class LegacyTerrainVisibilityMixin implements LegacyTerrainSources {
    @Shadow private ViewArea viewArea;
    @Unique private Frustum biblioteca$frustum;
    @Override public Frustum biblioteca$terrainFrustum() { return this.biblioteca$frustum; }
    //? if >=1.18 {
    @Shadow @Final private ObjectArrayList<LevelRenderer.RenderChunkInfo> renderChunksInFrustum;
    //?} else if >=1.17 {
    /*@Shadow @Final private ObjectArrayList<LevelRenderer.RenderChunkInfo> renderChunks;
    *///?} else {
    /*@Shadow @Final private ObjectList<LevelRenderer.RenderChunkInfo> renderChunks;
    *///?}

    private List<LevelRenderer.RenderChunkInfo> biblioteca$nativeSources() {
        //? if >=1.18 {
        return this.renderChunksInFrustum;
        //?} else {
        /*return this.renderChunks;
        *///?}
    }

    @Override public List<SectionRenderDispatcher.RenderSection> biblioteca$terrainSources() {
        List<SectionRenderDispatcher.RenderSection> sources = new ArrayList<>();
        for (LevelRenderer.RenderChunkInfo info : biblioteca$nativeSources()) sources.add(info.chunk);
        return sources;
    }

    @Inject(method = "setupRender", at = @At("TAIL"))
    private void biblioteca$includePeriodicSources(CallbackInfo ci, @Local(argsOnly = true) Frustum frustum) {
        this.biblioteca$frustum = frustum;
        Level level = Minecraft.getInstance().level;
        if (this.viewArea == null || level == null || !LoopingRegistry.isLooping(level)) return;
        FrustumInvoker camera = (FrustumInvoker) frustum;
        double slack = LoopingClientSettings.preloadBlocks();
        double reach = ClientCompat.renderDistance() * 16.0 + slack;
        int period = Compat.height(level);
        Set<SectionRenderDispatcher.RenderSection> present = Collections.newSetFromMap(new IdentityHashMap<>());
        List<LevelRenderer.RenderChunkInfo> visible = biblioteca$nativeSources();
        for (LevelRenderer.RenderChunkInfo info : visible) present.add(info.chunk);
        for (SectionRenderDispatcher.RenderSection section : this.viewArea.chunks) {
            if (present.contains(section)) continue;
            BlockPos pos = ClientCompat.origin(section);
            if (Math.abs(pos.getX() + 8.0 - camera.biblioteca$cameraX()) > reach + 16.0
                    || Math.abs(pos.getZ() + 8.0 - camera.biblioteca$cameraZ()) > reach + 16.0) continue;
            int first = (int) Math.ceil((camera.biblioteca$cameraY() - reach - pos.getY() - 8.0) / period);
            int last = (int) Math.floor((camera.biblioteca$cameraY() + reach - pos.getY() - 8.0) / period);
            for (int layer = first; layer <= last; layer++) {
                double y = pos.getY() + (double) layer * period;
                if (!SeamCull.plainlyVisible(frustum, pos.getX() - slack, y - slack, pos.getZ() - slack,
                        pos.getX() + 16.0 + slack, y + 16.0 + slack, pos.getZ() + 16.0 + slack)) continue;
                //? if >=1.17 {
                visible.add(new LevelRenderer.RenderChunkInfo(section, null, 0));
                //?} else {
                /*visible.add(((LevelRenderer) (Object) this).new RenderChunkInfo(section, null, 0));
                *///?}
                break;
            }
        }
    }
}
