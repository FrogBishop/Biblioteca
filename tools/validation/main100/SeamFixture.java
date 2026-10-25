package babel.biblioteca.validation.main100;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.scores.Scoreboard;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Tiny physical chunk store. Native PathNavigationRegion, collision and entity code run normally. */
public final class SeamFixture implements Opcodes {
    private static final Map<Level, Data> DATA = new IdentityHashMap<>();
    private static Class<?> levelClass, serverLevelClass, chunkClass, sourceClass, soundLevelClass;
    private static MinecraftServer server;

    public static final class Data {
        public final int minimum, period;
        public boolean looping = true;
        public boolean client = true;
        public int reads;
        int nextId;
        public final Map<BlockPos, BlockState> blocks = new HashMap<>();
        public final List<Entity> spawned = new ArrayList<>();
        final EntityLookup<Entity> lookup = new EntityLookup<>();
        final EntitySectionStorage<Entity> sections = new EntitySectionStorage<>(Entity.class, key -> Visibility.TICKING);
        final LevelEntityGetter<Entity> entities = new LevelEntityGetterAdapter<>(lookup, sections);
        final Scoreboard scoreboard = new Scoreboard();
        final WorldBorder border = new WorldBorder();
        LevelChunk chunk;
        ChunkSource source;
        Data(int minimum, int period) { this.minimum = minimum; this.period = period; }
    }

    public static Data data(Level level) { return DATA.get(level); }
    public static Level create(int minimum, int period) throws Exception {
        return create(minimum, period, false);
    }
    public static ServerLevel createServer(int minimum, int period) throws Exception {
        return (ServerLevel) create(minimum, period, true);
    }
    private static Level create(int minimum, int period, boolean server) throws Exception {
        if (levelClass == null) defineWorldClasses();
        Level level = (Level) allocate(server ? serverLevelClass : levelClass);
        Data data = new Data(minimum, period);
        data.client = !server;
        DATA.put(level, data);
        data.chunk = (LevelChunk) allocate(chunkClass);
        chunkClass.getField("world").set(data.chunk, level);
        data.source = (ChunkSource) allocate(sourceClass);
        sourceClass.getField("world").set(data.source, level);
        return level;
    }

    public static int minimum(Level level) { return data(level).minimum; }
    public static int nextId(Level level) { return ++data(level).nextId; }
    public static int seaLevel(Level level) { return minimum(level) + period(level) / 2; }
    public static Difficulty difficulty(Level level) { return Difficulty.NORMAL; }
    public static MinecraftServer server(Level level) { return server; }
    public static RegistryAccess registries(Level level) { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    public static Scoreboard scoreboard(Level level) { return data(level).scoreboard; }
    public static LevelEntityGetter<Entity> entities(Level level) { return data(level).entities; }
    public static java.util.Collection<?> dragonParts(Level level) { return List.of(); }
    public static Entity entity(Level level, int id) { return data(level).lookup.getEntity(id); }
    public static boolean add(Level level, Entity entity) {
        Data data = data(level);
        data.spawned.add(entity);
        data.lookup.add(entity);
        data.sections.getOrCreateSection(SectionPos.asLong(entity.blockPosition())).add(entity);
        return true;
    }
    public static int period(Level level) { return data(level).period; }
    public static boolean client(Level level) { return data(level).client; }
    public static boolean looping(Level level) { return data(level).looping; }
    public static LevelChunk chunk(Level level) { return data(level).chunk; }
    public static ChunkSource source(Level level) { return data(level).source; }
    public static WorldBorder border(Level level) { return data(level).border; }
    public static BlockState physicalBlock(Level level, BlockPos pos) {
        Data data = data(level);
        if (++data.reads > 200_000) throw new AssertionError("Unbounded pathfinding column scan");
        return data.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }
    public static BlockState block(Level level, BlockPos pos) {
        Data data = data(level);
        return physicalBlock(level, data.looping ? canonical(level, pos) : pos);
    }
    public static FluidState fluid(Level level, BlockPos pos) { return block(level, pos).getFluidState(); }
    public static FluidState physicalFluid(Level level, BlockPos pos) { return physicalBlock(level, pos).getFluidState(); }
    public static BlockPos canonical(Level level, BlockPos pos) {
        Data data = data(level);
        return new BlockPos(pos.getX(), data.minimum + Math.floorMod(pos.getY() - data.minimum, data.period), pos.getZ());
    }
    public static void stone(Level level, int x, int y, int z) {
        data(level).blocks.put(canonical(level, new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState());
    }

    // tickLeash only needs this server to emit the break sound. Avoid starting a server or sending
    // packets; the branch decision, distance and spring physics remain native Leashable methods.
    public static ServerLevel soundLevel() throws Exception {
        if (soundLevelClass == null) {
            ClassWriter w = writer("SeamSoundLevel", ServerLevel.class);
            for (Method method : Level.class.getMethods()) {
                if (!method.getName().equals("playSound")) continue;
                MethodVisitor mv = method(w, method.getName(), Type.getMethodDescriptor(method));
                finish(mv, RETURN);
            }
            soundLevelClass = define(w);
        }
        return (ServerLevel) allocate(soundLevelClass);
    }

    private static void defineWorldClasses() throws Exception {
        server = (MinecraftServer) allocate(define(writer("SeamServer", MinecraftServer.class)));
        levelClass = defineLevel("SeamTestLevel", Level.class);
        serverLevelClass = defineLevel("SeamTestServerLevel", ServerLevel.class);

        ClassWriter w = writer("SeamTestChunk", LevelChunk.class);
        w.visitField(ACC_PUBLIC, "world", "Lnet/minecraft/world/level/Level;", null, null).visitEnd();
        blockDelegate(w, true, "getBlockState", "net/minecraft/world/level/block/state/BlockState", "physicalBlock");
        blockDelegate(w, true, "getFluidState", "net/minecraft/world/level/material/FluidState", "physicalFluid");
        constant(w, "isYSpaceEmpty", "(II)Z", ICONST_0, IRETURN);
        chunkClass = define(w);

        w = writer("SeamTestSource", ChunkSource.class);
        w.visitField(ACC_PUBLIC, "world", "Lnet/minecraft/world/level/Level;", null, null).visitEnd();
        for (String name : new String[]{"getChunkNow", "getChunk"}) {
            String descriptor = name.equals("getChunkNow") ? "(II)Lnet/minecraft/world/level/chunk/LevelChunk;"
                    : "(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;";
            MethodVisitor mv = method(w, name, descriptor);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, "babel/biblioteca/validation/main100/SeamTestSource", "world", "Lnet/minecraft/world/level/Level;");
            call(mv, "chunk", "(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/level/chunk/LevelChunk;");
            finish(mv, ARETURN);
        }
        sourceClass = define(w);
    }

    private static Class<?> defineLevel(String name, Class<?> parent) throws Exception {
        ClassWriter w = writer(name, parent);
        delegate(w, "getMinY", "()I", "minimum");
        delegate(w, "getHeight", "()I", "period");
        delegate(w, "biblioteca$isLooping", "()Z", "looping");
        delegate(w, "isClientSide", "()Z", "client");
        MethodVisitor dimension = method(w, "dimension", "()Lnet/minecraft/resources/ResourceKey;");
        dimension.visitFieldInsn(GETSTATIC, "net/minecraft/world/level/Level", "OVERWORLD", "Lnet/minecraft/resources/ResourceKey;");
        finish(dimension, ARETURN);
        constant(w, "getRelativeTickSpeed", "()F", FCONST_1, FRETURN);
        delegate(w, "getNextEntityId", "()I", "nextId");
        delegate(w, "getDifficulty", "()Lnet/minecraft/world/Difficulty;", "difficulty");
        delegate(w, "getSeaLevel", "()I", "seaLevel");
        delegate(w, "getServer", "()Lnet/minecraft/server/MinecraftServer;", "server");
        delegate(w, "registryAccess", "()Lnet/minecraft/core/RegistryAccess;", "registries");
        delegate(w, "getScoreboard", "()Lnet/minecraft/world/scores/Scoreboard;", "scoreboard");
        delegate(w, "getEntities", "()Lnet/minecraft/world/level/entity/LevelEntityGetter;", "entities");
        delegate(w, "dragonParts", "()Ljava/util/Collection;", "dragonParts");
        MethodVisitor find = method(w, "getEntity", "(I)Lnet/minecraft/world/entity/Entity;");
        find.visitVarInsn(ALOAD, 0); find.visitVarInsn(ILOAD, 1);
        call(find, "entity", "(Lnet/minecraft/world/level/Level;I)Lnet/minecraft/world/entity/Entity;"); finish(find, ARETURN);
        MethodVisitor add = method(w, "addFreshEntity", "(Lnet/minecraft/world/entity/Entity;)Z");
        add.visitVarInsn(ALOAD, 0); add.visitVarInsn(ALOAD, 1);
        call(add, "add", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;)Z"); finish(add, IRETURN);
        constant(w, "getGameTime", "()J", LCONST_0, LRETURN);
        constant(w, "noCollision", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", ICONST_1, IRETURN);
        delegate(w, "getWorldBorder", "()Lnet/minecraft/world/level/border/WorldBorder;", "border");
        delegate(w, "getChunkSource", "()Lnet/minecraft/world/level/chunk/ChunkSource;", "source");
        blockDelegate(w, false, "getBlockState", "net/minecraft/world/level/block/state/BlockState", "block");
        blockDelegate(w, false, "getFluidState", "net/minecraft/world/level/material/FluidState", "fluid");
        for (Method method : Level.class.getMethods()) {
            if (Modifier.isFinal(method.getModifiers()) || method.getReturnType() != void.class) continue;
            if (!List.of("playSound", "levelEvent", "gameEvent").contains(method.getName())) continue;
            finish(method(w, method.getName(), Type.getMethodDescriptor(method)), RETURN);
        }
        return define(w);
    }

    public static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type);
    }
    private static ClassWriter writer(String name, Class<?> parent) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(V17, ACC_PUBLIC, "babel/biblioteca/validation/main100/" + name, null, Type.getInternalName(parent), null);
        return w;
    }
    private static Class<?> define(ClassWriter w) throws Exception { w.visitEnd(); return MethodHandles.lookup().defineClass(w.toByteArray()); }
    private static MethodVisitor method(ClassWriter w, String name, String descriptor) {
        MethodVisitor mv = w.visitMethod(ACC_PUBLIC, name, descriptor, null, null); mv.visitCode(); return mv;
    }
    private static void finish(MethodVisitor mv, int result) { mv.visitInsn(result); mv.visitMaxs(0, 0); mv.visitEnd(); }
    private static void call(MethodVisitor mv, String name, String descriptor) {
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(SeamFixture.class), name, descriptor, false);
    }
    private static void delegate(ClassWriter w, String name, String descriptor, String helper) {
        MethodVisitor mv = method(w, name, descriptor); mv.visitVarInsn(ALOAD, 0);
        call(mv, helper, "(Lnet/minecraft/world/level/Level;" + descriptor.substring(1));
        finish(mv, Type.getReturnType(descriptor).getOpcode(IRETURN));
    }
    private static void blockDelegate(ClassWriter w, boolean chunk, String name, String result, String helper) {
        MethodVisitor mv = method(w, name, "(Lnet/minecraft/core/BlockPos;)L" + result + ";");
        mv.visitVarInsn(ALOAD, 0);
        if (chunk) mv.visitFieldInsn(GETFIELD, "babel/biblioteca/validation/main100/SeamTestChunk", "world", "Lnet/minecraft/world/level/Level;");
        mv.visitVarInsn(ALOAD, 1);
        call(mv, helper, "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)L" + result + ";"); finish(mv, ARETURN);
    }
    private static void constant(ClassWriter w, String name, String descriptor, int value, int result) {
        MethodVisitor mv = method(w, name, descriptor); mv.visitInsn(value); finish(mv, result);
    }
}
