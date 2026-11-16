package babel.biblioteca.neoforge;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.network.LoopingStatePayload;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;

/** 1.20.4 custom payloads use a reader and write method instead of StreamCodec. */
public final class NeoForgeTransport {
    public static Consumer<LoopingStatePayload> receiver;
    private NeoForgeTransport() {}

    public static void initialize(IEventBus bus) { bus.addListener(NeoForgeTransport::register); }

    private static void register(RegisterPayloadHandlerEvent event) {
        event.registrar(Biblioteca.MOD_ID).versioned("1").play(LoopingStatePayload.ID, Payload::new,
                handlers -> handlers.client((payload, context) -> context.workHandler().execute(() -> receiver.accept(payload.state()))));
    }

    public static void send(ServerPlayer player, LoopingStatePayload payload) {
        PacketDistributor.PLAYER.with(player).send(new Payload(payload));
    }

    public record Payload(LoopingStatePayload state) implements CustomPacketPayload {
        public Payload(FriendlyByteBuf buffer) { this(LoopingStatePayload.fromBuffer(buffer)); }
        @Override public ResourceLocation id() { return LoopingStatePayload.ID; }
        @Override public void write(FriendlyByteBuf buffer) {
            FriendlyByteBuf encoded = state.toBuffer();
            try { buffer.writeBytes(encoded); } finally { encoded.release(); }
        }
    }
}
