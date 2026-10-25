package babel.biblioteca.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Physical storage only: the real Level mixins decide when a query crosses a world boundary. */
public final class SeamFixture implements Opcodes {
    private static final Map<Level, Data> DATA = new IdentityHashMap<>();
    private static final Set<Entity> STOP_AFTER_POSITION = Collections.newSetFromMap(new IdentityHashMap<>());
    private static Class<?> levelClass, chunkClass, sourceClass, livingClass;

    public static final class Data {
        public final List<Entity> entities = new ArrayList<>();
        public final Map<BlockPos, BlockState> blocks = new HashMap<>();
        Object getter, chunk, source;
    }

    public static Data data(Level level) { return DATA.get(level); }
    public static boolean hasSections() {
        try { Class.forName("net.minecraft.world.level.entity.EntityTypeTest"); return true; }
        catch (ClassNotFoundException ignored) { return false; }
    }
    public static int minimum(Level level) throws Exception { return levelClass.getField("minimum").getInt(level); }
    public static int period(Level level) throws Exception { return levelClass.getField("period").getInt(level); }

    public static Level create(int minimum, int period) throws Exception {
        if (levelClass == null) levelClass = createLevelClass();
        Level level = (Level) allocate(levelClass);
        levelClass.getField("minimum").setInt(level, minimum);
        levelClass.getField("period").setInt(level, period);
        levelClass.getField("looping").setBoolean(level, true);
        levelClass.getField("client").setBoolean(level, true);
        try { Field side = Level.class.getDeclaredField("isClientSide"); side.setAccessible(true); side.setBoolean(level, true); }
        catch (NoSuchFieldException ignored) { }
        Data data = new Data(); DATA.put(level, data);
        if (!hasSections()) {
            if (chunkClass == null) createChunkClasses();
            data.chunk = allocate(chunkClass); chunkClass.getField("world").set(data.chunk, level);
            data.source = allocate(sourceClass); sourceClass.getField("world").set(data.source, level);
        }
        return level;
    }

    public static BlockState blockAt(Level level, BlockPos pos) throws Exception {
        int min = minimum(level), height = period(level);
        BlockPos key = levelClass.getField("looping").getBoolean(level)
                ? new BlockPos(pos.getX(), min + Math.floorMod(pos.getY() - min, height), pos.getZ()) : pos;
        return DATA.get(level).blocks.getOrDefault(key, Blocks.AIR.defaultBlockState());
    }

    public static Object getter(Level level) throws Exception {
        Data data = DATA.get(level);
        if (data.getter != null) return data.getter;
        Class<?> api = Class.forName("net.minecraft.world.level.entity.LevelEntityGetter");
        Class<?> types = Class.forName("net.minecraft.world.level.entity.EntityTypeTest");
        Method cast = types.getMethod("tryCast", Object.class);
        data.getter = Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (proxy, method, args) -> {
            if (method.getName().equals("getAll")) return data.entities;
            AABB box = null; Object type = null, consumer = null;
            for (Object arg : args) {
                if (arg instanceof AABB area) box = area;
                else if (types.isInstance(arg)) type = arg;
                else consumer = arg;
            }
            if (box == null) return null;
            for (Entity entity : data.entities) {
                if (!box.intersects(entity.getBoundingBox()) || type != null && cast.invoke(type, entity) == null) continue;
                if (consumer instanceof Consumer callback) callback.accept(entity);
                else {
                    Method accept = consumer.getClass().getMethod("accept", Object.class); accept.setAccessible(true);
                    if (accept.invoke(consumer, entity).toString().equals("ABORT")) break;
                }
            }
            return null;
        });
        return data.getter;
    }

    public static Object source(Level level) { return DATA.get(level).source; }
    public static Object chunk(Level level, int x, int z) { return x == 0 && z == 0 ? DATA.get(level).chunk : null; }

    public static void collect(Level level, Object filter, AABB box, List<Entity> output, Predicate<Entity> selector) {
        for (Entity entity : DATA.get(level).entities) {
            if (filter == entity || filter instanceof Class<?> type && !type.isInstance(entity)) continue;
            if (filter instanceof net.minecraft.world.entity.EntityType<?> type && entity.getType() != type) continue;
            if (box.intersects(entity.getBoundingBox()) && (selector == null || selector.test(entity))) output.add(entity);
        }
    }

    private static Class<?> createLevelClass() throws Exception {
        String name = "babel/biblioteca/validation/SeamTestLevel";
        ClassWriter w = writer(name, "net/minecraft/world/level/Level");
        for (String f : new String[]{"minimum", "period", "looping", "client"}) w.visitField(ACC_PUBLIC, f, f.equals("minimum") || f.equals("period") ? "I" : "Z", null, null).visitEnd();
        String minName;
        try { Level.class.getMethod("getMinY"); minName = "getMinY"; }
        catch (NoSuchMethodException ignored) { minName = "getMinBuildHeight"; }
        fieldGetter(w, name, minName, "minimum", "I");
        fieldGetter(w, name, "getHeight", "period", "I");
        if (!hasSections()) fieldGetter(w, name, "getMaxBuildHeight", "period", "I");
        fieldGetter(w, name, "biblioteca$isLooping", "looping", "Z");
        fieldGetter(w, name, "isClientSide", "client", "Z");
        constant(w, "getRelativeTickSpeed", "()F", FCONST_1, FRETURN);
        constant(w, "getNextEntityId", "()I", ICONST_1, IRETURN);
        constant(w, "noCollision", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", ICONST_1, IRETURN);
        MethodVisitor mv = method(w, "getBlockState", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;");
        mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ALOAD, 1);
        call(mv, "blockAt", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"); finish(mv, ARETURN);
        if (hasSections()) delegate(w, "getEntities", "net/minecraft/world/level/entity/LevelEntityGetter", "getter");
        else delegate(w, "getChunkSource", "net/minecraft/world/level/chunk/ChunkSource", "source");
        mv = method(w, "dragonParts", "()Ljava/util/Collection;");
        mv.visitMethodInsn(INVOKESTATIC, "java/util/List", "of", "()Ljava/util/List;", true); finish(mv, ARETURN);
        mv = method(w, "getProfiler", "()Lnet/minecraft/util/profiling/ProfilerFiller;");
        mv.visitFieldInsn(GETSTATIC, "net/minecraft/util/profiling/InactiveProfiler", "INSTANCE", "Lnet/minecraft/util/profiling/InactiveProfiler;"); finish(mv, ARETURN);
        return define(w);
    }

    private static void createChunkClasses() throws Exception {
        String name = "babel/biblioteca/validation/SeamTestChunk";
        ClassWriter w = writer(name, "net/minecraft/world/level/chunk/LevelChunk");
        w.visitField(ACC_PUBLIC, "world", "Lnet/minecraft/world/level/Level;", null, null).visitEnd();
        for (String filter : new String[]{"net/minecraft/world/entity/Entity", "net/minecraft/world/entity/EntityType", "java/lang/Class"}) {
            MethodVisitor mv = method(w, filter.equals("java/lang/Class") ? "getEntitiesOfClass" : "getEntities",
                    "(L" + filter + ";Lnet/minecraft/world/phys/AABB;Ljava/util/List;Ljava/util/function/Predicate;)V");
            mv.visitVarInsn(ALOAD, 0); mv.visitFieldInsn(GETFIELD, name, "world", "Lnet/minecraft/world/level/Level;");
            for (int slot = 1; slot <= 4; slot++) mv.visitVarInsn(ALOAD, slot);
            call(mv, "collect", "(Lnet/minecraft/world/level/Level;Ljava/lang/Object;Lnet/minecraft/world/phys/AABB;Ljava/util/List;Ljava/util/function/Predicate;)V"); finish(mv, RETURN);
        }
        chunkClass = define(w);
        name = "babel/biblioteca/validation/SeamTestChunkSource";
        w = writer(name, "net/minecraft/world/level/chunk/ChunkSource");
        w.visitField(ACC_PUBLIC, "world", "Lnet/minecraft/world/level/Level;", null, null).visitEnd();
        for (String method : new String[]{"getChunk", "getChunkNow"}) {
            MethodVisitor mv = method(w, method, (method.equals("getChunk") ? "(IIZ)" : "(II)") + "Lnet/minecraft/world/level/chunk/LevelChunk;");
            mv.visitVarInsn(ALOAD, 0); mv.visitFieldInsn(GETFIELD, name, "world", "Lnet/minecraft/world/level/Level;");
            mv.visitVarInsn(ILOAD, 1); mv.visitVarInsn(ILOAD, 2);
            call(mv, "chunk", "(Lnet/minecraft/world/level/Level;II)Ljava/lang/Object;");
            mv.visitTypeInsn(CHECKCAST, "net/minecraft/world/level/chunk/LevelChunk"); finish(mv, ARETURN);
        }
        sourceClass = define(w);
    }

    /** Stop after the native aiStep has computed/committed its position, before unrelated world simulation. */
    public static final class PositionRecorded extends RuntimeException {
        PositionRecorded() { super(null, null, false, false); }
    }
    public static void afterPosition(Entity entity) { if (STOP_AFTER_POSITION.contains(entity)) throw new PositionRecorded(); }
    public static void stopAfterPosition(Entity entity, boolean stop) {
        if (stop) STOP_AFTER_POSITION.add(entity); else STOP_AFTER_POSITION.remove(entity);
    }
    public static Entity living(Level level) throws Exception {
        if (livingClass == null) {
            String name = "babel/biblioteca/validation/SeamTestLiving";
            String parent = "net/minecraft/world/entity/decoration/ArmorStand";
            ClassWriter w = writer(name, parent);
            MethodVisitor mv = method(w, "<init>", "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V");
            mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ALOAD, 1); mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", false); finish(mv, RETURN);
            mv = method(w, "setPos", "(DDD)V");
            mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(DLOAD, 1); mv.visitVarInsn(DLOAD, 3); mv.visitVarInsn(DLOAD, 5);
            mv.visitMethodInsn(INVOKESPECIAL, parent, "setPos", "(DDD)V", false);
            mv.visitVarInsn(ALOAD, 0); call(mv, "afterPosition", "(Lnet/minecraft/world/entity/Entity;)V"); finish(mv, RETURN);
            livingClass = define(w);
        }
        return (Entity) livingClass.getConstructor(net.minecraft.world.entity.EntityType.class, Level.class)
                .newInstance(net.minecraft.world.entity.EntityType.class.getField("ARMOR_STAND").get(null), level);
    }

    public static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type);
    }
    private static ClassWriter writer(String name, String parent) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); w.visit(V17, ACC_PUBLIC, name, null, parent, null); return w;
    }
    private static Class<?> define(ClassWriter w) throws Exception { w.visitEnd(); return MethodHandles.lookup().defineClass(w.toByteArray()); }
    private static void delegate(ClassWriter w, String name, String result, String helper) {
        MethodVisitor mv = method(w, name, "()L" + result + ";"); mv.visitVarInsn(ALOAD, 0);
        call(mv, helper, "(Lnet/minecraft/world/level/Level;)Ljava/lang/Object;"); mv.visitTypeInsn(CHECKCAST, result); finish(mv, ARETURN);
    }
    private static void call(MethodVisitor mv, String name, String descriptor) { mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(SeamFixture.class), name, descriptor, false); }
    private static MethodVisitor method(ClassWriter w, String name, String descriptor) {
        MethodVisitor mv = w.visitMethod(ACC_PUBLIC, name, descriptor, null, null); mv.visitCode(); return mv;
    }
    private static void finish(MethodVisitor mv, int result) { mv.visitInsn(result); mv.visitMaxs(0, 0); mv.visitEnd(); }
    private static void constant(ClassWriter w, String name, String descriptor, int value, int result) {
        MethodVisitor mv = method(w, name, descriptor); mv.visitInsn(value); finish(mv, result);
    }
    private static void fieldGetter(ClassWriter w, String owner, String name, String field, String type) {
        MethodVisitor mv = method(w, name, "()" + type); mv.visitVarInsn(ALOAD, 0); mv.visitFieldInsn(GETFIELD, owner, field, type); finish(mv, IRETURN);
    }
}
