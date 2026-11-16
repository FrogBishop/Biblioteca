package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Concrete overrides of the old default method; reuse its native multishot rotation. */
@Mixin({Pillager.class, Piglin.class})
public abstract class NeoForge1201CrossbowMixin implements CrossbowAttackMob {
    @Override public void shootCrossbowProjectile(LivingEntity shooter, LivingEntity target, Projectile projectile, float angle, float speed) {
        double dx = target.getX() - shooter.getX(), dz = target.getZ() - shooter.getZ();
        double y = target.getY(1.0 / 3.0);
        if (LoopingRegistry.isLooping(Compat.level(projectile))) y = SeamPositions.foldForDistance(Compat.level(projectile), y, projectile.getY());
        Vec3 aim = new Vec3(dx, y - projectile.getY() + Math.sqrt(dx * dx + dz * dz) * (double) 0.2F, dz);
        var direction = getProjectileShotVector(shooter, aim, angle);
        projectile.shoot(direction.x(), direction.y(), direction.z(), speed, 14 - Compat.level(shooter).getDifficulty().getId() * 4);
        shooter.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F / (shooter.getRandom().nextFloat() * 0.4F + 0.8F));
    }
}
