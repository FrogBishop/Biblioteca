package babel.biblioteca.mixin;

import babel.biblioteca.world.LoopingDimensionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After every entity tick, if the entity has moved outside the dimension's
 * Y bounds, wrap it to the other end using floor-modulo arithmetic.
 * Only runs on ServerLevel; the client receives the corrected position
 * via normal entity-tracking packets.
 *
 * For ServerPlayer specifically we also call connection.teleport() after
 * folding to send a ClientboundPlayerPositionPacket immediately.  Without
 * this the client keeps predicting the out-of-bounds position and continues
 * sending out-of-bounds movement packets every tick, since it never received
 * a server correction to snap it back.
 */
@Mixin(Entity.class)
public abstract class EntityYLoopMixin {

    @Shadow public abstract Level level();
    @Shadow public abstract double getX();
    @Shadow public abstract double getY();
    @Shadow public abstract double getZ();
    @Shadow public abstract float getYRot();
    @Shadow public abstract float getXRot();
    @Shadow public abstract void setPos(double x, double y, double z);
    @Shadow public abstract boolean isRemoved();

    @Inject(at = @At("TAIL"), method = "tick")
    private void biblioteca$wrapYOnTick(CallbackInfo ci) {
        if (isRemoved()) return;
        Level level = level();
        if (!(level instanceof ServerLevel)) return;
        if (!LoopingDimensionManager.isLooping(level.dimension())) return;

        double y      = getY();
        int    minY   = level.getMinY();
        int    height = level.getHeight();

        if (y >= minY && y < minY + height) return;

        double floorY        = Math.floor(y);
        double frac          = y - floorY;
        int    wrappedBlockY = Math.floorMod((int) floorY - minY, height) + minY;
        double newY          = wrappedBlockY + frac;

        setPos(getX(), newY, getZ());

        // For players: send an immediate position correction packet so the
        // client snaps to the folded Y without waiting for the next tracker
        // update.  Without this the client stays at the out-of-bounds Y and
        // keeps sending out-of-bounds movement packets every tick.
        if ((Object) this instanceof ServerPlayer player) {
            player.connection.teleport(getX(), newY, getZ(), getYRot(), getXRot());
        }
    }
}
