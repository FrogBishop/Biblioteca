package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps pre-1.17 position consumers in the frame chosen by EntityYLoopMixin. */
@Mixin(Entity.class)
public abstract class LegacyEntityPositionYLoopMixin {
    /**
     * These versions move the box first, then commit its bottom through setPosRaw. They also
     * construct the box in setPos from the original arguments, after setPosRaw has folded Y.
     * Leaving that box outside the stored period makes every following move fold again and
     * shift camera history by another whole period, even for purely horizontal movement.
     * Translate the existing box after the commit, before move resumes its collision effects.
     */
    @Inject(method = {"setPos(DDD)V", "setLocationFromBoundingbox()V"}, at = @At("TAIL"))
    private void biblioteca$alignCommittedBox(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Level level = Compat.level(self);
        if (level == null || !LoopingRegistry.isLooping(level)) return;
        AABB box = self.getBoundingBox();
        double offset = self.getY() - box.minY;
        if (offset != 0.0) self.setBoundingBox(box.move(0.0, offset, 0.0));
    }

    /** Native reset writes the original Y back to both history fields after the raw commit. */
    @Inject(method = "setPosAndOldPos(DDD)V", at = @At("TAIL"))
    private void biblioteca$resetCommittedHistory(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Level level = Compat.level(self);
        if (level == null || !LoopingRegistry.isLooping(level)) return;
        self.yo = self.getY();
        self.yOld = self.getY();
    }
}
