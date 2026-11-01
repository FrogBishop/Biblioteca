package babel.biblioteca.validation;

import babel.biblioteca.SeamEffects;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Native effect packet bytes for distinct observers; only the socket transport is captured. */
public final class EffectBroadcastChecks implements Opcodes {
    private static Class<?> worldType, connectionType;
    public static void run() throws Exception {
        createTypes();
        for (int period : new int[]{16, SeamFixture.hasSections() ? 384 : 256}) {
            int min = SeamFixture.hasSections() ? -64 : 0, top = min + period;
            ServerLevel world = (ServerLevel) SeamFixture.allocate(worldType);
            put(world, "minimum", min); put(world, "period", period); put(world, "looping", true);
            ServerPlayer upper = player(world, top - 0.25), sameSide = player(world, top - 1.25);
            ServerPlayer lower = player(world, min + 0.25), distant = player(world, min + period / 2.0);
            put(world, "testPlayers", List.of(upper, sameSide, lower, distant));
            AtomicInteger created = new AtomicInteger();
            java.util.function.DoubleFunction<Packet<?>> factory = y -> {
                created.incrementAndGet();
                return new ClientboundLevelEventPacket(2001, new BlockPos(0, (int) y, 0), 17, false);
            };
            check(SeamEffects.broadcastPerListener(world, null, 0, top - 1, 0, 2, factory), "periodic broadcast not handled");
            check(packets(upper).size() == 1 && packets(sameSide).size() == 1 && packets(lower).size() == 1 && packets(distant).isEmpty(), "observer radius selection");
            Packet<?> upperPacket = packets(upper).get(0), lowerPacket = packets(lower).get(0);
            check(upperPacket == packets(sameSide).get(0) && upperPacket != lowerPacket && created.get() == 2,
                    "packets must be shared only among observers using the same coordinate frame");
            check(position(upperPacket).getY() == top - 1 && position(lowerPacket).getY() == min - 1, "shared packet was mutated for another observer");
            check(position(roundTrip(upperPacket)).equals(position(upperPacket)) && position(roundTrip(lowerPacket)).equals(position(lowerPacket)), "native packet serialization changed the chosen image");
            clear(upper, sameSide, lower, distant); created.set(0);
            int count = SeamEffects.broadcastParticles(world, 0, top - 1, 0, 2, factory::apply);
            check(count == 3 && created.get() == 2 && packets(distant).isEmpty(), "particle observer selection/cache");
            clear(upper, sameSide, lower, distant);
            SeamEffects.broadcastPerListener(world, upper, 0, top - 1, 0, 2, factory);
            check(packets(upper).isEmpty() && packets(sameSide).size() == 1 && packets(lower).size() == 1, "except player was not respected");
            clear(upper, sameSide, lower, distant); put(world, "looping", false);
            check(!SeamEffects.broadcastPerListener(world, null, 0, top - 1, 0, 2, factory), "ordinary broadcast must delegate to vanilla");
            check(packets(upper).isEmpty() && packets(lower).isEmpty(), "ordinary helper sent duplicate packets");
        }
        System.out.println("PASS: independent observer frames, shared-packet immutability, native packet round trips, particle recipients and ordinary-world delegation");
    }

    private static ServerPlayer player(ServerLevel level, double y) throws Exception {
        ServerPlayer player = (ServerPlayer) SeamFixture.allocate(ServerPlayer.class);
        put(player, "level", level); put(player, "position", new Vec3(0, y, 0));
        put(player, "blockPosition", new BlockPos(0, (int) Math.floor(y), 0));
        player.connection = (ServerGamePacketListenerImpl) SeamFixture.allocate(connectionType);
        put(player.connection, "captured", new ArrayList<Packet<?>>());
        return player;
    }
    @SuppressWarnings("unchecked") private static List<Packet<?>> packets(ServerPlayer p) throws Exception { return (List<Packet<?>>) field(connectionType, "captured").get(p.connection); }
    private static void clear(ServerPlayer... players) throws Exception { for (ServerPlayer p : players) packets(p).clear(); }
    private static BlockPos position(Packet<?> packet) throws Exception {
        for (var method : packet.getClass().getMethods()) if (method.getReturnType() == BlockPos.class && method.getParameterCount() == 0) return (BlockPos) method.invoke(packet);
        for (Field f : packet.getClass().getDeclaredFields()) if (f.getType() == BlockPos.class) { f.setAccessible(true); return (BlockPos) f.get(packet); }
        throw new AssertionError("No native packet position accessor");
    }
    private static Packet<?> roundTrip(Packet<?> packet) throws Exception {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            try {
                var write = packet.getClass().getDeclaredMethod("write", FriendlyByteBuf.class); write.setAccessible(true); write.invoke(packet, buffer);
                try {
                    var read = packet.getClass().getDeclaredConstructor(FriendlyByteBuf.class); read.setAccessible(true);
                    return (Packet<?>) read.newInstance(buffer);
                } catch (NoSuchMethodException legacy) {
                    Object copy = SeamFixture.allocate(packet.getClass());
                    var read = packet.getClass().getDeclaredMethod("read", FriendlyByteBuf.class); read.setAccessible(true); read.invoke(copy, buffer);
                    return (Packet<?>) copy;
                }
            } catch (NoSuchMethodException codecEra) {
                Object codec = packet.getClass().getField("STREAM_CODEC").get(null);
                Class<?> api = Class.forName("net.minecraft.network.codec.StreamCodec");
                api.getMethod("encode", Object.class, Object.class).invoke(codec, buffer, packet);
                return (Packet<?>) api.getMethod("decode", Object.class).invoke(codec, buffer);
            }
        } finally { buffer.release(); }
    }
    private static void createTypes() throws Exception {
        String name = "babel/biblioteca/validation/EffectTestWorld";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(V17, ACC_PUBLIC, name, null, Type.getInternalName(ServerLevel.class), null);
        for (String f : new String[]{"minimum", "period", "looping"}) w.visitField(ACC_PUBLIC, f, f.equals("looping") ? "Z" : "I", null, null).visitEnd();
        w.visitField(ACC_PUBLIC, "testPlayers", "Ljava/util/List;", null, null).visitEnd();
        getter(w, name, "getMinY", "minimum", "I"); getter(w, name, "getMinBuildHeight", "minimum", "I");
        getter(w, name, "getHeight", "period", "I"); getter(w, name, "getMaxBuildHeight", "period", "I");
        getter(w, name, "biblioteca$isLooping", "looping", "Z"); getter(w, name, "players", "testPlayers", "Ljava/util/List;");
        w.visitEnd(); worldType = MethodHandles.lookup().defineClass(w.toByteArray());
        name = "babel/biblioteca/validation/EffectCaptureConnection";
        w = new ClassWriter(ClassWriter.COMPUTE_MAXS); w.visit(V17, ACC_PUBLIC, name, null, Type.getInternalName(ServerGamePacketListenerImpl.class), null);
        w.visitField(ACC_PUBLIC, "captured", "Ljava/util/List;", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(ACC_PUBLIC, "send", "(" + Type.getDescriptor(Packet.class) + ")V", null, null); m.visitCode();
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, name, "captured", "Ljava/util/List;"); m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true); m.visitInsn(POP); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        w.visitEnd(); connectionType = MethodHandles.lookup().defineClass(w.toByteArray());
    }
    private static void getter(ClassWriter w, String owner, String name, String f, String desc) { MethodVisitor m = w.visitMethod(ACC_PUBLIC, name, "()" + desc, null, null); m.visitCode(); m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, owner, f, desc); m.visitInsn(desc.length() == 1 ? IRETURN : ARETURN); m.visitMaxs(0, 0); m.visitEnd(); }
    private static Field field(Class<?> type, String name) throws Exception { for (Class<?> c = type; c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) { } throw new NoSuchFieldException(name); }
    private static void put(Object value, String name, Object data) throws Exception { field(value.getClass(), name).set(value, data); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
