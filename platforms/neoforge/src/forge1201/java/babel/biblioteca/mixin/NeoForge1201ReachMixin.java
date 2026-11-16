package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.extensions.IForgePlayer;
import org.spongepowered.asm.mixin.Mixin;

/** Preserve the loader's attribute reach and padding while selecting nearby periodic targets. */
@Mixin(Player.class)
public abstract class NeoForge1201ReachMixin implements IForgePlayer {
    @Override public boolean canReach(Vec3 hit, double padding) {
        Player self = (Player) (Object) this;
        if (LoopingRegistry.isLooping(Compat.level(self))) hit = SeamPositions.foldToward(Compat.level(self), hit, self.getEyeY());
        return self.getEyePosition().closerThan(hit, getEntityReach() + padding);
    }
    @Override public boolean canReach(BlockPos pos, double padding) {
        Player self = (Player) (Object) this;
        if (LoopingRegistry.isLooping(Compat.level(self))) {
            double near = SeamPositions.foldForDistance(Compat.level(self), pos.getY() + 0.5, self.getEyeY());
            pos = pos.offset(0, (int) Math.round(near - (pos.getY() + 0.5)), 0);
        }
        double reach = getBlockReach() + padding;
        return self.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < reach * reach;
    }
    @Override public boolean isCloseEnough(Entity target, double distance) {
        Player self = (Player) (Object) this;
        Vec3 eye = self.getEyePosition();
        var box = target.getBoundingBox().inflate(target.getPickRadius());
        if (LoopingRegistry.isLooping(Compat.level(self))) box = SeamPositions.foldToward(Compat.level(self), box, eye.y);
        return box.distanceToSqr(eye) < distance * distance;
    }
}
