package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.LoopingWeather;
import babel.biblioteca.client.WeatherCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inline weather renderer used through 1.21.1, with the same sliced geometry as newer releases. */
@Mixin(LevelRenderer.class)
public abstract class ClassicWeatherYLoopMixin {
    @Shadow @Final private float[] rainSizeX;
    @Shadow @Final private float[] rainSizeZ;
    @Shadow private int ticks;

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void biblioteca$weather(LightTexture lightTexture, float partialTicks, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level == null || !LoopingRegistry.isLooping(level)) return;
        ci.cancel();
        float intensity = level.getRainLevel(partialTicks);
        if (intensity <= 0) return;
        int radius = WeatherCompat.radius();
        int cx = Mth.floor(cameraX), cz = Mth.floor(cameraZ);
        var slices = LoopingWeather.visibleSlices(Mth.floor(cameraY), radius, Compat.minY(level), Compat.height(level));
        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
//? if >=1.17 {
        RenderSystem.setShader(GameRenderer::getParticleShader);
//?} else {
/*        RenderSystem.enableAlphaTest();
        RenderSystem.alphaFunc(516, 0.1f);
*///?}

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RandomSource random = RandomSource.create();
        // Keep the native rain/snow textures and animation, batching all periodic slices together.
        for (var kind : new Biome.Precipitation[]{Biome.Precipitation.RAIN, Biome.Precipitation.SNOW}) {
            BufferBuilder buffer = null;
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int x = cx - radius; x <= cx + radius; x++) {
                    int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    int column = (z - cz + 16) * 32 + x - cx + 16;
                    double rx = rainSizeX[column] * 0.5, rz = rainSizeZ[column] * 0.5;
                    for (var slice : slices) {
                        int bottom = slice.bottomY(surface), top = slice.topY();
                        if (bottom >= top || WeatherCompat.precipitation(level, pos.set(x, slice.cameraY(), z)) != kind) continue;
                        if (buffer == null) {
                            var texture = Compat.id("minecraft", kind == Biome.Precipitation.RAIN
                                    ? "textures/environment/rain.png" : "textures/environment/snow.png");
                            //? if >=1.17 {
                            RenderSystem.setShaderTexture(0, texture);
                            //?} else {
                            /*Minecraft.getInstance().getTextureManager().bind(texture);
                            *///?}
                            //? if >=1.21 {
                            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                            //?} else {
                            /*buffer = Tesselator.getInstance().getBuilder();
                            //? if >=1.17 {
                            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                            //?} else {
                            buffer.begin(7, DefaultVertexFormat.PARTICLE);
                            //?}
                            *///?}
                        }
                        random.setSeed(x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761);
                        int light = WeatherCompat.light(level, pos.set(x, Math.max(slice.cameraY(), surface), z));
                        double dx = x + 0.5 - cameraX, dz = z + 0.5 - cameraZ;
                        float distance = (float) ((dx * dx + dz * dz) / (radius * radius));
                        float alpha = ((1 - distance) * (kind == Biome.Precipitation.RAIN ? 0.5f : 0.3f) + 0.5f) * intensity;
                        float u = 0, v;
                        if (kind == Biome.Precipitation.RAIN) {
                            int phase = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761 & 255;
                            v = (-((ticks & 131071) + phase + partialTicks) / 32f * (3 + random.nextFloat())) % 32f;
                        } else {
                            float time = ticks + partialTicks;
                            u = (float) (random.nextDouble() + time * 0.01 * (float) random.nextGaussian());
                            v = -((ticks & 511) + partialTicks) / 512f
                                    + (float) (random.nextDouble() + time * (float) random.nextGaussian() * 0.001);
                            light = LightTexture.pack((LightTexture.block(light) * 3 + 15) / 4, (LightTexture.sky(light) * 3 + 15) / 4);
                        }
                        biblioteca$vertex(buffer, dx - rx, top - cameraY, dz - rz, u, bottom * 0.25f + v, alpha, light);
                        biblioteca$vertex(buffer, dx + rx, top - cameraY, dz + rz, u + 1, bottom * 0.25f + v, alpha, light);
                        biblioteca$vertex(buffer, dx + rx, bottom - cameraY, dz + rz, u + 1, top * 0.25f + v, alpha, light);
                        biblioteca$vertex(buffer, dx - rx, bottom - cameraY, dz - rz, u, top * 0.25f + v, alpha, light);
                    }
                }
            }
            if (buffer != null) {
                //? if >=1.21 {
                BufferUploader.drawWithShader(buffer.buildOrThrow());
                //?} else if >=1.19 {
                /*BufferUploader.drawWithShader(buffer.end());
                *///?} else {
                /*buffer.end();
                BufferUploader.end(buffer);
                *///?}
            }
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();
    }

    @Unique private static void biblioteca$vertex(BufferBuilder buffer, double x, double y, double z, float u, float v, float alpha, int light) {
        //? if >=1.21 {
        buffer.addVertex((float) x, (float) y, (float) z).setUv(u, v).setColor(1, 1, 1, alpha).setLight(light);
        //?} else {
        /*buffer.vertex(x, y, z).uv(u, v).color(1, 1, 1, alpha).uv2(light).endVertex();
        *///?}
    }
}
