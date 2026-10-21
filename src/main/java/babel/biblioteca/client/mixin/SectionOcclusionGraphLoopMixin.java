package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.LoopingClientSettings;
import babel.biblioteca.client.SeamCull;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Completes vanilla's source set with sections whose periodic images are visible. */
@Mixin(SectionOcclusionGraph.class)
public abstract class SectionOcclusionGraphLoopMixin {
    @Shadow private ViewArea viewArea;
    @Unique private final LongOpenHashSet biblioteca$present = new LongOpenHashSet();

    @Inject(method = "addSectionsInFrustum", at = @At("TAIL"))
    private void biblioteca$includeBoundarySources(CallbackInfo ci,
            @Local(argsOnly = true) Frustum frustum,
            @Local(argsOnly = true, ordinal = 0) List<SectionRenderDispatcher.RenderSection> visible) {
        Level level = Minecraft.getInstance().level;
        if (this.viewArea == null || level == null || !LoopingRegistry.isLooping(level)) return;
        int period = level.getHeight();
        if (period <= 0) return;
        FrustumInvoker camera = (FrustumInvoker) frustum;
        int cameraX = SectionPos.blockToSectionCoord(camera.biblioteca$cameraX());
        int cameraZ = SectionPos.blockToSectionCoord(camera.biblioteca$cameraZ());
        double cameraY = camera.biblioteca$cameraY();
        int radius = this.viewArea.getViewDistance();
        double slack = LoopingClientSettings.preloadBlocks();
        double reach = radius * 16.0 + slack;
        int minSectionY = level.getMinY() >> 4;
        int count = period >> 4;
        this.biblioteca$present.clear();
        for (SectionRenderDispatcher.RenderSection section : visible) {
            BlockPos origin = ClientCompat.origin(section);
            this.biblioteca$present.add(SectionPos.asLong(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4));
        }
        ViewAreaAccessor lookup = (ViewAreaAccessor) this.viewArea;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int sy = minSectionY; sy < minSectionY + count; sy++) {
            int sourceY = sy << 4;
            int first = (int) Math.ceil((cameraY - reach - sourceY - 8.0) / period);
            int last = (int) Math.floor((cameraY + reach - sourceY - 8.0) / period);
            for (int sx = cameraX - radius; sx <= cameraX + radius; sx++) {
                for (int sz = cameraZ - radius; sz <= cameraZ + radius; sz++) {
                    long key = SectionPos.asLong(sx, sy, sz);
                    if (this.biblioteca$present.contains(key)) continue;
                    int x = sx << 4;
                    int z = sz << 4;
                    SectionRenderDispatcher.RenderSection section = lookup.biblioteca$getRenderSectionAt(probe.set(x, sourceY, z));
                    if (section == null) continue;
                    BlockPos origin = ClientCompat.origin(section);
                    if (origin.getX() != x || origin.getY() != sourceY || origin.getZ() != z) continue;
                    for (int layer = first; layer <= last; layer++) {
                        double y = sourceY + (double) layer * period;
                        if (!SeamCull.plainlyVisible(frustum, x - slack, y - slack, z - slack,
                                x + 16.0 + slack, y + 16.0 + slack, z + 16.0 + slack)) continue;
                        visible.add(section);
                        this.biblioteca$present.add(key);
                        break;
                    }
                }
            }
        }
    }
}
