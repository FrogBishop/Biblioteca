package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps entity positions inside the world by folding Y at the moment a position is committed.
 *
 * setPosRaw is the single funnel every position write goes through: setPos delegates to it,
 * absMoveTo writes it directly, moveTo goes through setPos, and the position field itself is
 * private to Entity while setPosRaw is final. Folding there means the next coordinate is produced
 * in range rather than produced normally and then corrected afterwards.
 *
 * That difference is the whole point. A correction after the fact leaves the entity genuinely
 * outside the world for a tick: its movement, the packets and tracking updates built from that
 * position, and a player's client-side prediction all carry an out-of-range row, and the server
 * then has to send a teleport to drag the client back. With the fold at the commit point there is
 * no out-of-range position to describe, so none of that happens.
 */
@Mixin(Entity.class)
public abstract class EntityYLoopMixin {

    @Shadow public abstract Level level();

    /** Previous-tick position, which the camera and entity renderers interpolate from. */
    @Shadow public double yo;
    @Shadow public double yOld;

    @ModifyVariable(method = "setPosRaw(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double biblioteca$foldCommittedY(double y) {
        Level level = this.level();
        if (level == null) return y;
        if (!LoopingRegistry.isLooping(level)) return y;

        // Fast path: almost every position is already inside the world.
        double folded = SeamPositions.foldY(level, y);
        double shift = y - folded;

        if (shift == 0.0) {
            // Some paths commit a target that is already inside the world but a whole period away
            // from the row the entity is still shown at: a movement packet, a teleport that was
            // folded before it was sent, or an interpolated step that arrived after a dropped
            // packet. The renderer interpolates from the previous row, so without this the body
            // sweeps through the world and vanishes for a frame or two.
            // absSnapTo writes yo before setPosRaw, whereas yOld can still name the old period.
            // Shift each history field independently so the already-current one stays current.
            this.yo -= biblioteca$previousRowShift(level, this.yo, y);
            this.yOld -= biblioteca$previousRowShift(level, this.yOld, y);
            return y;
        }

        // A fold moves the position by a whole world height in a single step, and the renderer
        // interpolates from the previous tick's row. Moving that row by the same multiple of the
        // world height keeps the motion continuous: an entity that went from 319.9 to 320.0 now
        // reads as going from -64.1 to -64.0, the same movement at the same ring position.
        //
        // Collapsing the previous row to the new one instead - what a teleport does - would hold
        // the camera still for one tick and then jump. Shifting both previous-position fields
        // preserves the short interpolation interval across the boundary.
        // Buffered paths can write several unwrapped rows after the first crossing. The old
        // positions may already be folded, so subtracting the incoming shift again corrupts them.
        this.yo = SeamPositions.foldToward(this.yo, folded, level.getHeight());
        this.yOld = SeamPositions.foldToward(this.yOld, folded, level.getHeight());

        return folded;
    }

    /**
     * How far to move the previous row when the new row is already in range.
     *
     * <p>The shift is taken only when the previous row is a whole number of periods from the new
     * one, up to a small tolerance; that is the same ring row expressed in another copy. A
     * genuine jump of a few hundred blocks - a teleport within the world - leaves a large
     * remainder and is left alone, so it still snaps the way it always did.
     */
    private static double biblioteca$previousRowShift(Level level, double previousY, double y) {
        int period = level.getHeight();
        if (period <= 0) return 0.0;

        double gap = previousY - y;
        double half = (double) period * 0.5;
        if (gap <= half && gap >= -half) return 0.0;

        double shift = Math.round(gap / (double) period) * (double) period;
        return Math.abs(gap - shift) <= 16.0 ? shift : 0.0;
    }
}
