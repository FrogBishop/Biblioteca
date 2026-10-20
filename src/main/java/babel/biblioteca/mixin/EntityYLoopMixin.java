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
        if (folded == y) return y;

        // A fold moves the position by a whole world height in a single step, and the renderer
        // interpolates from the previous tick's row. Moving that row by the same multiple of the
        // world height keeps the motion continuous: an entity that went from 319.9 to 320.0 now
        // reads as going from -64.1 to -64.0, the same movement at the same ring position.
        //
        // Collapsing the previous row to the new one instead - what a teleport does - would hold
        // the camera still for one tick and then jump, which is the twitch this replaced. Only
        // the interpolated position matters here; nothing else reads these two fields.
        double shift = y - folded;
        this.yo -= shift;
        this.yOld -= shift;

        return folded;
    }
}
