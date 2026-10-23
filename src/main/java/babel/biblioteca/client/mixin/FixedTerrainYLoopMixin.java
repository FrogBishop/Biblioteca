package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.FixedTerrainBackend;
import babel.biblioteca.client.LegacyTerrainSources;
import babel.biblioteca.client.SeamCull;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Submits periodic copies inside one native 1.16 material pass, including globally sorted alpha. */
@Mixin(LevelRenderer.class)
public abstract class FixedTerrainYLoopMixin {
    @Unique private FixedTerrainBackend biblioteca$backend;

    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true)
    private void biblioteca$drawCopies(RenderType layer, PoseStack poses, double x, double y, double z, CallbackInfo ci) {
        Level level = Minecraft.getInstance().level;
        if (level == null || !LoopingRegistry.isLooping(level)) return;
        if (this.biblioteca$backend == null) this.biblioteca$backend = new FixedTerrainBackend();
        LegacyTerrainSources sources = (LegacyTerrainSources) this;
        Frustum frustum = sources.biblioteca$terrainFrustum();
        layer.setupRenderState();
        try {
            this.biblioteca$backend.draw(sources.biblioteca$terrainSources(), layer, poses, Compat.height(level),
                    new Vec3(x, y, z), ClientCompat.renderDistance() * 16.0,
                    (x0, y0, z0, x1, y1, z1) -> frustum == null || SeamCull.plainlyVisible(frustum, x0, y0, z0, x1, y1, z1));
        } finally {
            layer.clearRenderState();
        }
        ci.cancel();
    }

    @Inject(method = {"close", "allChanged", "setLevel"}, at = @At("HEAD"))
    private void biblioteca$release(CallbackInfo ci) {
        if (this.biblioteca$backend != null) this.biblioteca$backend.close();
    }
}
