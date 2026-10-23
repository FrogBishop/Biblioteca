package babel.biblioteca;

import babel.biblioteca.mixin.BlockAttachedEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Keeps vanilla's decoration geometry and saved anchor in the committed position's frame. */
public final class SeamDecorations {
    private SeamDecorations() {}

    public static void fold(Entity entity) {
        Level level = Compat.level(entity);
        if (level == null || !LoopingRegistry.isLooping(level)) return;
        BlockAttachedEntityAccessor attached = (BlockAttachedEntityAccessor) entity;
        BlockPos anchor = attached.biblioteca$attachmentPos();
        if (anchor == null) return;
        AABB box = entity.getBoundingBox();
        double shift = SeamPositions.foldY(level, box.getCenter().y) - box.getCenter().y;
        if (shift == 0.0) return;
        entity.setBoundingBox(box.move(0.0, shift, 0.0));
        attached.biblioteca$setAttachmentPos(anchor.offset(0, (int) Math.round(shift), 0));
    }
}
