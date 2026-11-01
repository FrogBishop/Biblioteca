package babel.biblioteca.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/** Native section discovery, range checks, delivery and queue ordering; storage alone is a fixture. */
public final class GameEventChecks implements Opcodes {
    private static final Map<Level, Data> DATA = new IdentityHashMap<>();
    private static Class<?> registryApi, registryType, listenerApi, worldType, sourceType, chunkType;
    private static Object emptyRegistry;
    private record Delivery(String listener, Vec3 origin) {}
    private static final class Data {
        final Map<Integer, Object> registries = new HashMap<>();
        final List<Delivery> deliveries = new ArrayList<>();
        Object chunk;
    }

    public static void run() throws Exception {
        Class<?> eventType;
        try { eventType = Class.forName("net.minecraft.world.level.gameevent.GameEvent"); }
        catch (ClassNotFoundException beforeGameEvents) { return; }
        listenerApi = Class.forName("net.minecraft.world.level.gameevent.GameEventListener");
        try {
            registryApi = Class.forName("net.minecraft.world.level.gameevent.GameEventListenerRegistry");
            registryType = Class.forName("net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry");
        } catch (ClassNotFoundException legacy) {
            registryApi = Class.forName("net.minecraft.world.level.gameevent.GameEventDispatcher");
            registryType = Class.forName("net.minecraft.world.level.gameevent.EuclideanGameEventDispatcher");
        }
        emptyRegistry = registryApi.getField("NOOP").get(null);
        createTypes();
        ServerLevel world = (ServerLevel) SeamFixture.allocate(worldType);
        DATA.put(world, new Data());
        Object source = SeamFixture.allocate(sourceType), chunk = SeamFixture.allocate(chunkType);
        put(source, "owner", world); put(chunk, "owner", world); put(world, "source", source);
        DATA.get(world).chunk = chunk;
        try { put(world, "gameEventMessages", new ArrayList<>()); } catch (NoSuchFieldException ignored) { }
        for (Method m : ServerLevel.class.getMethods()) if (m.getName().equals("debugSynchronizers") && m.getParameterCount() == 0) {
            Object debug = m.getReturnType().getConstructor(ServerLevel.class).newInstance(world);
            for (Field f : ServerLevel.class.getDeclaredFields()) if (f.getType() == m.getReturnType()) { f.setAccessible(true); f.set(world, debug); }
        }
        Object event = eventType.getField("STEP").get(null);
        for (int period : new int[]{16, 384}) for (boolean looping : new boolean[]{true, false}) {
            int min = -64, top = min + period;
            put(world, "minimum", min); put(world, "period", period); put(world, "looping", looping);
            Data data = DATA.get(world); data.registries.clear();
            addListener(world, "bottom", min, false);
            addListener(world, "top", top - 2, true);
            addListener(world, "middle", min + period / 2, false);
            for (int direction : new int[]{-1, 1}) for (int image : new int[]{0, 3}) {
                data.deliveries.clear();
                Vec3 position = new Vec3(0.5, (direction > 0 ? top - 0.5 : min + 0.5) + image * period, 0.5);
                post(world, event, position);
                Map<String, Integer> counts = new HashMap<>();
                for (Delivery d : data.deliveries) {
                    counts.merge(d.listener, 1, Integer::sum);
                    double listenerY = d.listener.equals("bottom") ? min + 0.5 : d.listener.equals("top") ? top - 1.5 : min + period / 2 + 0.5;
                    check(Math.abs(d.origin.y - listenerY) <= 2.01, "event delivery did not use listener's nearby source image");
                }
                if (looping) {
                    check(counts.getOrDefault("bottom", 0) == 1, "bottom listener missed/duplicated the seam event: " + counts);
                    check(counts.getOrDefault("top", 0) == 1, "top listener missed/duplicated the seam event: " + counts);
                    check(!counts.containsKey("middle"), "distant middle listener received the event");
                } else if (image == 0) {
                    check(counts.size() == 1 && counts.getOrDefault(direction > 0 ? "top" : "bottom", 0) == 1,
                            "ordinary event neighborhood changed: " + counts);
                } else check(counts.isEmpty(), "ordinary event outside stored height was wrapped");
            }
        }
        DATA.remove(world);
        System.out.println("PASS: native game-event discovery, periodic range/delivery, immediate/sorted listeners, one-section deduplication and ordinary-world controls");
    }

    private static void addListener(ServerLevel world, String label, int y, boolean sorted) throws Exception {
        Data data = DATA.get(world);
        int section = y >> 4;
        Object registry = data.registries.get(section);
        if (registry == null) {
            var ctor = registryType.getConstructors()[0];
            Object[] args = new Object[ctor.getParameterCount()];
            for (int i = 0; i < args.length; i++) {
                Class<?> type = ctor.getParameterTypes()[i];
                if (Level.class.isAssignableFrom(type)) args[i] = world;
                else if (type == int.class) args[i] = section;
                else args[i] = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, values) -> null);
            }
            registry = ctor.newInstance(args); data.registries.put(section, registry);
        }
        Object position = Class.forName("net.minecraft.world.level.gameevent.BlockPositionSource")
                .getConstructor(BlockPos.class).newInstance(new BlockPos(0, y, 0));
        Object listener = Proxy.newProxyInstance(listenerApi.getClassLoader(), new Class<?>[]{listenerApi}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getListenerSource" -> position;
                case "getListenerRadius" -> 2;
                case "handleEventsImmediately" -> !sorted;
                case "getDeliveryMode" -> Arrays.stream(method.getReturnType().getEnumConstants())
                        .filter(v -> v.toString().equals("BY_DISTANCE") == sorted).findFirst().orElseThrow();
                case "handleGameEvent" -> {
                    Object source = args[args.length - 1];
                    if (!(source instanceof Vec3) && !(source instanceof BlockPos)) source = source.getClass().getMethod("source").invoke(source);
                    data.deliveries.add(new Delivery(label, source instanceof Vec3 vec ? vec : Vec3.atCenterOf((BlockPos) source)));
                    yield true;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> label;
                default -> throw new AssertionError("Unexpected listener method: " + method);
            };
        });
        Field listeners = field(registryType, "listeners");
        ((List<Object>) listeners.get(registry)).add(listener);
    }

    private static void post(ServerLevel world, Object event, Vec3 position) throws Exception {
        Class<?> dispatcher = Class.forName("net.minecraft.world.level.gameevent.GameEventDispatcher");
        Object target = dispatcher.isInterface() ? world : dispatcher.getConstructor(ServerLevel.class).newInstance(world);
        Method post = Arrays.stream(target.getClass().getMethods()).filter(m -> m.getName().equals(dispatcher.isInterface() ? "gameEvent" : "post")
                && m.getParameterCount() == 3 && Arrays.stream(m.getParameterTypes()).anyMatch(p -> p.getName().contains("GameEvent") || p.getSimpleName().equals("Holder"))).findFirst().orElseThrow();
        Object[] args = new Object[3];
        for (int i = 0; i < args.length; i++) {
            Class<?> type = post.getParameterTypes()[i];
            if (type.isInstance(event)) args[i] = event;
            else if (type == Vec3.class) args[i] = position;
            else if (type == BlockPos.class) args[i] = new BlockPos(0, (int) Math.floor(position.y), 0);
            else if (type.getSimpleName().equals("Context")) {
                Method factory = Arrays.stream(type.getMethods()).filter(m -> m.getName().equals("of") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].getSimpleName().equals("Entity")).findFirst().orElseThrow();
                args[i] = factory.invoke(null, new Object[]{null});
            }
        }
        post.invoke(target, args);
        try { Method flush = ServerLevel.class.getDeclaredMethod("sendGameEvents"); flush.setAccessible(true); flush.invoke(world); }
        catch (NoSuchMethodException immediate) { }
    }

    public static Object chunk(Level level, int x, int z) { return x == 0 && z == 0 ? DATA.get(level).chunk : null; }
    public static Object registry(Level level, int section) { return DATA.get(level).registries.getOrDefault(section, emptyRegistry); }
    private static void createTypes() throws Exception {
        String world = "babel/biblioteca/validation/EventTestWorld", source = "babel/biblioteca/validation/EventTestSource", chunk = "babel/biblioteca/validation/EventTestChunk";
        ClassWriter w = writer(world, ServerLevel.class);
        for (String f : new String[]{"minimum", "period", "looping"}) w.visitField(ACC_PUBLIC, f, f.equals("looping") ? "Z" : "I", null, null).visitEnd();
        w.visitField(ACC_PUBLIC, "source", Type.getDescriptor(ServerChunkCache.class), null, null).visitEnd();
        getter(w, world, "getMinY", "minimum", "I"); getter(w, world, "getMinBuildHeight", "minimum", "I");
        getter(w, world, "getHeight", "period", "I"); getter(w, world, "biblioteca$isLooping", "looping", "Z");
        getter(w, world, "getChunkSource", "source", Type.getDescriptor(ServerChunkCache.class));
        MethodVisitor mv = w.visitMethod(ACC_PUBLIC, "players", "()Ljava/util/List;", null, null); mv.visitCode();
        mv.visitMethodInsn(INVOKESTATIC, "java/util/Collections", "emptyList", "()Ljava/util/List;", false); finish(mv, ARETURN);
        w.visitEnd(); worldType = MethodHandles.lookup().defineClass(w.toByteArray());
        w = writer(source, ServerChunkCache.class); w.visitField(ACC_PUBLIC, "owner", Type.getDescriptor(Level.class), null, null).visitEnd();
        mv = w.visitMethod(ACC_PUBLIC, "getChunkNow", "(II)" + Type.getDescriptor(LevelChunk.class), null, null); mv.visitCode();
        mv.visitVarInsn(ALOAD, 0); mv.visitFieldInsn(GETFIELD, source, "owner", Type.getDescriptor(Level.class)); mv.visitVarInsn(ILOAD, 1); mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(GameEventChecks.class), "chunk", "(" + Type.getDescriptor(Level.class) + "II)Ljava/lang/Object;", false);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(LevelChunk.class)); finish(mv, ARETURN); w.visitEnd(); sourceType = MethodHandles.lookup().defineClass(w.toByteArray());
        w = writer(chunk, LevelChunk.class); w.visitField(ACC_PUBLIC, "owner", Type.getDescriptor(Level.class), null, null).visitEnd();
        String name = registryApi.getSimpleName().equals("GameEventListenerRegistry") ? "getListenerRegistry" : "getEventDispatcher";
        mv = w.visitMethod(ACC_PUBLIC, name, "(I)" + Type.getDescriptor(registryApi), null, null); mv.visitCode();
        mv.visitVarInsn(ALOAD, 0); mv.visitFieldInsn(GETFIELD, chunk, "owner", Type.getDescriptor(Level.class)); mv.visitVarInsn(ILOAD, 1);
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(GameEventChecks.class), "registry", "(" + Type.getDescriptor(Level.class) + "I)Ljava/lang/Object;", false);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(registryApi)); finish(mv, ARETURN); w.visitEnd(); chunkType = MethodHandles.lookup().defineClass(w.toByteArray());
    }
    private static ClassWriter writer(String name, Class<?> parent) { ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); w.visit(V17, ACC_PUBLIC, name, null, Type.getInternalName(parent), null); return w; }
    private static void getter(ClassWriter w, String owner, String name, String f, String desc) { MethodVisitor m = w.visitMethod(ACC_PUBLIC, name, "()" + desc, null, null); m.visitCode(); m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, owner, f, desc); finish(m, desc.length() == 1 ? IRETURN : ARETURN); }
    private static void finish(MethodVisitor m, int instruction) { m.visitInsn(instruction); m.visitMaxs(0, 0); m.visitEnd(); }
    private static Field field(Class<?> type, String name) throws Exception { for (Class<?> c = type; c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) { } throw new NoSuchFieldException(name); }
    private static void put(Object value, String name, Object data) throws Exception { field(value.getClass(), name).set(value, data); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
