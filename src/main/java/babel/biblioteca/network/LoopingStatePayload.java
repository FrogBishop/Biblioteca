package babel.biblioteca.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * S2C packet: tells the client which dimension keys have Y-looping enabled.
 * Sent on login and whenever an operator changes a dimension's looping flag.
 */
public record LoopingStatePayload(Set<String> loopingDimensions) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LoopingStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("biblioteca", "looping_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoopingStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.loopingDimensions().size());
                        for (String dim : payload.loopingDimensions()) {
                            buf.writeUtf(dim);
                        }
                    },
                    buf -> {
                        int count = buf.readVarInt();
                        Set<String> dims = new HashSet<>(count);
                        for (int i = 0; i < count; i++) {
                            dims.add(buf.readUtf());
                        }
                        return new LoopingStatePayload(dims);
                    }
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
