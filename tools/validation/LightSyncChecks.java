package babel.biblioteca.validation;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.world.LoopingDimensionManager;
import babel.biblioteca.world.LoopingWorldData;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.BitSet;
import java.util.List;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Executes native ChunkHolder broadcasting with border and interior chunk watchers. */
public final class LightSyncChecks implements Opcodes {
    private static Class<?> captureConnection, testChunk;
    public static void run() throws Exception {
        var data = LoopingDimensionManager.class.getDeclaredField("serverData");
        data.setAccessible(true);
        check(data.get(null) == null, "run this isolated check without an active server world");
        data.set(null, LoopingWorldData.empty());
        try { verifyBroadcasts(); }
        finally { LoopingDimensionManager.onServerStopped(); }
    }

    private static void verifyBroadcasts() throws Exception {
        Level level = SeamFixture.create(SeamFixture.hasSections() ? -64 : 0, SeamFixture.hasSections() ? 384 : 256);
        var dimension = Level.class.getDeclaredField("dimension");
        dimension.setAccessible(true);
        dimension.set(level, Level.OVERWORLD);
        var looping = level.getClass().getField("looping");
        looping.setBoolean(level, false);
        level.getClass().getField("client").setBoolean(level, false);
        LoopingRegistry.bumpRevision();
        if (testChunk == null) testChunk = testChunkClass();
        LevelChunk chunk = (LevelChunk) SeamFixture.allocate(testChunk);
        testChunk.getField("testLevel").set(chunk, level);
        LightChunkGetter source = (LightChunkGetter) Proxy.newProxyInstance(LightChunkGetter.class.getClassLoader(),
                new Class<?>[]{LightChunkGetter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getLevel")) return level;
                    if (method.getName().equals("getChunkForLighting") || method.getName().equals("onLightUpdate")) return null;
                    throw new AssertionError("Unexpected light source method: " + method);
                });
        var engine = new LevelLightEngine(source, true, true);
        ServerPlayer border = player(), interior = player();
        ChunkHolder.PlayerProvider players = (ChunkHolder.PlayerProvider) Proxy.newProxyInstance(
                ChunkHolder.PlayerProvider.class.getClassLoader(), new Class<?>[]{ChunkHolder.PlayerProvider.class},
                (proxy, method, args) -> {
                    List<ServerPlayer> watchers = (boolean) args[1] ? List.of(border) : List.of(border, interior);
                    return method.getReturnType() == java.util.stream.Stream.class ? watchers.stream() : watchers;
                });
        ChunkHolder holder = holder(level, engine, players);
        broadcast(holder, chunk);
        check(count(border) == 1 && count(interior) == 0, "untouched vanilla chunks retain border-only light broadcasting");
        looping.setBoolean(level, true); LoopingRegistry.bumpRevision();
        LoopingDimensionManager.setLooping(Level.OVERWORLD, true);
        broadcast(holder, chunk);
        check(count(border) == 2 && count(interior) == 1, "periodic light update must reach interior watchers");
        looping.setBoolean(level, false); LoopingRegistry.bumpRevision();
        LoopingDimensionManager.setLooping(Level.OVERWORLD, false);
        broadcast(holder, chunk);
        check(count(border) == 3 && count(interior) == 2, "restoring vanilla light must also reach interior watchers");
        broadcast(holder(level, engine, players), chunk);
        check(count(border) == 4 && count(interior) == 3, "restoration also covers newly observed chunk holders");
        dimension.set(level, Level.NETHER);
        broadcast(holder(level, engine, players), chunk);
        check(count(border) == 5 && count(interior) == 3, "unmodified dimensions retain border-only behavior");
        dimension.set(level, Level.END);
        LoopingDimensionManager.setLooping(Level.END, true);
        LoopingDimensionManager.setLooping(Level.END, false);
        broadcast(holder(level, engine, players), chunk);
        check(count(border) == 6 && count(interior) == 4, "enable/disable before the first delta still resynchronizes watchers");
        LoopingDimensionManager.onServerStopped();
        broadcast(holder(level, engine, players), chunk);
        check(count(border) == 7 && count(interior) == 4, "server shutdown clears retained lighting authority");
        System.out.println("PASS: native light packets reach interior watchers, mode restoration and new holders; ordinary dimensions and stopped servers retain vanilla behavior");
    }

    private static ChunkHolder holder(Level level, LevelLightEngine engine, ChunkHolder.PlayerProvider players) throws Exception {
        var constructor = ChunkHolder.class.getConstructors()[0];
        Class<?>[] types = constructor.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == ChunkPos.class) args[i] = new ChunkPos(0, 0);
            else if (type == int.class) args[i] = 33;
            else if (type.isInstance(level)) args[i] = level;
            else if (type.isInstance(engine)) args[i] = engine;
            else if (type.isInstance(players)) args[i] = players;
            else if (type.getSimpleName().equals("LevelChangeListener"))
                args[i] = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, values) -> null);
            else throw new AssertionError("Unexpected ChunkHolder constructor argument: " + type);
        }
        return (ChunkHolder) constructor.newInstance(args);
    }

    private static void broadcast(ChunkHolder holder, LevelChunk chunk) throws Exception {
        var field = ChunkHolder.class.getDeclaredField("skyChangedLightSectionFilter");
        field.setAccessible(true);
        if (field.getType() == int.class) field.setInt(holder, 2);
        else ((BitSet) field.get(holder)).set(1);
        holder.broadcastChanges(chunk);
    }

    private static ServerPlayer player() throws Exception {
        var player = (ServerPlayer) SeamFixture.allocate(ServerPlayer.class);
        if (captureConnection == null) captureConnection = captureConnectionClass();
        player.connection = (ServerGamePacketListenerImpl) SeamFixture.allocate(captureConnection);
        return player;
    }
    private static int count(ServerPlayer player) throws Exception { return captureConnection.getField("packets").getInt(player.connection); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static Class<?> testChunkClass() throws Exception {
        String name = "babel/biblioteca/validation/LightTestChunk";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V17, ACC_PUBLIC, name, null, Type.getInternalName(LevelChunk.class), null);
        writer.visitField(ACC_PUBLIC, "testLevel", Type.getDescriptor(Level.class), null, null).visitEnd();
        var method = writer.visitMethod(ACC_PUBLIC, "getLevel", "()" + Type.getDescriptor(Level.class), null, null);
        method.visitCode(); method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, name, "testLevel", Type.getDescriptor(Level.class));
        method.visitInsn(ARETURN); method.visitMaxs(0, 0); method.visitEnd();
        method = writer.visitMethod(ACC_PUBLIC, "getPos", "()" + Type.getDescriptor(ChunkPos.class), null, null);
        method.visitCode(); method.visitTypeInsn(NEW, Type.getInternalName(ChunkPos.class));
        method.visitInsn(DUP); method.visitInsn(ICONST_0); method.visitInsn(ICONST_0);
        method.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(ChunkPos.class), "<init>", "(II)V", false);
        method.visitInsn(ARETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        return MethodHandles.lookup().defineClass(writer.toByteArray());
    }

    // Only the transport is replaced: native ChunkHolder chooses recipients and builds the packet.
    private static Class<?> captureConnectionClass() throws Exception {
        String name = "babel/biblioteca/validation/LightCaptureConnection";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V17, ACC_PUBLIC, name, null, Type.getInternalName(ServerGamePacketListenerImpl.class), null);
        writer.visitField(ACC_PUBLIC, "packets", "I", null, null).visitEnd();
        var method = writer.visitMethod(ACC_PUBLIC, "send", "(" + Type.getDescriptor(Packet.class) + ")V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(LightSyncChecks.class), "checkPacket",
                "(" + Type.getDescriptor(Packet.class) + ")V", false);
        method.visitVarInsn(ALOAD, 0);
        method.visitInsn(DUP);
        method.visitFieldInsn(GETFIELD, name, "packets", "I");
        method.visitInsn(ICONST_1);
        method.visitInsn(IADD);
        method.visitFieldInsn(PUTFIELD, name, "packets", "I");
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        return MethodHandles.lookup().defineClass(writer.toByteArray());
    }

    public static void checkPacket(Packet<?> packet) {
        check(packet instanceof ClientboundLightUpdatePacket, "native light packet type");
    }
}
