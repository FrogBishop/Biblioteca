package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.LoopingWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Extracts rain and snow across the boundary between the world's top and bottom.
 *
 * Vanilla clips each weather column against the absolute heightmap. Near the bottom of a looping
 * world this discards the top rows' weather, even though those rows are just below the camera.
 * Weather has its own geometry and is not included in the terrain renderer's periodic copies.
 *
 * Split the usual camera-local weather range into world periods and clip each piece against its
 * own terrain surface and world top. Sampling the source rows preserves their biome, precipitation
 * type and lighting. Only the geometry is shifted; vanilla still supplies its animation, weather
 * radius, rain intensity and both the ordinary and OIT rendering paths.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererYLoopMixin {

    @Shadow
    protected abstract WeatherEffectRenderer.ColumnInstance createRainColumnInstance(
            RandomSource random, long ticks, int x, int bottomY, int topY, int z,
            int lightCoords, float partialTicks);

    @Shadow
    protected abstract WeatherEffectRenderer.ColumnInstance createSnowColumnInstance(
            RandomSource random, long ticks, int x, int bottomY, int topY, int z,
            int lightCoords, float partialTicks);

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void biblioteca$extractPeriodicWeather(
            ClientLevel level, float partialTicks, Vec3 cameraPos, WeatherRenderState renderState,
            CallbackInfo ci) {

        if (!LoopingRegistry.isLooping(level)) return;
        int period = level.getHeight();
        if (period <= 0) return;

        ci.cancel();
        renderState.intensity = level.getRainLevel(partialTicks);
        if (renderState.intensity <= 0.0F) return;

        int radius = Minecraft.getInstance().options.weatherRadius().get();
        renderState.radius = radius;
        int cameraX = Mth.floor(cameraPos.x);
        int cameraY = Mth.floor(cameraPos.y);
        int cameraZ = Mth.floor(cameraPos.z);
        List<LoopingWeather.Slice> slices =
                LoopingWeather.visibleSlices(cameraY, radius, level.getMinY(), period);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RandomSource random = RandomSource.createThreadLocalInstance();
        long ticks = level.getGameTime();

        for (int z = cameraZ - radius; z <= cameraZ + radius; z++) {
            for (int x = cameraX - radius; x <= cameraX + radius; x++) {
                int terrainHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int seed = x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761;

                for (LoopingWeather.Slice slice : slices) {
                    int bottomY = slice.bottomY(terrainHeight);
                    int topY = slice.topY();
                    if (bottomY >= topY) continue;

                    Biome.Precipitation precipitation =
                            level.getPrecipitationAt(pos.set(x, slice.cameraY(), z));
                    if (precipitation == Biome.Precipitation.NONE) continue;

                    int lightY = Math.max(slice.cameraY(), terrainHeight);
                    int lightCoords = LightCoordsUtil.getLightCoords(level, pos.set(x, lightY, z));
                    random.setSeed(seed);

                    if (precipitation == Biome.Precipitation.RAIN) {
                        renderState.rainColumns.add(this.createRainColumnInstance(
                                random, ticks, x, bottomY, topY, z, lightCoords, partialTicks));
                    } else if (precipitation == Biome.Precipitation.SNOW) {
                        renderState.snowColumns.add(this.createSnowColumnInstance(
                                random, ticks, x, bottomY, topY, z, lightCoords, partialTicks));
                    }
                }
            }
        }
    }
}
