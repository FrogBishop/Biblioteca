package babel.biblioteca.client;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.BibliotecaDiagnostics;
import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClientDiagnostics {
    private static String rendererMods = "vanilla";
    private ClientDiagnostics() {}

    public static void setRendererMods(String value) { rendererMods = value; }

    public static List<String> append(List<String> original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return original;
        boolean looping = LoopingRegistry.isLooping(mc.level);
        List<String> lines = new ArrayList<>(original);
        lines.add("");
        lines.add(I18n.get("biblioteca.diagnostics.client.mode", looping, LoopingRegistry.skyLightMode(mc.level).id()));
        if (mc.showOnlyReducedInfo()) return lines;
        double y = ClientCompat.cameraPosition().y;
        int period = Compat.height(mc.level), reach = ClientCompat.renderDistance() * 16;
        //? if >=1.19 {
        double fov = mc.options.fov().get();
        //?} else {
        /*double fov = mc.options.fov;
        *///?}
        lines.add(I18n.get("biblioteca.diagnostics.client.position", Compat.minY(mc.level), Compat.maxY(mc.level), period,
                String.format(Locale.ROOT, "%.2f", y), String.format(Locale.ROOT, "%.2f", looping ? SeamPositions.foldY(mc.level, y) : y)));
        lines.add(I18n.get("biblioteca.diagnostics.client.render", rendererMods, (int) fov, ClientCompat.renderDistance(),
                looping ? 2 * reach / period + 1 : 1));
        return lines;
    }

    /** Integrated-server exports can be compared after scheduling a separate client-thread sample. */
    public static void capture(BibliotecaDiagnostics.Export export) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getSingleplayerServer() != export.server() || mc.level == null || !mc.level.dimension().equals(export.dimension())) return;
            var data = new com.google.gson.JsonParser().parse(export.data().toString()).getAsJsonObject();
            data.addProperty("clientGameTime", mc.level.getGameTime());
            data.addProperty("rendererMods", rendererMods);
            data.addProperty("sameTickSnapshot", false);
            int compared = 0, mismatches = 0;
            for (var element : data.getAsJsonArray("samples")) {
                var sample = element.getAsJsonObject();
                BlockPos pos = new BlockPos(sample.get("x").getAsInt(), sample.get("y").getAsInt(), sample.get("z").getAsInt());
                boolean loaded = mc.level.hasChunkAt(pos); sample.addProperty("clientLoaded", loaded);
                if (!loaded || !sample.get("loaded").getAsBoolean()) continue;
                int sky = mc.level.getBrightness(LightLayer.SKY, pos), block = mc.level.getBrightness(LightLayer.BLOCK, pos);
                sample.addProperty("clientSky", sky); sample.addProperty("clientBlock", block);
                compared++;
                if (sky != sample.get("serverSky").getAsInt() || block != sample.get("serverBlock").getAsInt()) mismatches++;
            }
            data.addProperty("comparedSamples", compared); data.addProperty("lightMismatches", mismatches);
            var target = export.file().resolveSibling(export.file().getFileName().toString().replace(".json", "-client.json"));
            try { BibliotecaDiagnostics.write(target, data); }
            catch (IOException error) { Biblioteca.LOGGER.warn("Could not write client diagnostic comparison", error); }
        });
    }
}
