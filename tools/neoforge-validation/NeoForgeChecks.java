package babel.biblioteca.validation.neoforge;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.validation.*;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.TagLoader;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Runs the existing gameplay regressions inside FML, plus the new loader-specific bindings. */
public final class NeoForgeChecks {
    public static void run(Path classes, boolean client, boolean noRender) throws Exception {
        try (AutoCloseable fixture = client ? NeoForgeClientChecks.installMinecraftFixture() : () -> {}) {
            runChecks(classes, client, noRender);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void runChecks(Path classes, boolean client, boolean noRender) throws Exception {
        String version = ModList.get().getModContainerById(Biblioteca.MOD_ID).orElseThrow().getModInfo().getVersion().toString();
        if (!version.equals(System.getProperty("biblioteca.validation.version"))) {
            throw new AssertionError("Wrong packaged mod loaded: " + version);
        }
        ApiChecks.run(classes);
        CoreChecks.run(client);
        LightSyncChecks.run();
        if (System.getProperty("biblioteca.validation.minecraft").equals("1.21.11")) {
            Class.forName("babel.biblioteca.validation.neoforge12111.SnowGolemAimChecks").getMethod("run").invoke(null);
        }

        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var commandContext = CommandBuildContext.simple(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
        NeoForge.EVENT_BUS.post(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, commandContext));
        var command = dispatcher.getRoot().getChild("biblioteca");
        if (command == null || command.getChild("looping") == null || command.getChild("sky_light") == null
                || command.getChild("beacon") == null || command.getChild("no_void_fog") == null) {
            throw new AssertionError("NeoForge command event did not register the full command tree");
        }
        StreamCodec codec = NetworkRegistry.getCodec(LoopingStatePayload.ID, ConnectionProtocol.PLAY, PacketFlow.CLIENTBOUND);
        if (codec == null || NetworkRegistry.getCodec(LoopingStatePayload.ID, ConnectionProtocol.PLAY, PacketFlow.SERVERBOUND) != null) {
            throw new AssertionError("Payload must be registered exclusively as play/clientbound");
        }
        var payload = new LoopingStatePayload(Set.of("test:orbit"), Set.of("minecraft:the_nether"), Set.of("test:orbit"),
                Map.of("test:orbit", SkyLightMode.HYBRID));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, payload);
            if (!payload.equals(codec.decode(buffer)) || buffer.isReadable()) throw new AssertionError("Registered network codec round trip");
        } finally { buffer.release(); }
        ServerPlayer player = (ServerPlayer) SeamFixture.allocate(ServerPlayer.class);
        var playerId = java.util.UUID.fromString("bd169bda-fc43-4a76-8357-d2d1d2b2187e");
        player.setUUID(playerId);
        var profile = net.minecraft.world.entity.player.Player.class.getDeclaredField("gameProfile");
        profile.setAccessible(true);
        profile.set(player, new com.mojang.authlib.GameProfile(playerId, "BibliotecaTest"));
        player.connection = (CaptureListener) SeamFixture.allocate(CaptureListener.class);
        CaptureListener.lastPacket = null;
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        checkPacket(Biblioteca.currentPayload(), "login event");
        Biblioteca.sendState(player, payload);
        checkPacket(payload, "state update transport");
        System.out.println("DATA_OK NeoForge lifecycle: packaged entrypoint, statistic, command event, login sync, state transport and clientbound network codec");

        SeamChecks.run(client && !noRender);
        SkyChecks.run();
        CommandChecks.run();
        boolean currentRenderer = System.getProperty("biblioteca.validation.minecraft").equals("26.3");
        if (currentRenderer) {
            BuiltInRegistries.REGISTRY.forEach(registry -> reloadEmptyTags(registry));
            var checks = Class.forName("babel.biblioteca.validation.main100.SeamChecks");
            checks.getMethod("runWithPreparedRegistries", boolean.class).invoke(null, client && !noRender);
            for (String name : new String[]{"MobTargetingChecks", "DimensionCommandChecks", "SkyLightSettingsChecks", "SkyLightChecks"}) {
                runCurrentChecks(name);
            }
        }
        if (client) {
            NeoForgeClientChecks.run(payload);
            DimensionModelChecks.run();
            if (currentRenderer) runCurrentChecks("DimensionConfigChecks");
            if (!noRender) {
                if (currentRenderer) {
                    runCurrentChecks("SeamClientChecks");
                    runCurrentChecks("SkyLightClientChecks");
                }
                TerrainChecks.run();
                if (currentRenderer) Class.forName("babel.biblioteca.validation.RenderingChecks").getMethod("run").invoke(null);
            }
        }
    }

    private static void runCurrentChecks(String name) throws Exception {
        Class.forName("babel.biblioteca.validation.main100." + name).getMethod("run").invoke(null);
    }

    private static <T> void reloadEmptyTags(net.minecraft.core.Registry<T> registry) {
        registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(), Map.of())).apply();
    }

    private static void checkPacket(LoopingStatePayload expected, String action) {
        if (!(CaptureListener.lastPacket instanceof ClientboundCustomPayloadPacket packet) || !expected.equals(packet.payload())) {
            throw new AssertionError("Incorrect packet from " + action);
        }
    }

    private static final class CaptureListener extends ServerGamePacketListenerImpl {
        static Packet<?> lastPacket;
        private CaptureListener() { super(null, null, null, null); }
        @Override public void send(Packet<?> packet) { lastPacket = packet; }
    }
}
