package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import babel.biblioteca.SeamMovement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;
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


    /** Previous-tick position, which the camera and entity renderers interpolate from. */
    @Shadow public double yo;
    @Shadow public double yOld;

    @Unique private int biblioteca$moving;

    //? if >=1.21.2 {
    //? if >=1.21.6 {
    @Shadow @Final private ArrayDeque<?> movementThisTick;
    //?} else {
    /*@Shadow @Final private List<?> movementThisTick;
    *///?}
    //? if >=1.21.5 {
    @Shadow @Final private List<?> finalMovementsThisTick;
    //?}
    //?}
    //? if >=1.21.11 {
    @Shadow private Vec3 lastKnownPosition;
    //?}

    /** Physical travel keeps its full signed displacement, including multiple complete turns. */
    @WrapMethod(method = "move")
    private void biblioteca$physicalMovement(MoverType type, Vec3 delta, Operation<Void> original) {
        biblioteca$moving++;
        try { original.call(type, delta); }
        finally { biblioteca$moving--; }
    }

    @Unique
    private void biblioteca$shiftMovementFrame(double shift) {
        this.yo -= shift;
        this.yOld -= shift;
        //? if >=1.21.11 {
        if (lastKnownPosition != null) lastKnownPosition = lastKnownPosition.add(0.0, -shift, 0.0);
        //?}
        //? if >=1.21.2 {
        Set<Object> shifted = Collections.newSetFromMap(new IdentityHashMap<>());
        SeamMovement.shiftAll(movementThisTick, -shift, shifted);
        //? if >=1.21.5 {
        SeamMovement.shiftAll(finalMovementsThisTick, -shift, shifted);
        //?}
        //?}
    }

    @ModifyVariable(method = "setPosRaw(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double biblioteca$foldCommittedY(double y) {
        Level level = Compat.level((Entity) (Object) this);
        if (level == null) return y;
        if (!LoopingRegistry.isLooping(level)) return y;

        // Fast path: almost every position is already inside the world.
        double folded = SeamPositions.foldY(level, y);
        double shift = y - folded;

        if (biblioteca$moving > 0) {
            if (shift != 0.0) biblioteca$shiftMovementFrame(shift);
            return folded;
        }
        //? if >=1.21.2 {
        // Projectiles can record a swept path and commit directly instead of calling move.
        // Only a recorded endpoint matching this raw commit establishes physical travel.
        if (shift != 0.0 && SeamMovement.endsAt(movementThisTick, y)) {
            biblioteca$shiftMovementFrame(shift);
            return folded;
        }
        //?}

        if (shift == 0.0) {
            // Some paths commit a target that is already inside the world but a whole period away
            // from the row the entity is still shown at: a movement packet, a teleport that was
            // folded before it was sent, or an interpolated step that arrived after a dropped
            // packet. The renderer interpolates from the previous row, so without this the body
            // sweeps through the world and vanishes for a frame or two.
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
        // A buffered path can keep writing unwrapped rows after the entity has already crossed.
        // Pick each previous row's copy instead of subtracting the incoming period repeatedly.
        this.yo = SeamPositions.foldToward(this.yo, folded, Compat.height(level));
        this.yOld = SeamPositions.foldToward(this.yOld, folded, Compat.height(level));

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
        int period = Compat.height(level);
        if (period <= 0) return 0.0;

        double gap = previousY - y;
        double half = (double) period * 0.5;
        if (gap <= half && gap >= -half) return 0.0;

        double shift = Math.round(gap / (double) period) * (double) period;
        return Math.abs(gap - shift) <= 16.0 ? shift : 0.0;
    }
}
