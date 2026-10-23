package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.entity.EntityTypeTest;
//?}
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a hanging entity's position, bounding box and attachment in one coordinate frame, and
 * repeats the placement checks that read the box as an absolute area.
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
 *
 * <p>A seam-spanning box always has rows outside the world, whichever window it is expressed in,
 * because the rows it covers are the top row and one or more rows below the world. Vanilla's
 * placement checks read those rows through {@code LevelChunk} and through the section-based block
 * scan, both of which answer air for a row outside the build height instead of the row the ring
 * puts there. The support scan even evaluates the predicate on air once and reports every such row
 * as air. The collision and support checks are therefore repeated here through
 * {@code Level.getBlockState}, which folds, and the overlap check goes through
 * {@code Level.getEntities}, which already sees the ring. The entity's own body is still described
 * by the folded box, so every consumer that looks at the position rather than at block rows
 * behaves as in vanilla.
 */
@Mixin(HangingEntity.class)
public abstract class HangingEntityYLoopMixin {

    //? if >=1.21.10 {
    @Shadow protected abstract AABB getPopBox();
    @Shadow protected abstract AABB calculateSupportBox();
    //?}

    @Inject(method = "recalculateBoundingBox", at = @At("RETURN"))
    private void biblioteca$foldWholeEntity(CallbackInfo ci) {
        babel.biblioteca.SeamDecorations.fold((Entity) (Object) this);
    }

    //? if >=1.21.10 {
    @Inject(method = "survives", at = @At("HEAD"), cancellable = true)
    private void biblioteca$ringPlacementChecks(CallbackInfoReturnable<Boolean> cir) {
        HangingEntity self = (HangingEntity) (Object) this;
        Level level = Compat.level(self);
        if (!LoopingRegistry.isLooping(level)) return;

        AABB popBox = this.getPopBox();
        if (!biblioteca$noBlockCollision(level, self, popBox) || biblioteca$borderCollision(level, self, popBox)) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(biblioteca$isSupported(level) && biblioteca$canCoexist(level, self, popBox));
    }

    private static boolean biblioteca$borderCollision(Level level, Entity self, AABB box) {
        //? if >=1.21.11 {
        return !level.noBorderCollision(self, box);
        //?} else {
        /*return level.getWorldBorder().isInsideCloseToBorder(self, box)
                && Shapes.joinIsNotEmpty(level.getWorldBorder().getCollisionShape(), Shapes.create(box), BooleanOp.AND);
        *///?}
    }

    /** {@code CollisionGetter.noBlockCollision} with every row read through the ring. */
    private static boolean biblioteca$noBlockCollision(Level level, Entity entity, AABB box) {
        CollisionContext context = CollisionContext.of(entity);
        VoxelShape area = Shapes.create(box);
        for (BlockPos pos : babel.biblioteca.Compat.blocksIn(box)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            VoxelShape shape = state.getCollisionShape(level, pos, context);
            if (shape.isEmpty()) continue;

            VoxelShape moved = shape.move(pos.getX(), pos.getY(), pos.getZ());
            if (Shapes.joinIsNotEmpty(moved, area, BooleanOp.AND)) {
                return false;
            }
        }

        return true;
    }

    /** {@code HangingEntity.isSupportingBlock} over the ring rows behind the decoration. */
    private boolean biblioteca$isSupported(Level level) {
        for (BlockPos pos : babel.biblioteca.Compat.blocksIn(this.calculateSupportBox())) {
            BlockState state = level.getBlockState(pos);
            if (!Compat.isSolid(state) && !DiodeBlock.isDiode(state)) return false;
        }

        return true;
    }

    /** {@code HangingEntity.canCoexist(false)} through the ring-aware entity search. */
    private boolean biblioteca$canCoexist(Level level, HangingEntity self, AABB popBox) {
        return level.getEntities(
                EntityTypeTest.forClass(HangingEntity.class), popBox,
                other -> other != self
                        && (other.getType() == self.getType() || other.getDirection() == self.getDirection()))
                .isEmpty();
    }
    //?}
}
