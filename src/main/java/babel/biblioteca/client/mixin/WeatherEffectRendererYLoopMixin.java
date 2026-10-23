package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.LoopingWeather;
import babel.biblioteca.client.WeatherCompat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.WeatherRenderState;
//?} else if >=1.21.9 {
/*import net.minecraft.client.renderer.state.WeatherRenderState;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** Samples source rows separately on each side of the boundary and shifts only rain/snow geometry. */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererYLoopMixin {
    @Shadow protected abstract WeatherEffectRenderer.ColumnInstance createRainColumnInstance(RandomSource random,
            //? if >=26.2 {
            long ticks,
            //?} else {
            /*int ticks,
            *///?}
            int x, int bottom, int top, int z, int light, float partialTicks);
    @Shadow protected abstract WeatherEffectRenderer.ColumnInstance createSnowColumnInstance(RandomSource random,
            //? if >=26.2 {
            long ticks,
            //?} else {
            /*int ticks,
            *///?}
            int x, int bottom, int top, int z, int light, float partialTicks);

    //? if >=1.21.9 {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void biblioteca$extract(
            //? if >=26.2 {
            ClientLevel level,
            //?} else {
            /*Level level, int ticks,
            *///?}
            float partialTicks, Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
        if (!LoopingRegistry.isLooping(level)) return;
        ci.cancel();
        state.intensity = level.getRainLevel(partialTicks);
        if (state.intensity <= 0) return;
        state.radius = WeatherCompat.radius();
        //? if >=26.2 {
        long ticks = level.getGameTime();
        //?}
        biblioteca$columns(level, ticks, partialTicks, camera, state.radius, state.rainColumns, state.snowColumns);
    }
    //?} else {
    /*@Inject(method = "collectColumnInstances", at = @At("HEAD"), cancellable = true)
    private void biblioteca$collect(Level level, int ticks, float partialTicks, Vec3 camera, int radius,
            List<WeatherEffectRenderer.ColumnInstance> rain, List<WeatherEffectRenderer.ColumnInstance> snow, CallbackInfo ci) {
        if (!LoopingRegistry.isLooping(level)) return;
        ci.cancel();
        biblioteca$columns(level, ticks, partialTicks, camera, radius, rain, snow);
    }
    *///?}

    @Unique
    private void biblioteca$columns(Level level,
            //? if >=26.2 {
            long ticks,
            //?} else {
            /*int ticks,
            *///?}
            float partialTicks, Vec3 camera, int radius,
            List<WeatherEffectRenderer.ColumnInstance> rain, List<WeatherEffectRenderer.ColumnInstance> snow) {
        int cameraX = Mth.floor(camera.x);
        int cameraZ = Mth.floor(camera.z);
        var slices = LoopingWeather.visibleSlices(Mth.floor(camera.y), radius, Compat.minY(level), Compat.height(level));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RandomSource random = RandomSource.create();
        for (int z = cameraZ - radius; z <= cameraZ + radius; z++) {
            for (int x = cameraX - radius; x <= cameraX + radius; x++) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int seed = x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761;
                for (var slice : slices) {
                    int bottom = slice.bottomY(surface);
                    int top = slice.topY();
                    if (bottom >= top) continue;
                    var precipitation = WeatherCompat.precipitation(level, pos.set(x, slice.cameraY(), z));
                    if (precipitation == Biome.Precipitation.NONE) continue;
                    int light = WeatherCompat.light(level, pos.set(x, Math.max(slice.cameraY(), surface), z));
                    random.setSeed(seed);
                    if (precipitation == Biome.Precipitation.RAIN) {
                        rain.add(createRainColumnInstance(random, ticks, x, bottom, top, z, light, partialTicks));
                    } else {
                        snow.add(createSnowColumnInstance(random, ticks, x, bottom, top, z, light, partialTicks));
                    }
                }
            }
        }
    }
}
