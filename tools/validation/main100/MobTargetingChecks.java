package babel.biblioteca.validation.main100;

import net.minecraft.core.Direction;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.breeze.Shoot;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static babel.biblioteca.validation.main100.SeamChecks.check;

/** Native target acquisition, sight rays, ranged attack/spawn methods and projectile hit tests. */
public final class MobTargetingChecks {
    private interface Factory { Mob create(Level level); }
    private interface Check { void run() throws Exception; }
    private static final Factory[] SHOOTERS = {Snow::new, Archer::new, Drowner::new, PotionThrower::new, Spitter::new, IllusionArcher::new,
            level -> new WitherBoss(EntityTypes.WITHER, level)};

    public static void run() throws Exception {
        // No world resource reload is started; bind the native common defaults for ordinary,
        // unenchanted test items. Targeting, projectile construction and all aim math stay native.
        net.minecraft.core.registries.BuiltInRegistries.ITEM.listElements().forEach(holder ->
                holder.bindComponents(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS));
        List<Throwable> failures = new ArrayList<>();
        for (int period : new int[]{16, 384}) {
            verify(failures, () -> sightAndTargets(period));
            verify(failures, () -> gaze(period));
            verify(failures, () -> hitsAndOwner(period));
            for (Factory factory : SHOOTERS) verify(failures, () -> shooting(period, factory));
            verify(failures, () -> crossbow(period));
            verify(failures, () -> fireballs(period));
            verify(failures, () -> breeze(period));
            verify(failures, () -> homing(period));
        }
        // In a short world, the native gravity compensation can exceed half a period even when
        // the target displacement does not. Only the displacement may be wrapped.
        verify(failures, () -> {
            for (boolean crossbow : new boolean[]{false, true}) {
                Vec3 ordinary = fire(16, Archer::new, -60, -53, false, crossbow, 12.5);
                Vec3 periodic = fire(16, Archer::new, -49, -58, true, crossbow, 12.5);
                vector(periodic, ordinary, "short worlds preserve the full native ballistic lift");
                check(periodic.y > 0, "ballistic compensation must not be wrapped back into a downward shot");
            }
            Vec3 ordinaryFar = fire(384, Archer::new, 319, -63, false, false);
            check(ordinaryFar.y < -1.5 && ordinaryFar.horizontalDistance() < .1, "ordinary worlds keep real long vertical shots");
        });
        if (!failures.isEmpty()) {
            AssertionError failure = new AssertionError("Mob targeting failures: " + failures.size());
            failures.forEach(failure::addSuppressed); throw failure;
        }
        System.out.println("PASS: native mob sight/acquisition, gaze, ranged attacks, projectile hits and owner exclusion in 16/384-row worlds");
    }

    private static void sightAndTargets(int period) throws Exception {
        for (Factory factory : new Factory[]{Snow::new, Archer::new}) {
            ServerLevel level = SeamFixture.createServer(-64, period);
            Mob observer = factory.create(level);
            Zombie target = new Zombie(EntityTypes.ZOMBIE, level);
            int top = -64 + period;
            observer.setPos(0.5, top - 1, 0.5); target.setPos(6.5, top - 1, 0.5);
            SeamFixture.add(level, observer); SeamFixture.add(level, target);
            check(observer.getEyeY() >= top, "fixture eyes extend past the stored top");
            check(observer.hasLineOfSight(target), "clear sight above the top is periodic");
            var goal = new NearestAttackableTargetGoal<>(observer, Zombie.class, 0, true, false, null);
            check(goal.canUse(), "native nearest target goal acquires a top-row target"); goal.start();
            check(observer.getTarget() == target, "native goal commits the acquired target");
            int eyeY = (int)Math.floor(observer.getEyeY());
            for (int y = eyeY - 1; y <= eyeY + 1; y++) SeamFixture.stone(level, 3, y, 0);
            observer.getSensing().tick();
            check(!observer.hasLineOfSight(target) && !goal.canUse(), "periodic wall still blocks sight and acquisition");
            SeamFixture.data(level).blocks.clear(); observer.getSensing().tick();
            target.setPos(6.5, -63, 0.5);
            check(observer.hasLineOfSight(target) && target.hasLineOfSight(observer), "sight works in both seam directions");
            SeamFixture.data(level).looping = false;
            if (period == 384) check(!observer.hasLineOfSight(target), "ordinary sight retains its 128-block distance guard");
        }
        System.out.println("PASS: mob sight " + period + " rows: native nearest-target acquisition, wrapped eyes, walls, both directions and ordinary dimensions");
    }

    private static void gaze(int period) throws Exception {
        Level level = SeamFixture.create(-64, period);
        Mob subject = new SeamChecks.TestMob(level), observer = new SeamChecks.TestMob(level);
        subject.setPos(0.5, -63.5, 0.5); observer.setPos(5.5, -64 + period - 1.5, 0.5);
        observer.setYRot(90); observer.yHeadRot = 90;
        observer.setXRot((float)-Math.toDegrees(Math.atan2(2, 5)));
        check(subject.isLookingAtMe(observer, .025, true, false, subject.getEyeY()), "Enderman/creaking gaze cone sees the nearby eye copy");
        observer.setYRot(-90); observer.yHeadRot = -90;
        check(!subject.isLookingAtMe(observer, .025, true, false, subject.getEyeY()), "looking away does not satisfy the gaze cone");
        observer.lookAt(Anchor.EYES, subject.getEyePosition());
        check(Math.abs(observer.getXRot() + Math.toDegrees(Math.atan2(2, 5))) < .01, "direct eye-anchored lookAt uses the same nearby copy");
        double[] heights = {subject.getY(), subject.getEyeY()};
        double[] original = heights.clone();
        check(subject.isLookingAtMe(observer, .025, true, false, heights), "multi-height gaze checks still find the visible eye sample");
        check(java.util.Arrays.equals(heights, original), "gaze checks do not mutate caller-owned height arrays");
    }

    private static void shooting(int period, Factory factory) throws Exception {
        for (double delta : new double[]{0, 2, -2}) {
            double source = delta < 0 ? -63 : -64 + period - 1;
            double target = delta == 0 ? source : delta > 0 ? -63 : -64 + period - 1;
            Vec3 ordinary = fire(period, factory, -60, -60 + delta, false, false);
            Vec3 looping = fire(period, factory, source, target, true, false);
            vector(looping, ordinary, factory.create(SeamFixture.create(-64, period)).getClass().getSimpleName() + " native shot has the same local trajectory across the seam");
        }
    }

    private static void crossbow(int period) throws Exception {
        for (double delta : new double[]{0, 2, -2}) {
            double source = delta < 0 ? -63 : -64 + period - 1;
            double target = delta == 0 ? source : delta > 0 ? -63 : -64 + period - 1;
            vector(fire(period, Archer::new, source, target, true, true), fire(period, Archer::new, -60, -60 + delta, false, true), "mob crossbow preserves aim and ballistic compensation");
        }
    }

    private static Vec3 fire(int period, Factory factory, double sourceY, double targetY, boolean looping, boolean crossbow) throws Exception {
        return fire(period, factory, sourceY, targetY, looping, crossbow, 6.5);
    }
    private static Vec3 fire(int period, Factory factory, double sourceY, double targetY, boolean looping, boolean crossbow, double targetX) throws Exception {
        ServerLevel level = SeamFixture.createServer(-64, period); SeamFixture.data(level).looping = looping;
        Mob shooter = factory.create(level), target = new SeamChecks.TestMob(level);
        shooter.setPos(0.5, sourceY, 0.5); target.setPos(targetX, targetY, 0.5);
        shooter.setYRot(0); shooter.yBodyRot = 0; shooter.setSilent(true);
        if (shooter instanceof Skeleton || shooter instanceof Illusioner) shooter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        SeamFixture.add(level, shooter); SeamFixture.add(level, target);
        Projectile shot;
        if (crossbow) {
            shot = ProjectileUtil.getMobArrow(shooter, new ItemStack(Items.ARROW), 1, new ItemStack(Items.CROSSBOW));
            Method method = CrossbowItem.class.getDeclaredMethod("shootProjectile", LivingEntity.class, Projectile.class, int.class, float.class, float.class, float.class, LivingEntity.class);
            method.setAccessible(true); method.invoke(Items.CROSSBOW, shooter, shot, 0, 1.6f, 0f, 0f, target);
        } else {
            ((RangedAttackMob)shooter).performRangedAttack(target, 1);
            shot = (Projectile)SeamFixture.data(level).spawned.getLast();
        }
        check(!looping || shot.getY() >= -64 && shot.getY() < -64 + period, "projectile keeps its canonical stored position");
        check(shot.getDeltaMovement().length() > .01, "native attack actually launches a moving projectile");
        return shot.getDeltaMovement();
    }

    private static void fireballs(int period) throws Exception {
        for (boolean ghast : new boolean[]{false, true}) for (double delta : new double[]{0, 2, -2}) {
            double source = delta < 0 ? -63 : -64 + period - 1;
            double target = delta == 0 ? source : delta > 0 ? -63 : -64 + period - 1;
            vector(fireball(period, ghast, source, target, true), fireball(period, ghast, -60, -60 + delta, false),
                    (ghast ? "Ghast" : "Blaze") + " native firing goal aims through the near seam");
        }
    }

    private static Vec3 fireball(int period, boolean ghast, double sourceY, double targetY, boolean looping) throws Exception {
        ServerLevel level = SeamFixture.createServer(-64, period); SeamFixture.data(level).looping = looping;
        Mob shooter = ghast ? new Ghast(EntityTypes.GHAST, level) : new Blaze(EntityTypes.BLAZE, level);
        Mob target = new SeamChecks.TestMob(level);
        shooter.setPos(.5, sourceY, .5); target.setPos(6.5, targetY, .5);
        shooter.setYRot(0); shooter.setXRot(0); shooter.yHeadRot = 0; shooter.setSilent(true);
        SeamFixture.add(level, shooter); SeamFixture.add(level, target); shooter.setTarget(target);
        Class<?> type = Class.forName(ghast ? "net.minecraft.world.entity.monster.Ghast$GhastShootFireballGoal" : "net.minecraft.world.entity.monster.Blaze$BlazeAttackGoal");
        var ctor = type.getDeclaredConstructor(ghast ? Ghast.class : Blaze.class); ctor.setAccessible(true);
        Goal goal = (Goal)ctor.newInstance(shooter);
        Field counter = type.getDeclaredField(ghast ? "chargeTime" : "attackStep"); counter.setAccessible(true); counter.setInt(goal, ghast ? 19 : 1);
        shooter.getRandom().setSeed(42); goal.tick();
        Projectile shot = (Projectile)SeamFixture.data(level).spawned.getLast();
        check(shot.getDeltaMovement().length() > .01, "native fireball has a firing direction");
        return shot.getDeltaMovement();
    }

    private static void breeze(int period) throws Exception {
        ServerLevel level = SeamFixture.createServer(-64, period);
        Breeze breeze = new Breeze(EntityTypes.BREEZE, level);
        Mob target = new SeamChecks.TestMob(level);
        breeze.setPos(.5, -64 + period - 1, .5); target.setPos(6.5, -63, .5);
        Method range = Shoot.class.getDeclaredMethod("isTargetWithinRange", Breeze.class, LivingEntity.class); range.setAccessible(true);
        check((boolean)range.invoke(null, breeze, target), "breeze recognizes a target across the seam within its attack radius");
        SeamFixture.data(level).looping = false;
        if (period == 384) check(!(boolean)range.invoke(null, breeze, target), "ordinary breeze range stays Euclidean");
        SeamFixture.data(level).looping = true;
        breeze.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        Method tick = Shoot.class.getDeclaredMethod("tick", ServerLevel.class, Breeze.class, long.class); tick.setAccessible(true);
        tick.invoke(new Shoot(), level, breeze, 0L);
        Projectile charge = (Projectile)SeamFixture.data(level).spawned.getLast();
        check(charge.getDeltaMovement().horizontalDistance() > .5 && Math.abs(charge.getDeltaMovement().y) < .4,
                "breeze wind charge travels toward the nearby target with native uncertainty");
    }

    private static void homing(int period) throws Exception {
        Level level = SeamFixture.create(-64, period);
        Mob target = new SeamChecks.TestMob(level);
        ShulkerBullet bullet = new ShulkerBullet(EntityTypes.SHULKER_BULLET, level);
        Method choose = ShulkerBullet.class.getDeclaredMethod("selectNextMoveDirection", Direction.Axis.class, Entity.class); choose.setAccessible(true);
        Field heading = ShulkerBullet.class.getDeclaredField("currentMoveDirection"); heading.setAccessible(true);
        bullet.setPos(.5, -64 + period - 1, .5); target.setPos(.5, -61, .5);
        choose.invoke(bullet, Direction.Axis.X, target);
        check(heading.get(bullet) == Direction.UP, "shulker homing chooses the nearby upward seam route");
        bullet.setPos(.5, -63.5, .5); target.setPos(.5, -64 + period - 4, .5);
        choose.invoke(bullet, Direction.Axis.X, target);
        check(heading.get(bullet) == Direction.DOWN, "shulker homing chooses the nearby downward seam route");
        SeamFixture.data(level).looping = false;
        choose.invoke(bullet, Direction.Axis.X, target);
        check(heading.get(bullet) == Direction.UP, "ordinary shulker homing keeps its stored target direction");
    }

    private static void hitsAndOwner(int period) throws Exception {
        ServerLevel level = SeamFixture.createServer(-64, period);
        Snow owner = new Snow(level); owner.setPos(.5, -64 + period - 1, .5);
        Zombie target = new Zombie(EntityTypes.ZOMBIE, level); target.setPos(2.5, owner.getY(), .5);
        SeamFixture.add(level, owner); SeamFixture.add(level, target);
        Snowball shot = new Snowball(level, owner, new ItemStack(Items.SNOWBALL));
        Method outside = Projectile.class.getDeclaredMethod("isOutsideOwnerCollisionRange"); outside.setAccessible(true);
        check(!(boolean)outside.invoke(shot), "wrapping the muzzle does not immediately end owner protection");
        shot.setDeltaMovement(new Vec3(3, 0, 0));
        var hit = ProjectileUtil.getHitResultOnMoveVector(shot, entity -> entity == target);
        check(hit instanceof EntityHitResult entityHit && entityHit.getEntity() == target, "server projectile intersects the visible periodic target box");
        var many = ProjectileUtil.getManyEntityHitResult(level, shot, shot.position(), shot.position().add(shot.getDeltaMovement()),
                shot.getBoundingBox().expandTowards(shot.getDeltaMovement()).inflate(1), entity -> entity == target, false, true);
        check(many.size() == 1 && many.iterator().next().getEntity() == target, "multi-hit rays use the same periodic target box");
        shot.setPos(8, shot.getY(), .5); shot.setDeltaMovement(Vec3.ZERO);
        check((boolean)outside.invoke(shot), "owner protection ends after the projectile really leaves the shooter");
    }

    private static void vector(Vec3 actual, Vec3 expected, String message) {
        check(actual.distanceTo(expected) < 1e-5, message + ": " + actual + " != " + expected);
    }
    private static void verify(List<Throwable> failures, Check check) {
        try { check.run(); } catch (Throwable failure) { failure.printStackTrace(); failures.add(failure); }
    }
    private static final class Snow extends SnowGolem {
        Snow(Level level) { super(EntityTypes.SNOW_GOLEM, level); }
        @Override public float rangedAttackUncertainty(Level level) { return 0; }
    }
    private static final class Archer extends Skeleton {
        Archer(Level level) { super(EntityTypes.SKELETON, level); }
        @Override public float rangedAttackUncertainty(Level level) { return 0; }
    }
    private static final class Drowner extends Drowned {
        Drowner(Level level) { super(EntityTypes.DROWNED, level); }
        @Override public float rangedAttackUncertainty(Level level) { return 0; }
    }
    private static final class PotionThrower extends Witch {
        PotionThrower(Level level) { super(EntityTypes.WITCH, level); }
        @Override public float rangedAttackUncertainty(Level level) { return 0; }
    }
    private static final class Spitter extends Llama {
        Spitter(Level level) { super(EntityTypes.LLAMA, level); }
        @Override public float rangedAttackUncertainty(Level level) { return 0; }
    }
    private static final class IllusionArcher extends Illusioner {
        IllusionArcher(Level level) { super(EntityTypes.ILLUSIONER, level); }
        @Override public float rangedAttackUncertainty(Level level) { return 0; }
    }
}
