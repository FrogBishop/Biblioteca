package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.IPlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Old NeoForge replaces vanilla inline reach checks with its attribute-aware player extension. */
@Mixin(value = IPlayerExtension.class, remap = false)
public interface NeoForgeReachMixin {
    @ModifyVariable(method = "canReach(Lnet/minecraft/world/phys/Vec3;D)Z", at = @At("HEAD"), argsOnly = true)
    private Vec3 biblioteca$nearEntityHit(Vec3 hit) {
        Player self = (Player) (Object) this;
        return LoopingRegistry.isLooping(Compat.level(self)) ? SeamPositions.foldToward(Compat.level(self), hit, self.getEyeY()) : hit;
    }

    @WrapOperation(method = "canReach(Lnet/minecraft/core/BlockPos;D)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;", remap = true))
    private Vec3 biblioteca$nearBlock(Vec3i position, Operation<Vec3> original) {
        Vec3 centre = original.call(position);
        Player self = (Player) (Object) this;
        return LoopingRegistry.isLooping(Compat.level(self)) ? SeamPositions.foldToward(Compat.level(self), centre, self.getEyeY()) : centre;
    }

    @WrapOperation(method = "isCloseEnough", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;", remap = true))
    private AABB biblioteca$nearEntityBox(Entity entity, Operation<AABB> original) {
        AABB box = original.call(entity);
        Player self = (Player) (Object) this;
        return LoopingRegistry.isLooping(Compat.level(self)) ? SeamPositions.foldToward(Compat.level(self), box, self.getEyeY()) : box;
    }
}
