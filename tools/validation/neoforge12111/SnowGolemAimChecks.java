package babel.biblioteca.validation.neoforge12111;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.validation.SeamFixture;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.level.Level;
import java.util.Arrays;

/** Calls the native callback after the projectile's muzzle has crossed the world boundary. */
public final class SnowGolemAimChecks {
    public static void run() throws Exception {
        var callback = Arrays.stream(SnowGolem.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("lambda$performRangedAttack$")
                        && Arrays.equals(m.getParameterTypes(), new Class<?>[]{double.class, double.class, double.class, double.class, Snowball.class}))
                .findFirst().orElseThrow();
        callback.setAccessible(true);
        for (int height : new int[]{16, 384}) {
            Level level = SeamFixture.create(-64, height);
            CaptureSnowball ball = new CaptureSnowball(level);
            ball.setPos(0, -63.75, 0);
            callback.invoke(null, 2.0, -64 + height - 0.25, 0.75, 3.0, ball);
            expect(ball.x, 2.0); expect(ball.y, 0.25); expect(ball.z, 3.0);
            ball.setPos(0, -64 + height - 0.25, 0);
            callback.invoke(null, 2.0, -63.75, 0.75, 3.0, ball);
            expect(ball.y, 1.25);
            level.getClass().getField("looping").setBoolean(level, false);
            LoopingRegistry.bumpRevision();
            callback.invoke(null, 2.0, -63.75, 0.75, 3.0, ball);
            expect(ball.y, 1.25 - height);
        }
        System.out.println("PASS: NeoForge native snowball callback preserves nearby seam aim, ballistic lift and ordinary-world aim");
    }
    private static void expect(double actual, double expected) {
        if (Math.abs(actual - expected) > 1e-7) throw new AssertionError("Snowball aim: " + actual + " != " + expected);
    }
    private static final class CaptureSnowball extends Snowball {
        double x, y, z;
        CaptureSnowball(Level level) { super(EntityType.SNOWBALL, level); }
        @Override public void shoot(double x, double y, double z, float speed, float spread) { this.x = x; this.y = y; this.z = z; }
    }
}
