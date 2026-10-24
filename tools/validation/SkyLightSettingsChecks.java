package babel.biblioteca.validation;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.world.LoopingDimensionManager;
import babel.biblioteca.world.LoopingWorldData;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static babel.biblioteca.validation.SeamChecks.check;

/** Native SavedData and packet codecs, including old settings and non-vanilla dimension IDs. */
public final class SkyLightSettingsChecks {
    public static void run() throws Exception {
        ResourceKey<Level> custom = ResourceKey.create(Registries.DIMENSION, Identifier.parse("example:sky_islands"));
        LoopingWorldData legacy = read("""
                {"looping":["minecraft:overworld"],"beacon_ignores_sky":["minecraft:the_nether"],
                 "no_void_fog":["minecraft:overworld"],"no_sky_light":["minecraft:the_nether","example:sky_islands"]}
                """);
        check(legacy.getSkyLightMode(Level.OVERWORLD) == SkyLightMode.LOOPING, "old false sky setting stays periodic");
        check(legacy.getSkyLightMode(Level.NETHER) == SkyLightMode.DISABLED && legacy.getSkyLightMode(custom) == SkyLightMode.DISABLED, "old true sky settings migrate, including custom dimensions");
        check(legacy.getLoopingDimensions().equals(Set.of(Level.OVERWORLD)) && legacy.getNoVoidFogDimensions().equals(Set.of(Level.OVERWORLD)), "migration preserves independent attributes");
        check(read("[\"minecraft:overworld\"]").getSkyLightMode(Level.OVERWORLD) == SkyLightMode.LOOPING, "old bare-list worlds keep the sky default");
        check(read("{}").getSkyLightMode(custom) == SkyLightMode.DEFAULT, "missing modes use the established default");
        check(read("{\"looping\":[\"minecraft:overworld\"],\"sky_light\":[\"minecraft:overworld\"]}")
                .getLoopingDimensions().contains(Level.OVERWORLD), "pre-0.67 legacy sky_light list cannot conflict with the new mode map");
        LoopingWorldData overridden = read("""
                {"no_sky_light":["minecraft:overworld","example:sky_islands"],
                 "sky_light_mode":{"minecraft:overworld":"looping","example:sky_islands":"hybrid"}}
                """);
        check(overridden.getSkyLightMode(Level.OVERWORLD) == SkyLightMode.LOOPING && overridden.getSkyLightMode(custom) == SkyLightMode.HYBRID, "explicit new settings override legacy disabled entries");
        legacy.setSkyLightMode(custom, SkyLightMode.HYBRID);
        var json = LoopingWorldData.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow();
        check(!json.getAsJsonObject().has("no_sky_light"), "new saves write the enum map without legacy booleans");
        LoopingWorldData restored = LoopingWorldData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        check(restored.getSkyLightModes().equals(legacy.getSkyLightModes()), "sky modes survive save/reload");
        check(LoopingWorldData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"sky_light_mode\":{\"minecraft:overworld\":\"invalid\"}}")).error().isPresent(), "invalid persisted modes fail explicitly");

        var payload = new LoopingStatePayload(Set.of("minecraft:overworld", "example:sky_islands"), Set.of(), Set.of(),
                Map.of("minecraft:overworld", SkyLightMode.LOOPING, "minecraft:the_nether", SkyLightMode.DISABLED, "example:sky_islands", SkyLightMode.HYBRID));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LoopingStatePayload.STREAM_CODEC.encode(buffer, payload);
            check(LoopingStatePayload.STREAM_CODEC.decode(buffer).equals(payload) && !buffer.isReadable(), "native packet codec preserves all three modes and custom IDs");
        } finally { buffer.release(); }

        Field field = LoopingDimensionManager.class.getDeclaredField("serverData"); field.setAccessible(true);
        Object old = field.get(null);
        var publish = LoopingDimensionManager.class.getDeclaredMethod("republishSnapshots"); publish.setAccessible(true);
        try {
            field.set(null, restored); publish.invoke(null);
            int revision = LoopingRegistry.revision();
            for (SkyLightMode mode : SkyLightMode.values()) {
                LoopingDimensionManager.setSkyLightMode(custom, mode);
                check(LoopingDimensionManager.getSkyLightMode(custom) == mode && restored.getSkyLightMode(custom) == mode, "manager publishes and persists each mode");
                check(Biblioteca.currentPayload().skyLightModes().getOrDefault("example:sky_islands", SkyLightMode.DEFAULT) == mode, "command broadcast snapshot carries the selected mode");
            }
            check(LoopingRegistry.revision() > revision, "mode edits invalidate worker caches");
        } finally { field.set(null, old); publish.invoke(null); LoopingRegistry.bumpRevision(); }
        System.out.println("PASS: three-mode SavedData migration, precedence/defaults, codec round trips, custom dimension packets and server snapshots");
    }

    private static LoopingWorldData read(String json) {
        return LoopingWorldData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}
