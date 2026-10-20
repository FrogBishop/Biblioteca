package babel.biblioteca.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * S2C packet: the per-dimension attributes of this mod, as dimension key strings.
 *
 *   loopingDimensions   dimensions whose Y axis wraps around
 *   beaconIgnoresSky    dimensions whose beacon beams skip the sky check
 *   voidFog             dimensions that show the vanilla void-fog darkening
 *   skyLight            dimensions that keep the vanilla sky-light channel
 *
 * Sent on login and whenever an operator changes a dimension's attributes.
 */
public record LoopingStatePayload(
        Set<String> loopingDimensions,
        Set<String> beaconIgnoresSky,
        Set<String> voidFog,
        Set<String> skyLight) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LoopingStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("biblioteca", "looping_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoopingStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        writeSet(buf, payload.loopingDimensions());
                        writeSet(buf, payload.beaconIgnoresSky());
                        writeSet(buf, payload.voidFog());
                        writeSet(buf, payload.skyLight());
                    },
                    buf -> new LoopingStatePayload(readSet(buf), readSet(buf), readSet(buf), readSet(buf))
            );

    private static void writeSet(RegistryFriendlyByteBuf buf, Set<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }

    private static Set<String> readSet(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Set<String> values = new HashSet<>(count);
        for (int i = 0; i < count; i++) {
            values.add(buf.readUtf());
        }
        return values;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
