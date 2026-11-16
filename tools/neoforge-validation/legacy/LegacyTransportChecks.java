package babel.biblioteca.validation.neoforge;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.validation.SeamFixture;
import io.netty.buffer.Unpooled;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Exercises each loader's registered codec and handler; no replacement transport is installed. */
public final class LegacyTransportChecks {
    private static final String NETWORK = "net.neoforged.neoforge.network.";
    private static Object field(Object object, String name) throws Exception {
        Class<?> type = object instanceof Class<?> cls ? cls : object.getClass();
        var field = type.getDeclaredField(name); field.setAccessible(true);
        return field.get(object instanceof Class<?> ? null : object);
    }
    private static Object call(Object target, String name, Object... args) throws Exception {
        for (var method : target.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length
                && !method.isBridge()) {
            method.setAccessible(true); return method.invoke(target, args);
        }
        throw new NoSuchMethodException(name);
    }

    public static void run(boolean client) throws Exception {
        var payload = new LoopingStatePayload(Set.of("test:orbit"), Set.of("minecraft:the_nether"), Set.of("test:orbit"), Map.of("test:orbit", SkyLightMode.HYBRID));
        boolean channel = !System.getProperty("biblioteca.validation.minecraft").equals("1.20.4");
        var transport = Class.forName("babel.biblioteca.neoforge.NeoForgeTransport");
        if (channel) channel(transport, payload, client); else payload(payload, client);
        ServerPlayer player = (ServerPlayer) SeamFixture.allocate(ServerPlayer.class);
        var id = UUID.fromString("bd169bda-fc43-4a76-8357-d2d1d2b2187e"); player.setUUID(id);
        var profile = net.minecraft.world.entity.player.Player.class.getDeclaredField("gameProfile"); profile.setAccessible(true);
        profile.set(player, new com.mojang.authlib.GameProfile(id, "BibliotecaTest"));
        player.connection = (CaptureListener) SeamFixture.allocate(CaptureListener.class);
        // The original loader also synchronizes tier data on login and inspects the connection.
        // Supply an actual in-memory Netty channel instead of leaving unrelated native listeners
        // with a null connection while exercising Biblioteca's real event registration.
        var connection = new CaptureConnection();
        var channelField = net.minecraft.network.Connection.class.getDeclaredField("channel");
        channelField.setAccessible(true); channelField.set(connection, new io.netty.channel.embedded.EmbeddedChannel());
        for (Class<?> owner = player.connection.getClass(); owner != null; owner = owner.getSuperclass())
            for (var field : owner.getDeclaredFields()) if (field.getType() == net.minecraft.network.Connection.class) {
                field.setAccessible(true); field.set(player.connection, connection);
            }
        CaptureListener.last = null;
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        if (CaptureListener.last == null) throw new AssertionError("Login did not send native payload packet");
        CaptureListener.last = null; Biblioteca.sendState(player, payload);
        if (CaptureListener.last == null || !CaptureListener.last.getClass().getSimpleName().equals("ClientboundCustomPayloadPacket"))
            throw new AssertionError("Update transport did not send native clientbound payload");
        System.out.println("DATA_OK NeoForge legacy transport: registered codec/reader, client-only direction, native handler, login and update packets");
    }

    private static void channel(Class<?> transport, LoopingStatePayload payload, boolean client) throws Exception {
        Object channel = field(transport, "channel"), codec = field(channel, "indexedCodec");
        Object registration = ((Map<?, ?>) field(codec, "types")).get(LoopingStatePayload.class);
        Object direction = ((Optional<?>) field(registration, "networkDirection")).orElseThrow();
        if (!direction.toString().equals("PLAY_TO_CLIENT")) throw new AssertionError("Wrong channel direction");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            call(channel, "encodeMessage", payload, buffer);
            // Native IndexedMessageCodec writes its discriminator before the registered decoder.
            buffer.readUnsignedByte();
            Object decoder = ((Optional<?>) field(registration, "decoder")).orElseThrow();
            Object decoded = decoder instanceof Function<?, ?> f ? ((Function<FriendlyByteBuf, ?>) f).apply(buffer) : call(decoder, "decode", buffer);
            if (!payload.equals(decoded) || buffer.isReadable()) throw new AssertionError("Registered channel codec round trip");
            if (client) {
                buffer.clear(); call(channel, "encodeMessage", payload, buffer);
                Class<?> provider = Class.forName("net.neoforged.neoforge.common.util.LogicalSidedProvider");
                provider.getMethod("setClient", Supplier.class).invoke(null, (Supplier<Object>) () -> net.minecraft.client.Minecraft.getInstance());
                Class<?> contextType = Class.forName(NETWORK + "NetworkEvent$Context");
                Constructor<?> constructor = Arrays.stream(contextType.getDeclaredConstructors())
                        .filter(c -> c.getParameterCount() == 3 && c.getParameterTypes()[2] == int.class).findFirst().orElseThrow();
                constructor.setAccessible(true);
                Object context = constructor.newInstance(null, direction, 0);
                Method consume = Arrays.stream(codec.getClass().getDeclaredMethods()).filter(m -> m.getName().equals("consume")).findFirst().orElseThrow();
                consume.setAccessible(true);
                Object argument = consume.getParameterTypes()[2] == Supplier.class ? (Supplier<Object>) () -> context : context;
                consume.invoke(codec, buffer, 0, argument);
                if (buffer.isReadable()) throw new AssertionError("Native handler did not consume its payload");
            }
        } finally { buffer.release(); }
    }

    private static void payload(LoopingStatePayload payload, boolean client) throws Exception {
        Class<?> registry = Class.forName(NETWORK + "registration.NetworkRegistry");
        Object instance = registry.getMethod("getInstance").invoke(null);
        Object registration = ((Map<?, ?>) field(instance, "knownPlayRegistrations")).get(LoopingStatePayload.ID);
        if (!call(registration, "flow").equals(Optional.of(PacketFlow.CLIENTBOUND))) throw new AssertionError("Wrong payload direction");
        Class<?> type = Class.forName("babel.biblioteca.neoforge.NeoForgeTransport$Payload");
        Object wrapper = type.getConstructor(LoopingStatePayload.class).newInstance(payload);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            type.getMethod("write", FriendlyByteBuf.class).invoke(wrapper, buffer);
            Object decoded = call(registration, "apply", buffer);
            if (!payload.equals(call(decoded, "state")) || buffer.isReadable()) throw new AssertionError("Registered payload reader round trip");
            if (client) {
                int[] enqueued = {0};
                Class<?> work = Class.forName(NETWORK + "handling.ISynchronizedWorkHandler");
                Object worker = Proxy.newProxyInstance(work.getClassLoader(), new Class<?>[]{work}, (proxy, method, args) -> {
                    if (method.getName().equals("execute") && args[0] instanceof Runnable task) { enqueued[0]++; task.run(); return null; }
                    throw new AssertionError("Unexpected worker method " + method);
                });
                Class<?> context = Class.forName(NETWORK + "handling.PlayPayloadContext");
                Object ctx = context.getConstructors()[0].newInstance(null, null, worker, PacketFlow.CLIENTBOUND, null, Optional.empty());
                call(registration, "handle", decoded, ctx);
                if (enqueued[0] != 1) throw new AssertionError("Payload did not enqueue exactly once");
            }
        } finally { buffer.release(); }
    }

    private static final class CaptureListener extends ServerGamePacketListenerImpl {
        static Packet<?> last;
        private CaptureListener() { super(null, null, null, null); }
        @Override public void send(Packet<?> packet) { last = packet; }
    }
    private static final class CaptureConnection extends net.minecraft.network.Connection {
        private CaptureConnection() { super(PacketFlow.SERVERBOUND); }
        @Override public void send(Packet<?> packet) { CaptureListener.last = packet; }
    }
}
