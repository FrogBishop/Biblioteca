package babel.biblioteca.validation;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.PendingLoopingConfig;
import babel.biblioteca.SeamPositions;
import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.world.LoopingWorldData;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Executes against each node's real Minecraft classes inside the Fabric class loader. */
public final class CoreChecks {
    public static void run(boolean client) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        new babel.biblioteca.Biblioteca().onInitialize();
        check(net.minecraft.core.registries.BuiltInRegistries.CUSTOM_STAT.containsKey(
                babel.biblioteca.Compat.id("biblioteca", "seam_crossings")), "crossing statistic registration");
        if (client) {
            Class<?> initializer = Class.forName("babel.biblioteca.client.BibliotecaClient");
            initializer.getMethod("onInitializeClient").invoke(initializer.getConstructor().newInstance());
        }
        var defaults = LoopingWorldData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).result().orElseThrow();
        check(defaults.getLoopingDimensions().isEmpty() && defaults.getBeaconIgnoresSky().isEmpty()
                && defaults.getNoVoidFogDimensions().isEmpty() && defaults.getNoSkyLightDimensions().isEmpty(), "new-world defaults");
        var data = new LoopingWorldData(Set.of(Level.OVERWORLD), Set.of(Level.NETHER), Set.of(Level.END), Set.of(Level.NETHER));
        var encoded = LoopingWorldData.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow().getAsJsonObject();
        check(encoded.has("no_void_fog") && encoded.has("no_sky_light") && !encoded.has("void_fog") && !encoded.has("sky_light"), "attribute names");
        var restored = LoopingWorldData.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        check(restored.isLooping(Level.OVERWORLD) && restored.beaconIgnoresSky(Level.NETHER), "saved topology/beacon fields");
        check(restored.getNoVoidFogDimensions().equals(Set.of(Level.END)) && restored.getNoSkyLightDimensions().equals(Set.of(Level.NETHER)), "saved lighting fields");
        restored.setNoVoidFog(Level.END, false);
        restored.setNoSkyLight(Level.NETHER, false);
        check(restored.isDirty() && restored.getNoVoidFogDimensions().isEmpty() && restored.getNoSkyLightDimensions().isEmpty(), "false restores vanilla flags");
        Set<ResourceKey<Level>> snapshot = data.getLoopingDimensions();
        data.setLooping(Level.OVERWORLD, false);
        check(snapshot.contains(Level.OVERWORLD), "save snapshot must not be a live view");

        var payload = new LoopingStatePayload(Set.of("minecraft:overworld", "test:short"), Set.of("minecraft:the_nether"),
                Set.of("minecraft:the_end"), Set.of("test:short"));
        Method write = LoopingStatePayload.class.getDeclaredMethod("write", FriendlyByteBuf.class);
        Method read = LoopingStatePayload.class.getDeclaredMethod("read", FriendlyByteBuf.class);
        write.setAccessible(true);
        read.setAccessible(true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            write.invoke(payload, buffer);
            check(payload.equals(read.invoke(null, buffer)) && buffer.readableBytes() == 0, "four-field wire round trip");
        } finally { buffer.release(); }
        Set<ResourceKey<Level>> pending = new HashSet<>(Set.of(Level.OVERWORLD));
        PendingLoopingConfig.set(pending, Set.of(Level.NETHER), Set.of(Level.END), Set.of());
        pending.clear();
        check(PendingLoopingConfig.hasPending() && PendingLoopingConfig.getPendingLooping().contains(Level.OVERWORLD), "GUI pending snapshot");
        check(PendingLoopingConfig.getPendingNoVoidFog().contains(Level.END) && PendingLoopingConfig.getPendingNoSkyLight().isEmpty(), "GUI lighting snapshot");
        PendingLoopingConfig.consume();
        check(!PendingLoopingConfig.hasPending() && PendingLoopingConfig.getPendingLooping().isEmpty()
                && PendingLoopingConfig.getPendingBeaconIgnoresSky().isEmpty() && PendingLoopingConfig.getPendingNoVoidFog().isEmpty(), "pending lifecycle");

        for (int period : new int[]{16, 128, 384, 1024}) {
            for (int y = -1700; y < 1700; y += 7) {
                double reference = y * 0.71 + 0.125;
                double folded = SeamPositions.foldToward(y + 0.375, reference, period);
                check(Math.abs(folded - reference) <= period * 0.5 + 1e-9, "nearest periodic distance");
                check(Math.abs((folded - y - 0.375) / period - Math.rint((folded - y - 0.375) / period)) < 1e-9, "fraction/period preservation");
            }
        }
        if (client) checkClient(payload);
        System.out.println("DATA_OK core: codec defaults/roundtrip, packet fields, pending lifecycle, coordinate folding" + (client ? ", client sync/disconnect" : ""));
    }

    private static void checkClient(LoopingStatePayload payload) throws Exception {
        Class<?> cache = Class.forName("babel.biblioteca.client.LoopingDimensionCache");
        int revision = LoopingRegistry.revision();
        cache.getMethod("applyPayload", LoopingStatePayload.class).invoke(null, payload);
        check(LoopingRegistry.revision() != revision, "payload invalidates level cache");
        check((boolean) cache.getMethod("isLooping", ResourceKey.class).invoke(null, Level.OVERWORLD), "client loop snapshot");
        check((boolean) cache.getMethod("hasNoVoidFog", ResourceKey.class).invoke(null, Level.END), "client fog snapshot");
        cache.getMethod("applyPayload", LoopingStatePayload.class).invoke(null, new LoopingStatePayload(Set.of(), Set.of(), Set.of(), Set.of()));
        check(!(boolean) cache.getMethod("hasNoVoidFog", ResourceKey.class).invoke(null, Level.END), "payload replaces previous flags");
        cache.getMethod("applyPayload", LoopingStatePayload.class).invoke(null, payload);
        cache.getMethod("clear").invoke(null);
        check(!(boolean) cache.getMethod("isLooping", ResourceKey.class).invoke(null, Level.OVERWORLD), "disconnect clears flags");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
