package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Interpolates remote entities across the seam instead of through the world.
 *
 * <p>A movement packet names an absolute row, and the server writes it in the stored period: an
 * entity that walks from y = 319.9 to y = 320.0 is reported at y = -64.0 after the fold. The
 * client, however, is still rendering that entity near y = 319.9. Every consumer of such a target
 * therefore has to pick the copy of the target nearest the entity's current row, or it compares,
 * interpolates or teleports across a whole world height:
 *
 * <ul>
 *   <li>{@code ClientPacketListener} treats the 384 block gap as "too big to interpolate" and
 *       snaps;</li>
 *   <li>the interpolation handler would still be handed the far copy and would walk the entity
 *       through the world during its interpolation steps.</li>
 * </ul>
 *
 * <p>Both are fixed here, at the two places every path funnels through: the interpolation target
 * itself and the previous-position fields the renderer interpolates from. Snapping the previous
 * position to the copy nearest the current one is what makes a teleport packet across the seam look
 * like the one block step it actually is.
 */
@Mixin(Entity.class)
public abstract class EntityInterpolationYLoopMixin {

    @Shadow public abstract Level level();

    @Shadow public abstract Vec3 position();

    /**
     * Chooses the copy of an interpolation target nearest the entity, so the interpolation runs
     * over one ring step rather than a whole world height.
     */
    @ModifyVariable(
            method = "moveOrInterpolateTo(Lnet/minecraft/world/entity/PositionPath;FFZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private PositionPath biblioteca$foldInterpolationPath(PositionPath path) {
        Level level = this.level();
        if (path == null || level == null || !LoopingRegistry.isLooping(level)) return path;

        int period = level.getHeight();
        if (period <= 0) return path;

        double currentY = this.position().y;
        long layers = SeamPositions.layer(path.endPosition().y, currentY, period);
        if (layers == 0L) return path;

        double shift = (double) layers * period;

        return switch (path) {
            case PositionPath.Linear linear ->
                    PositionPath.of(linear.endPosition().add(0.0, shift, 0.0));
            case PositionPath.Stepped stepped -> {
                List<PositionStep> steps = new ArrayList<>(stepped.steps().size());
                for (PositionStep step : stepped.steps()) {
                    steps.add(new PositionStep(step.position().add(0.0, shift, 0.0), step.tickOffset()));
                }

                yield new PositionPath.Stepped(
                        stepped.endPosition().add(0.0, shift, 0.0),
                        steps);
            }
        };
    }

    /**
     * The previous position a packet supplies is an absolute row as well. Moving it to the copy
     * nearest the committed position keeps {@code Mth.lerp(partialTick, yOld, getY())} continuous
     * when the packet crossed the seam; without this the renderer sweeps a whole world height in
     * one tick.
     */
    @ModifyVariable(
            method = "setOldPosAndRot(Lnet/minecraft/world/phys/Vec3;FF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3 biblioteca$foldOldPosition(Vec3 oldPosition) {
        Level level = this.level();
        if (oldPosition == null || level == null || !LoopingRegistry.isLooping(level)) return oldPosition;

        return SeamPositions.foldToward(level, oldPosition, this.position().y);
    }
}
