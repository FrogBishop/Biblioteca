package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Aims a mob's head at the ring copy of whatever it is looking at.
 *
 * <p>Look targets are absolute positions: an entity across the seam, or the next node of a path
 * that crosses it. The head rotation is computed as {@code wantedY - mob.getEyeY()}, so a target on
 * the far side of the seam makes the mob stare straight up or down instead of across. Folding the
 * target row onto the copy nearest the mob's eyes is enough; the horizontal angle is unaffected.
 *
 * <p>Every caller funnels through the four-argument {@code setLookAt}, including the entity and
 * vector overloads, so one hook covers mob AI, goals and scripted look commands.
 */
@Mixin(LookControl.class)
public abstract class LookControlYLoopMixin {

    @Shadow @Final protected Mob mob;

    @ModifyVariable(
            method = "setLookAt(DDDFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double biblioteca$foldLookTargetY(double targetY) {
        Level level = Compat.level(this.mob);
        if (level == null || !LoopingRegistry.isLooping(level)) return targetY;

        int period = Compat.height(level);
        if (period <= 0) return targetY;

        return SeamPositions.foldToward(targetY, this.mob.getEyeY(), period);
    }
}
