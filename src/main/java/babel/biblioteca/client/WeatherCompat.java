package babel.biblioteca.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
//? if >=26.2 {
import net.minecraft.util.LightCoordsUtil;
//?} else {
/*import net.minecraft.client.renderer.LevelRenderer;
*///?}

/** Native weather options and source-world sampling, before applying a periodic display offset. */
public final class WeatherCompat {
    private WeatherCompat() {}

    public static int radius() {
        //? if >=1.21.11 {
        return Minecraft.getInstance().options.weatherRadius().get();
        //?} else {
        /*return Minecraft.useFancyGraphics() ? 10 : 5;
        *///?}
    }

    public static Biome.Precipitation precipitation(Level level, BlockPos pos) {
        //? if >=26.2 {
        return ((ClientLevel) level).getPrecipitationAt(pos);
        //?} else {
        /*if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())))
            return Biome.Precipitation.NONE;
        //? if >=1.21.2 {
        return level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel());
        //?} else if >=1.19.4 {
        return level.getBiome(pos).value().getPrecipitationAt(pos);
        //?} else {
        //? if >=1.18.2 {
        Biome biome = level.getBiome(pos).value();
        //?} else {
        Biome biome = level.getBiome(pos);
        //?}
        if (biome.getPrecipitation() == Biome.Precipitation.NONE) return Biome.Precipitation.NONE;
        //? if >=1.18 {
        boolean warm = biome.warmEnoughToRain(pos);
        //?} else {
        boolean warm = biome.getTemperature(pos) >= 0.15f;
        //?}
        return warm ? Biome.Precipitation.RAIN : Biome.Precipitation.SNOW;
        //?}
        *///?}
    }

    public static int light(Level level, BlockPos pos) {
        //? if >=26.2 {
        return LightCoordsUtil.getLightCoords(level, pos);
        //?} else if >=26.1 {
        /*return LevelRenderer.getLightCoords(level, pos);
        *///?} else {
        /*return LevelRenderer.getLightColor(level, pos);
        *///?}
    }
}
