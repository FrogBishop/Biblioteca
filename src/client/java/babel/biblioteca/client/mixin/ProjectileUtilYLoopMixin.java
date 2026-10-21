package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Makes the crosshair pick find entities across the seam.
 *
 * <p>The pick casts a short ray in the camera's own coordinates and asks the level for the entities
 * whose boxes are near it. An entity standing on the far side of the seam is not near the camera in
 * absolute terms, so it is never a candidate - the player cannot aim at, attack or interact with
 * it, even though it is drawn beside them.
 *
 * <p>Two small changes make the same logic work on the ring: the search box is widened by one world
 * height in both directions so the far side is a candidate at all, and each candidate's box is moved onto the copy
 * nearest the camera before it is clipped against the ray. The hit location that comes back is in
 * the camera's frame, which is what the client-side interaction code expects.
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilYLoopMixin {

    @ModifyVariable(
            method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;"
                    + "Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static AABB biblioteca$widenSearch(AABB searchBox) {
        Level level = Minecraft.getInstance().level;
        if (level == null || !LoopingRegistry.isLooping(level)) return searchBox;

        int period = level.getHeight();
        return period <= 0 ? searchBox : searchBox.inflate(0.0, period, 0.0);
    }

    @WrapOperation(
            method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;"
                    + "Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB biblioteca$foldCandidateBox(Entity entity, Operation<AABB> original) {
        AABB box = original.call(entity);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return box;

        Level level = entity.level();
        if (!LoopingRegistry.isLooping(level)) return box;

        double cameraY = minecraft.gameRenderer.mainCamera().position().y;
        return SeamPositions.foldToward(level, box, cameraY);
    }
}