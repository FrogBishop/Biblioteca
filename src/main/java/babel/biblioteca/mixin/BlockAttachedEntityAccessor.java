package babel.biblioteca.mixin;

import net.minecraft.core.BlockPos;
//? if >=1.21 {
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
//?} else {
/*import net.minecraft.world.entity.decoration.HangingEntity;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read and write access to the block a hanging entity is attached to.
 *
 * The attachment has to move by the same whole periods as the entity's bounding box when the
 * entity is folded into the stored period; otherwise the saved attachment no longer matches the
 * entity position and the entity is rejected on load.
 */
//? if >=1.21 {
@Mixin(BlockAttachedEntity.class)
//?} else {
/*@Mixin(HangingEntity.class)
*///?}
public interface BlockAttachedEntityAccessor {

    @Accessor("pos")
    BlockPos biblioteca$attachmentPos();

    @Accessor("pos")
    void biblioteca$setAttachmentPos(BlockPos pos);
}
