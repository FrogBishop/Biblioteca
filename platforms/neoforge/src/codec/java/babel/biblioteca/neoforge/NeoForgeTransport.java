package babel.biblioteca.neoforge;

import babel.biblioteca.network.LoopingStatePayload;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 1.20.5–1.21.6 register their client handler alongside the codec on both physical sides. */
public final class NeoForgeTransport {
    public static Consumer<LoopingStatePayload> receiver;
    private NeoForgeTransport() {}

    public static void initialize(IEventBus bus) { bus.addListener(NeoForgeTransport::register); }

    private static void register(RegisterPayloadHandlersEvent event) {
        // PayloadRegistrar's default MAIN handler thread performs the enqueue before this callback.
        event.registrar("1").playToClient(LoopingStatePayload.TYPE, LoopingStatePayload.STREAM_CODEC,
                (payload, context) -> receiver.accept(payload));
    }

    public static void send(ServerPlayer player, LoopingStatePayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
