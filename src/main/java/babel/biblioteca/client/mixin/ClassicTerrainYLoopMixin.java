package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClassicTerrainBackend;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.PeriodicTerrainPlan;
import babel.biblioteca.client.SeamCull;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds copies within the existing terrain pass and substitutes a globally sorted alpha stream. */
@Mixin(LevelRenderer.class)
public abstract class ClassicTerrainYLoopMixin {
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Shadow private Frustum cullingFrustum;
    @Shadow private Frustum capturedFrustum;
    @Unique private ClassicTerrainBackend biblioteca$backend;
    @Unique private boolean biblioteca$sorted;

    @Inject(method = "renderSectionLayer", at = @At("HEAD"))
    private void biblioteca$beginLayer(CallbackInfo ci) { this.biblioteca$sorted = false; }

    @Inject(method = "renderSectionLayer", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectListIterator;hasPrevious()Z", ordinal = 0))
    private void biblioteca$sortedLayer(CallbackInfo ci, @Local Uniform offset,
            @Local(argsOnly = true) RenderType layer,
            @Local(argsOnly = true, ordinal = 0) double x,
            @Local(argsOnly = true, ordinal = 1) double y,
            @Local(argsOnly = true, ordinal = 2) double z) {
        if (this.biblioteca$sorted || layer != RenderType.translucent() || offset == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level)) return;
        if (this.biblioteca$backend == null) this.biblioteca$backend = new ClassicTerrainBackend();
        this.biblioteca$backend.drawSorted(this.visibleSections, layer, offset, minecraft.level.getHeight(),
                new Vec3(x, y, z), minecraft.options.getEffectiveRenderDistance() * 16.0, biblioteca$visibility());
        this.biblioteca$sorted = true;
    }

    @WrapOperation(method = "renderSectionLayer", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void biblioteca$copyDraw(VertexBuffer buffer, Operation<Void> original,
            @Local Uniform offset, @Local SectionRenderDispatcher.RenderSection section,
            @Local(argsOnly = true) RenderType layer,
            @Local(argsOnly = true, ordinal = 0) double x,
            @Local(argsOnly = true, ordinal = 1) double y,
            @Local(argsOnly = true, ordinal = 2) double z) {
        if (this.biblioteca$sorted) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !LoopingRegistry.isLooping(minecraft.level) || offset == null) {
            original.call(buffer);
            return;
        }
        if (layer == RenderType.tripwire()) {
            if (this.biblioteca$backend == null) this.biblioteca$backend = new ClassicTerrainBackend();
            this.biblioteca$backend.drawSorted(this.visibleSections, layer, offset, minecraft.level.getHeight(),
                    new Vec3(x, y, z), minecraft.options.getEffectiveRenderDistance() * 16.0, biblioteca$visibility());
            this.biblioteca$sorted = true;
            return;
        }
        BlockPos origin = ClientCompat.origin(section);
        int period = minecraft.level.getHeight();
        double distance = minecraft.options.getEffectiveRenderDistance() * 16.0;
        int first = (int) Math.ceil((y - distance - origin.getY() - 8.0) / period);
        int last = (int) Math.floor((y + distance - origin.getY() - 8.0) / period);
        var visible = biblioteca$visibility();
        for (int copy = first; copy <= last; copy++) {
            double copyY = origin.getY() + (double) copy * period;
            if (!visible.test(origin.getX(), copyY, origin.getZ(), origin.getX() + 16.0, copyY + 16.0, origin.getZ() + 16.0)) continue;
            offset.set((float) (origin.getX() - x), (float) (copyY - y), (float) (origin.getZ() - z));
            offset.upload();
            original.call(buffer);
        }
    }

    @Unique private PeriodicTerrainPlan.Visibility biblioteca$visibility() {
        Frustum frustum = this.capturedFrustum != null ? this.capturedFrustum : this.cullingFrustum;
        return (x0, y0, z0, x1, y1, z1) -> frustum == null || SeamCull.plainlyVisible(frustum, x0, y0, z0, x1, y1, z1);
    }

    @Inject(method = {"close", "allChanged", "setLevel"}, at = @At("HEAD"))
    private void biblioteca$releaseGeometry(CallbackInfo ci) {
        if (this.biblioteca$backend != null) this.biblioteca$backend.close();
    }
}
