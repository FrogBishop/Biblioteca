package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a hanging entity's position, bounding box and attachment in one coordinate frame.
 *
 * <p>A painting or item frame is built from the block it hangs on and then given a bounding box
 * around that block. A tall painting at the top row spans the seam, and its centre lands on the
 * first row past the world top. The chunk position that comes out of the entity's position is
 * folded into the stored period, but the bounding box is not: the entity then reports a position
 * near the bottom of the world and a box near the top of it.
 *
 * <p>That mismatch is what keeps such a decoration from being placed: the survival test, the
 * overlap test and the entity section a server stores it in are all derived from the box, while
 * the saved attachment comes from the position. Folding the box, the position and the attachment
 * by the same whole number of periods gives one consistent entity. Every ring cell it covers is
 * unchanged; only the absolute window it is expressed in moves.
 */
@Mixin(HangingEntity.class)
public abstract class HangingEntityYLoopMixin {

    @Shadow protected abstract AABB calculateBoundingBox(BlockPos pos, Direction direction);

    @Inject(method = "recalculateBoundingBox", at = @At("HEAD"), cancellable = true)
    private void biblioteca$foldWholeEntity(CallbackInfo ci) {
        HangingEntity self = (HangingEntity) (Object) this;
        Direction direction = self.getDirection();
        if (direction == null) {
            // Vanilla's body does nothing without a direction.
            ci.cancel();
            return;
        }

        Level level = self.level();
        if (!LoopingRegistry.isLooping(level)) return;

        int period = level.getHeight();
        if (period <= 0) return;

        BlockAttachedEntityAccessor accessor = (BlockAttachedEntityAccessor) this;
        BlockPos attachment = accessor.biblioteca$attachmentPos();
        if (attachment == null) return;

        AABB box = this.calculateBoundingBox(attachment, direction);
        Vec3 centre = box.getCenter();
        double foldedY = SeamPositions.foldY(level, centre.y);
        double shift = foldedY - centre.y;

        if (shift != 0.0) {
            accessor.biblioteca$setAttachmentPos(attachment.offset(0, (int) Math.round(shift), 0));
        }

        self.setPosRaw(centre.x, foldedY, centre.z);
        self.setBoundingBox(box.move(0.0, shift, 0.0));
        ci.cancel();
    }
}
