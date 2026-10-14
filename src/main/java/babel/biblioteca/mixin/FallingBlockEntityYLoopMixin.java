package babel.biblioteca.mixin;

import babel.biblioteca.world.LoopingDimensionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FallingBlockEntity overrides Entity.tick() without calling super.tick(),
 * so EntityYLoopMixin's TAIL injection never fires for falling blocks (sand,
 * gravel, concrete powder, etc.).  This mixin adds the Y-wrap check directly
 * to FallingBlockEntity.tick() using the same pattern as PrimedTntYLoopMixin.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityYLoopMixin {

    @Inject(at = @At("TAIL"), method = "tick")
    private void biblioteca$wrapYOnTick(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.isRemoved()) return;
        Level level = self.level();
        if (!(level instanceof ServerLevel)) return;
        if (!LoopingDimensionManager.isLooping(level.dimension())) return;

        double y      = self.getY();
        int    minY   = level.getMinY();
        int    height = level.getHeight();

        if (y >= minY && y < minY + height) return;

        double floorY        = Math.floor(y);
        double frac          = y - floorY;
        int    wrappedBlockY = Math.floorMod((int) floorY - minY, height) + minY;
        self.setPos(self.getX(), wrappedBlockY + frac, self.getZ());
    }
}
