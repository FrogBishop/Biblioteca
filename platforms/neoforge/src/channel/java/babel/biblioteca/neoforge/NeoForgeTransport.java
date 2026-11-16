package babel.biblioteca.neoforge;

import babel.biblioteca.network.LoopingStatePayload;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.PlayNetworkDirection;
import net.neoforged.neoforge.network.simple.SimpleChannel;

/** 1.20.1–1.20.3 use the native, version-negotiated SimpleChannel transport. */
public final class NeoForgeTransport {
    public static Consumer<LoopingStatePayload> receiver;
    private static SimpleChannel channel;
    private NeoForgeTransport() {}

    public static void initialize(IEventBus bus) {
        channel = NetworkRegistry.ChannelBuilder.named(LoopingStatePayload.ID).networkProtocolVersion(() -> "1")
                .clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals).simpleChannel();
        channel.messageBuilder(LoopingStatePayload.class, 0, PlayNetworkDirection.PLAY_TO_CLIENT)
                .encoder(NeoForgeTransport::write).decoder(LoopingStatePayload::fromBuffer)
                .consumerMainThread((payload, context) -> receiver.accept(payload)).add();
    }

    private static void write(LoopingStatePayload payload, FriendlyByteBuf buffer) {
        FriendlyByteBuf encoded = payload.toBuffer();
        try { buffer.writeBytes(encoded); } finally { encoded.release(); }
    }

    public static void send(ServerPlayer player, LoopingStatePayload payload) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
