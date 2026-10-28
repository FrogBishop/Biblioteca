package babel.biblioteca.validation.modern;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.world.LoopingDimensionManager;
import babel.biblioteca.world.LoopingWorldData;
import babel.biblioteca.validation.SeamFixture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.BitSet;
import java.util.List;

/** Executes native ChunkHolder broadcasting with border and interior chunk watchers. */
public final class LightSyncChecks {
    public static void run() throws Exception {
        var data = LoopingDimensionManager.class.getDeclaredField("serverData");
        data.setAccessible(true);
        check(data.get(null) == null, "run this isolated check without an active server world");
        data.set(null, LoopingWorldData.empty());
        try { verifyBroadcasts(); }
        finally { LoopingDimensionManager.onServerStopped(); }
    }

    private static void verifyBroadcasts() throws Exception {
        Level level = SeamFixture.create(-64, 384);
        var dimension = Level.class.getDeclaredField("dimension");
        dimension.setAccessible(true);
        dimension.set(level, Level.OVERWORLD);
        var looping = level.getClass().getField("looping");
        looping.setBoolean(level, false);
        level.getClass().getField("client").setBoolean(level, false);
        LoopingRegistry.bumpRevision();
        TestChunk chunk = (TestChunk) SeamFixture.allocate(TestChunk.class);
        chunk.testLevel = level;
        var engine = new LevelLightEngine(new LightChunkGetter() {
            public LightChunk getChunkForLighting(int x, int z) { return null; }
            public Level getLevel() { return level; }
        }, true, true);
        ServerPlayer border = player(), interior = player();
        ChunkHolder.PlayerProvider players = (pos, borderOnly) -> borderOnly ? List.of(border) : List.of(border, interior);
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

    private static ChunkHolder holder(Level level, LevelLightEngine engine, ChunkHolder.PlayerProvider players) {
        return new ChunkHolder(new ChunkPos(0, 0), 33, level, engine, (pos, oldLevel, newLevel, setter) -> {}, players);
    }

    private static void broadcast(ChunkHolder holder, LevelChunk chunk) throws Exception {
        var field = ChunkHolder.class.getDeclaredField("skyChangedLightSectionFilter");
        field.setAccessible(true);
        ((BitSet) field.get(holder)).set(1);
        holder.broadcastChanges(chunk);
    }

    private static ServerPlayer player() throws Exception {
        var player = (ServerPlayer) SeamFixture.allocate(ServerPlayer.class);
        player.connection = (CaptureConnection) SeamFixture.allocate(CaptureConnection.class);
        return player;
    }
    private static int count(ServerPlayer player) { return ((CaptureConnection) player.connection).packets; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class TestChunk extends LevelChunk {
        Level testLevel;
        private TestChunk() { super(null, new ChunkPos(0, 0)); }
        @Override public Level getLevel() { return testLevel; }
        @Override public ChunkPos getPos() { return new ChunkPos(0, 0); }
    }
    private static final class CaptureConnection extends ServerGamePacketListenerImpl {
        int packets;
        private CaptureConnection() { super(null, null, null, null); }
        @Override public void send(Packet<?> packet) {
            check(packet instanceof ClientboundLightUpdatePacket, "native light packet type");
            packets++;
        }
    }
}
