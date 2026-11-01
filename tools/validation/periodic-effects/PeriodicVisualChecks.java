package biblioteca.periodicprobe;

import babel.biblioteca.Compat;
import babel.biblioteca.client.CloudLayers;
import babel.biblioteca.client.LoopingClientSettings;
import babel.biblioteca.world.LoopingDimensionManager;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real rendering, repeated crossings and option controls in a height-16 void dimension. */
public final class PeriodicVisualChecks {
    private static final ResourceKey<Level> WORLD = ResourceKey.create(Registries.DIMENSION, Compat.id("biblioteca", "visuals"));
    private static final Map<EntityRenderState, Entity> owners = new IdentityHashMap<>();
    private static final Set<Object> cloudBuffers = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Float> blockHeights = new HashSet<>();
    private static volatile boolean setup;
    private static volatile Throwable failure;
    private static int ticks, phase, frame, sampledFrame, cloudDraws, entityCopies, selfCopies, totalCloudPasses, initialBuffers, guardianBeams, crystalCopies, shadowPieces;
    private static boolean finished;
    private static float pitch;

    public static void serverTick(MinecraftServer server) {
        if (setup || failure != null || server.getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(server.getLevel(WORLD) != null, "height-16 test dimension did not load");
            command(server, "biblioteca looping set biblioteca:visuals true");
            command(server, "execute in biblioteca:visuals run biblioteca no_void_fog true");
            command(server, "execute in biblioteca:visuals run biblioteca sky_light hybrid");
            command(server, "gamemode creative @a");
            command(server, "time set noon");
            command(server, "weather clear");
            var visual = server.getLevel(WORLD);
            visual.getChunk(-1, 1); visual.getChunk(0, 1);
            for (int x = -3; x <= 3; x++) for (int z = 20; z <= 26; z++)
                visual.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            visual.setBlockAndUpdate(new net.minecraft.core.BlockPos(2, 1, 23), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            check(server.getLevel(WORLD).getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 23)) != null, "chest fixture missing");
            var cow = net.minecraft.world.entity.EntityTypes.COW.create(visual, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            check(cow != null, "native cow creation"); cow.setPos(0.5, 1, 23.5); cow.setNoAi(true); cow.setSilent(true); cow.setPersistenceRequired(); visual.addFreshEntity(cow);
            var crystal = new net.minecraft.world.entity.boss.enderdragon.EndCrystal(visual, 4.5, 15, 23.5);
            crystal.setBeamTarget(new net.minecraft.core.BlockPos(4, 0, 23)); crystal.setShowBottom(false); visual.addFreshEntity(crystal);
            var guardian = new net.minecraft.world.entity.monster.Guardian(net.minecraft.world.entity.EntityTypes.GUARDIAN, visual);
            guardian.setPos(-4.5, 15, 23.5); guardian.setNoAi(true); guardian.setNoGravity(true); guardian.setTarget(cow); visual.addFreshEntity(guardian);
            var attack = guardian.getClass().getDeclaredMethod("setActiveAttackTarget", int.class); attack.setAccessible(true); attack.invoke(guardian, cow.getId());
            command(server, "execute in biblioteca:visuals run tp @a 0.5 6 0.5 0 0");
            setup = true;
        } catch (Throwable error) { failure = error; }
    }

    public static void begin() { owners.clear(); blockHeights.clear(); guardianBeams = 0; crystalCopies = 0; shadowPieces = 0; }
    public static void guardianBeam(net.minecraft.world.phys.Vec3 vector) {
        guardianBeams++;
        if (babel.biblioteca.LoopingRegistry.isLooping(Minecraft.getInstance().level) && Math.abs(vector.y) > 8)
            failure = new AssertionError("guardian beam crossed the full period: " + vector);
    }
    public static void blockSubmitted(net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state, float y) {
        if (state.blockPos.equals(new net.minecraft.core.BlockPos(2, 1, 23))) blockHeights.add(y);
    }
    public static void extracted(Entity entity, EntityRenderState state) { owners.put(state, entity); }
    public static void entities(Camera camera, List<EntityRenderState> states) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(WORLD)) return;
        try {
            Set<EntityRenderState> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<Double> heights = new HashSet<>(); entityCopies = 0; selfCopies = 0;
            for (EntityRenderState state : states) {
                check(distinct.add(state), "two queued images share a mutable render state");
                Entity entity = owners.get(state);
                if (entity == null) continue;
                if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal
                        && state instanceof net.minecraft.client.renderer.entity.state.EndCrystalRenderState visual) {
                    crystalCopies++;
                    check(crystal.getBeamTarget().getY() == 0, "canonical crystal metadata was mutated");
                    if (babel.biblioteca.LoopingRegistry.isLooping(mc.level)) check(Math.abs(visual.beamOffset.y - 1.5) < 0.01, "crystal beam takes the long path");
                }
                if (entity == mc.player) {
                    selfCopies++;
                    check(Math.abs(state.y - camera.position().y) > 8, "first-person nearest body was submitted");
                } else if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals("cow")) {
                    entityCopies++; check(heights.add(state.y), "duplicate entity image height");
                    double actual = camera.position().distanceToSqr(state.x, state.y, state.z);
                    check(Math.abs(actual - state.distanceToCameraSq) < 1, "shadow/name distance uses a different image: " + state.distanceToCameraSq + " vs " + actual);
                    shadowPieces += state.shadowPieces.size();
                    check(state.distanceToCameraSq < 256 || state.shadowPieces.isEmpty(), "distant image retained a nearby shadow");
                }
            }
            frame++;
        } catch (Throwable error) { failure = error; }
    }

    public static void clouds(Object renderer, int draws, int distinctTransforms) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(WORLD)) return;
        try {
            CloudLayers layers = (CloudLayers) field(renderer, "biblioteca$layers");
            check(draws == layers.count() && draws == distinctTransforms, "cloud draw/transform count: " + draws + "/" + layers.count() + "/" + distinctTransforms);
            cloudDraws = draws; totalCloudPasses++;
            for (String name : new String[]{"ubo", "utb"}) {
                Object ring = field(renderer, name);
                cloudBuffers.add(ring.getClass().getMethod("currentBuffer").invoke(ring));
            }
        } catch (Throwable error) { failure = error; }
    }

    public static void clientTick(Minecraft mc) {
        if (finished || !setup || mc.level == null || !mc.level.dimension().equals(WORLD)) return;
        mc.mouseHandler.releaseMouse(); KeyMapping.releaseAll();
        mc.player.getAbilities().flying = true; mc.player.setDeltaMovement(0, 0, 0);
        mc.player.setYRot(0); mc.player.yRotO = 0; mc.player.setXRot(pitch); mc.player.xRotO = pitch;
        try {
            if (failure != null) throw new AssertionError("render observation", failure);
            if (++ticks < 60 || frame <= sampledFrame || totalCloudPasses == 0) return;
            switch (phase) {
                case 0 -> {
                    mc.options.fov().set(110); mc.options.cloudRange().set(8); mc.options.cloudStatus().set(CloudStatus.FANCY);
                    mc.options.improvedTransparency().set(false); next();
                }
                case 1, 2, 3 -> {
                    check(cloudDraws > 3, "expected more than three cloud layers: " + cloudDraws);
                    check(entityCopies >= 3, "expected several native cow states: " + entityCopies);
                    check(blockHeights.size() >= 3, "expected several native chest poses: " + blockHeights.size());
                    check(guardianBeams > 0 && crystalCopies >= 3, "missing native beam submissions");
                    capture(mc, "visuals-crossing-" + phase);
                    command(mc.getSingleplayerServer(), "execute in biblioteca:visuals run tp @a 0.5 " + (phase == 1 ? "16.5" : "-0.5") + " 0.5 0 0");
                    if (phase == 3) { pitch = -90; initialBuffers = cloudBuffers.size(); }
                    next();
                }
                case 4 -> {
                    check(selfCopies >= 2, "distant first-person self images missing: " + selfCopies);
                    capture(mc, "visuals-self"); pitch = 0;
                    LoopingClientSettings.setEntityMirrors(false); LoopingClientSettings.setMultipleCloudLayers(false); next();
                }
                case 5 -> {
                    check(entityCopies == 1 && selfCopies == 0 && cloudDraws == 1 && blockHeights.size() == 1, "disabled controls: entities=" + entityCopies + " self=" + selfCopies + " clouds=" + cloudDraws + " chest=" + blockHeights.size());
                    capture(mc, "visuals-disabled");
                    LoopingClientSettings.setEntityMirrors(true); LoopingClientSettings.setMultipleCloudLayers(true);
                    mc.options.cloudStatus().set(CloudStatus.FAST); next();
                }
                case 6 -> {
                    check(cloudDraws > 3 && entityCopies >= 3, "flat cloud copies/control restoration");
                    capture(mc, "visuals-flat"); mc.options.cloudStatus().set(CloudStatus.FANCY);
                    mc.options.improvedTransparency().set(true); next();
                }
                case 7 -> {
                    check(cloudDraws > 3, "OIT cloud copies");
                    capture(mc, "visuals-oit");
                    System.out.println("VISUAL_OIT requested=true active=" + mc.gameRenderer.useImprovedTransparency());
                    pitch = 20; command(mc.getSingleplayerServer(), "execute in biblioteca:visuals run tp @a 0.5 2 18.5 0 20"); next();
                }
                case 8 -> {
                    check(shadowPieces > 0, "native nearby cow shadow missing"); capture(mc, "visuals-shadows");
                    pitch = 0; command(mc.getSingleplayerServer(), "biblioteca looping set biblioteca:visuals false"); next();
                }
                case 9 -> {
                    check(cloudDraws == 1 && entityCopies == 1 && selfCopies == 0 && blockHeights.size() == 1, "ordinary world controls");
                    capture(mc, "visuals-ordinary");
                    check(cloudBuffers.size() <= initialBuffers + 6, "native cloud buffer ring grew with layer count: " + cloudBuffers.size());
                    System.out.println("VISUAL_RESOURCES passes=" + totalCloudPasses + " nativeCloudBuffers=" + cloudBuffers.size()); next();
                }
                case 10 -> { finished = true; System.out.println("PERIODIC_DONE failed=false"); mc.stop(); }
                default -> throw new AssertionError("unknown phase");
            }
        } catch (Throwable error) { finished = true; System.out.println("PERIODIC_FAILURE " + error); error.printStackTrace(); mc.stop(); }
    }
    private static void next() { phase++; ticks = 0; sampledFrame = frame; }
    private static void capture(Minecraft mc, String name) {
        System.out.println("VISUAL_SAMPLE name=" + name + " frame=" + frame + " entityCopies=" + entityCopies + " selfCopies=" + selfCopies + " cloudDraws=" + cloudDraws + " chestCopies=" + blockHeights.size() + " guardianBeams=" + guardianBeams + " crystalCopies=" + crystalCopies + " shadowPieces=" + shadowPieces);
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> System.out.println("PERIODIC_FRAME " + name));
    }
    private static Object field(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
    private static void command(MinecraftServer server, String command) {
        if (!server.isSameThread()) { server.execute(() -> command(server, command)); return; }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
