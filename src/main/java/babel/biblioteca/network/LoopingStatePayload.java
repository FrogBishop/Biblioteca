package babel.biblioteca.network;

import babel.biblioteca.Compat;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/*import net.minecraft.network.FriendlyByteBuf;
*///?}
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

//? if >=1.20.5 {
/**
 * S2C packet: the per-dimension attributes of this mod, as dimension key strings.
 *
 *   loopingDimensions   dimensions whose Y axis wraps around
 *   beaconIgnoresSky    dimensions whose beacon beams skip the sky check
 *
 * Sent on login and whenever an operator changes a dimension's attributes.
 */
public record LoopingStatePayload(Set<String> loopingDimensions, Set<String> beaconIgnoresSky)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LoopingStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Compat.id("biblioteca", "looping_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoopingStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        writeSet(buf, payload.loopingDimensions());
                        writeSet(buf, payload.beaconIgnoresSky());
                    },
                    buf -> new LoopingStatePayload(readSet(buf), readSet(buf))
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
//?} else {
/*// S2C packet for the pre-1.20.5 custom-payload channel.
public record LoopingStatePayload(Set<String> loopingDimensions, Set<String> beaconIgnoresSky) {

    public static final Identifier ID = Compat.id("biblioteca", "looping_state");

    public FriendlyByteBuf toBuffer() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeVarInt(loopingDimensions.size());
        for (String value : loopingDimensions) {
            buf.writeUtf(value);
        }
        buf.writeVarInt(beaconIgnoresSky.size());
        for (String value : beaconIgnoresSky) {
            buf.writeUtf(value);
        }
        return buf;
    }

    public static LoopingStatePayload fromBuffer(FriendlyByteBuf buf) {
        int loopingCount = buf.readVarInt();
        Set<String> looping = new HashSet<>(loopingCount);
        for (int i = 0; i < loopingCount; i++) {
            looping.add(buf.readUtf());
        }
        int beaconCount = buf.readVarInt();
        Set<String> beacon = new HashSet<>(beaconCount);
        for (int i = 0; i < beaconCount; i++) {
            beacon.add(buf.readUtf());
        }
        return new LoopingStatePayload(looping, beacon);
    }
}
*///?}
