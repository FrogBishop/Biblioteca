package biblioteca.physicsprobe;

import babel.biblioteca.Compat;
import babel.biblioteca.world.LoopingDimensionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.*;
import java.util.*;

/** Test-only entry point; every decision runs through actual server entities and loaded chunks. */
public final class EntityPhysicsProbe implements ModInitializer {
    private static final List<Throwable> failures = new ArrayList<>();
    private int startupTicks;
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            server.overworld().setChunkForced(0, 0, true);
            server.overworld().getChunk(0, 0);
        });
        // Loading block data alone does not make entity sections visible on every version.
        // Allow the real server to process the forced chunk's tracking status before querying drops.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++startupTicks == 5) run(server);
        });
    }
    private static void run(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            level.getChunk(0, 0);
            int min = Compat.minY(level), height = Compat.height(level);
            for (int x = 5; x <= 11; x++) for (int z = 5; z <= 11; z++)
                for (int y = min; y < min + height; y++) level.setBlock(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState(), 2);
            LoopingDimensionManager.setLooping(level.dimension(), true);
            for (BlockState material : new BlockState[]{Blocks.SAND.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.ANVIL.defaultBlockState()}) verify("falling_lifetime_" + material, () -> {
                FallingBlockEntity e = falling(level, min + 3.5, material);
                e.setDeltaMovement(0, -0.1, 0);
                while (age(e) < 600) {
                    old(e); e.tick();
                    check(!removed(e), "sand discarded at age " + age(e) + ", y=" + e.getY() + ", velocity=" + e.getDeltaMovement().y);
                }
                check(age(e) == 600, "age must remain monotonic through crossings");
                old(e); e.tick();
                check(age(e) == 601 && removed(e), "looping dimensions retain the native age timeout");
                long drops = level.getEntitiesOfClass(ItemEntity.class, e.getBoundingBox().inflate(2.0)).stream()
                        .filter(item -> item.getItem().getItem() == material.getBlock().asItem()).count();
                check(drops == 1, "expiry must produce exactly one matching item, got " + drops);
            });
            for (double speed : new double[]{-1.96, 1.96, -9, 9, -height-3.25, height+3.25}) {
                verify("falling_movement_" + speed, () -> {
                    double start = speed < 0 ? min + 0.25 : min + height - 0.25;
                    FallingBlockEntity e = falling(level, start, Blocks.SAND.defaultBlockState());
                    e.setNoGravity(true); e.setDeltaMovement(0,speed,0); old(e); e.tick();
                    check(!removed(e), "unobstructed fast sand discarded");
                    near(e.getY(), fold(start + speed, min, height), "committed Y");
                    check(!ground(e), "air was mistaken for ground");
                    near(e.yo, e.getY() - speed, "previous position must preserve physical travel direction and length");
                    paths(e, Math.abs(speed));
                });
            }
            verify("falling_lands_across_seam", () -> {
                BlockPos floor = new BlockPos(8,min+height-3,8), landed = floor.above();
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
                FallingBlockEntity e = falling(level, min+0.25, Blocks.SAND.defaultBlockState());
                e.setNoGravity(true);e.setDeltaMovement(0,-5,0); old(e);e.tick();
                check(removed(e) && level.getBlockState(landed).getBlock() == Blocks.SAND, "sand must place on the real floor across seam");
                level.setBlock(landed, Blocks.AIR.defaultBlockState(),2);level.setBlock(floor, Blocks.AIR.defaultBlockState(),2);
            });
            for (double distance : new double[]{9.0, height + 3.25}) verify("concrete_water_" + distance, () -> {
                BlockPos water = new BlockPos(8,min+height-2,8);
                level.setBlock(water, Blocks.WATER.defaultBlockState(),2);
                FallingBlockEntity e = falling(level,min+0.25,colored("CONCRETE_POWDER").defaultBlockState());
                e.setNoGravity(true);e.setDeltaMovement(0,-distance,0);old(e);e.tick();
                check(removed(e) && level.getBlockState(water).getBlock()==colored("CONCRETE"),
                        "fast powder must hit the first water on its physical downward path");
                level.setBlock(water,Blocks.AIR.defaultBlockState(),2);
            });
            for (String type : new String[]{"ITEM", "TNT", "COW"}) verify("shared_entity_movement_" + type, () -> {
                Entity e = entity(type, level); e.setPos(8.5,min+0.25,8.5); old(e);
                Method compute = method(Entity.class,"computeSpeed",0);
                if (compute != null) compute.invoke(e);
                e.move(MoverType.SELF,new Vec3(0,-1.5,0));
                e.move(MoverType.SELF,new Vec3(0,-1.25,0));
                Method apply = method(Entity.class,"applyEffectsFromBlocks",0);
                if (apply != null) apply.invoke(e);
                near(e.getY(), min+height-2.5, "two moves in one tick");
                paths(e,2.75);
                if (compute != null) {
                    compute.invoke(e);
                    Field speed = Entity.class.getDeclaredField("lastKnownSpeed");speed.setAccessible(true);
                    near(((Vec3)speed.get(e)).y,-2.75,"computed speed must exclude coordinate wrapping");
                }
            });
            verify("ordinary_lifetime_control", () -> {
                LoopingDimensionManager.setLooping(level.dimension(), false);
                FallingBlockEntity e = falling(level, min+height/2.0, Blocks.SAND.defaultBlockState());
                e.setNoGravity(true);field(FallingBlockEntity.class,"time").setInt(e,600);old(e);e.tick();
                check(removed(e),"ordinary dimensions retain the native age timeout");
            });
            if (failures.isEmpty()) System.out.println("PHYSICS_OK height=" + height);
            else { System.out.println("PHYSICS_FAILED count=" + failures.size()); for (Throwable error:failures) error.printStackTrace(); }
        } catch (Throwable error) {System.out.println("PHYSICS_FAILED setup");error.printStackTrace();}
        server.halt(false);
    }
    private interface Check {void run() throws Exception;}
    private static void verify(String name,Check check) {
        try {check.run();System.out.println("PHYSICS_CASE_OK " + name);}
        catch (Throwable e) {failures.add(e);System.out.println("PHYSICS_CASE_FAILED " + name + ": " + e);}
    }
    private static FallingBlockEntity falling(ServerLevel level,double y,BlockState state) throws Exception {
        FallingBlockEntity e = (FallingBlockEntity)entity("FALLING_BLOCK",level);
        field(FallingBlockEntity.class,"blockState").set(e,state);field(FallingBlockEntity.class,"time").setInt(e,2);
        e.setPos(8.5,y,8.5);return e;
    }
    private static Entity entity(String id,ServerLevel level) throws Exception {
        Class<?> types;try {types=Class.forName("net.minecraft.world.entity.EntityTypes");}catch(ClassNotFoundException e){types=EntityType.class;}
        Object type=types.getField(id).get(null);
        String cls=switch(id) {case "ITEM" -> "item.ItemEntity";case "TNT" -> "item.PrimedTnt";case "COW" -> "animal.cow.Cow";default -> "item.FallingBlockEntity";};
        Class<?> entityClass;try {entityClass=Class.forName("net.minecraft.world.entity."+cls);}catch(ClassNotFoundException e){entityClass=Class.forName("net.minecraft.world.entity.animal.Cow");}
        return (Entity)entityClass.getConstructor(EntityType.class,net.minecraft.world.level.Level.class).newInstance(type,level);
    }
    private static net.minecraft.world.level.block.Block colored(String kind) throws Exception {
        try { return (net.minecraft.world.level.block.Block) Blocks.class.getField("WHITE_" + kind).get(null); }
        catch (NoSuchFieldException newer) {
            Object colors = Blocks.class.getField(kind).get(null);
            return (net.minecraft.world.level.block.Block) colors.getClass().getMethod("white").invoke(colors);
        }
    }
    private static void paths(Entity e,double distance) throws Exception {
        Field paths;try {paths=field(Entity.class,"finalMovementsThisTick");}catch(NoSuchFieldException absent){return;}
        Collection<?> list=(Collection<?>)paths.get(e);
        if (list.isEmpty()) return;
        double length=0;Vec3 previous=null;
        for(Object p:list){Method fm=method(p.getClass(),"from",0),tm=method(p.getClass(),"to",0);Vec3 from=(Vec3)fm.invoke(p),to=(Vec3)tm.invoke(p);
            if(previous!=null)near(from.y,previous.y,"movement queue continuity");length+=to.subtract(from).length();previous=to;}
        near(length,distance,"swept path must not include a synthetic whole-world movement");
        near(previous.y,e.getY(),"last recorded endpoint stays beside canonical entity");
    }
    private static void old(Entity e){e.xo=e.getX();e.yo=e.getY();e.zo=e.getZ();e.xOld=e.getX();e.yOld=e.getY();e.zOld=e.getZ();}
    private static int age(Entity e)throws Exception{return field(FallingBlockEntity.class,"time").getInt(e);}
    private static boolean removed(Entity e)throws Exception{Method m=method(Entity.class,"isRemoved",0);return m!=null?(boolean)m.invoke(e):field(Entity.class,"removed").getBoolean(e);}
    private static boolean ground(Entity e)throws Exception{Method m=method(Entity.class,"onGround",0);return m!=null?(boolean)m.invoke(e):field(Entity.class,"onGround").getBoolean(e);}
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static Method method(Class<?> c,String n,int count){for(Class<?> t=c;t!=null;t=t.getSuperclass())for(Method m:t.getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterCount()==count){m.setAccessible(true);return m;}return null;}
    private static double fold(double y,int min,int height){return y-Math.floor((y-min)/height)*height;}
    private static void near(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-6,message+": "+actual+" != "+expected);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
