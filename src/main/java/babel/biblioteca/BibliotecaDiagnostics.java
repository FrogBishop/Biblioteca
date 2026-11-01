package babel.biblioteca;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.storage.LevelResource;
//? if >=1.19.3 {
import net.minecraft.core.registries.BuiltInRegistries;
//?} else {
/*import net.minecraft.core.Registry;
*///?}
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

/** Explicit operator diagnostics. World sampling stays on the owning game thread. */
public final class BibliotecaDiagnostics {
    public record Export(MinecraftServer server, ResourceKey<Level> dimension, Path file, JsonObject data) {}
    private static Consumer<Export> clientCapture = export -> {};
    private BibliotecaDiagnostics() {}

    public static void setClientCapture(Consumer<Export> capture) { clientCapture = capture; }

    public static int report(CommandSourceStack source) {
        Level level = source.getLevel();
        BlockPos pos = Compat.blockPos(source.getPosition());
        boolean loaded = level.hasChunkAt(pos);
        Compat.sendSuccess(source, () -> Compat.translatable("biblioteca.diagnostics.summary",
                Compat.keyId(level.dimension()).toString(), Compat.minY(level), Compat.maxY(level), Compat.height(level),
                LoopingRegistry.isLooping(level), LoopingRegistry.skyLightMode(level).id(),
                loaded ? level.getBrightness(LightLayer.SKY, pos) : "?",
                loaded ? level.getBrightness(LightLayer.BLOCK, pos) : "?"), false);
        return 1;
    }

    public static int export(CommandSourceStack source) {
        Level level = source.getLevel();
        BlockPos center = Compat.blockPos(source.getPosition());
        JsonObject data = new JsonObject();
        data.addProperty("schemaVersion", 1);
        data.addProperty("dimension", Compat.keyId(level.dimension()).toString());
        data.addProperty("serverGameTime", level.getGameTime());
        data.addProperty("minimumY", Compat.minY(level)); data.addProperty("height", Compat.height(level));
        data.addProperty("looping", LoopingRegistry.isLooping(level));
        data.addProperty("skyMode", LoopingRegistry.skyLightMode(level).id());
        JsonArray samples = new JsonArray();
        for (int x = center.getX() - 8; x <= center.getX() + 8; x++) {
            for (int y = Compat.minY(level); y <= Compat.maxY(level); y++) {
                BlockPos pos = new BlockPos(x, y, center.getZ());
                JsonObject sample = new JsonObject();
                sample.addProperty("x", x); sample.addProperty("y", y); sample.addProperty("z", center.getZ());
                boolean loaded = level.hasChunkAt(pos); sample.addProperty("loaded", loaded);
                if (loaded) {
                    //? if >=1.19.3 {
                    sample.addProperty("block", BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
                    //?} else {
                    /*sample.addProperty("block", Registry.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
                    *///?}
                    sample.addProperty("serverSky", level.getBrightness(LightLayer.SKY, pos));
                    sample.addProperty("serverBlock", level.getBrightness(LightLayer.BLOCK, pos));
                }
                samples.add(sample);
            }
        }
        data.add("samples", samples);
        Path file = source.getServer().getWorldPath(LevelResource.ROOT).resolve("biblioteca-diagnostics")
                .resolve("section-" + level.getGameTime() + "-" + UUID.randomUUID().toString().substring(0, 8) + ".json");
        try {
            write(file, data);
            clientCapture.accept(new Export(source.getServer(), level.dimension(), file, data));
            Compat.sendSuccess(source, () -> Compat.translatable("biblioteca.diagnostics.saved", file.toAbsolutePath().toString()), false);
            return 1;
        } catch (IOException error) {
            source.sendFailure(Compat.translatable("biblioteca.diagnostics.failed", error.getMessage()));
            return 0;
        }
    }

    public static void write(Path file, JsonObject data) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(data) + "\n", StandardCharsets.UTF_8);
    }
}
