package babel.biblioteca.network;

import babel.biblioteca.Compat;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

/** Full per-dimension attribute snapshot; sent on join and after configuration changes. */
public record LoopingStatePayload(Set<String> loopingDimensions, Set<String> beaconIgnoresSky,
                                  Set<String> noVoidFog, Set<String> noSkyLight)
        //? if >=1.20.5 {
        implements CustomPacketPayload
        //?}
{
    public static final Identifier ID = Compat.id("biblioteca", "looping_state");

    //? if >=1.20.5 {
    public static final Type<LoopingStatePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LoopingStatePayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), LoopingStatePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?} else {
    /*public FriendlyByteBuf toBuffer() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buffer);
        return buffer;
    }

    public static LoopingStatePayload fromBuffer(FriendlyByteBuf buffer) { return read(buffer); }
    *///?}

    private void write(FriendlyByteBuf buffer) {
        writeSet(buffer, loopingDimensions);
        writeSet(buffer, beaconIgnoresSky);
        writeSet(buffer, noVoidFog);
        writeSet(buffer, noSkyLight);
    }

    private static LoopingStatePayload read(FriendlyByteBuf buffer) {
        return new LoopingStatePayload(readSet(buffer), readSet(buffer), readSet(buffer), readSet(buffer));
    }

    private static void writeSet(FriendlyByteBuf buffer, Set<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) buffer.writeUtf(value);
    }

    private static Set<String> readSet(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        Set<String> values = new HashSet<>(count);
        for (int i = 0; i < count; i++) values.add(buffer.readUtf());
        return values;
    }
}
