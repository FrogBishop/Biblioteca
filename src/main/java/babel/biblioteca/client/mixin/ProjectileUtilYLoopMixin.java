package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes the crosshair pick find entities across the seam.
 *
 * <p>The pick casts a short ray in the camera's own coordinates and asks the level for the entities
 * whose boxes are near it, then clips each candidate box against the ray. An entity standing on the
 * far side of the seam is a world height away by both measures, so it is never a candidate and
 * could not be hit if it were - the player cannot aim at, attack or interact with something drawn
 * beside them.
 *
 * <p>The candidate set is already handled by the level: {@code EntitySearchYLoopMixin} widens a
 * search box that comes near a seam. What is left is the per-candidate test. The box clipped against
 * the ray is the entity's stored one, so it is moved onto the copy nearest the camera first. The hit
 * location that comes back is then in the camera's frame, which is what the client-side interaction
 * code expects.
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilYLoopMixin {

    @WrapOperation(
            method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;"
                    + "Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB biblioteca$foldCandidateBox(Entity entity, Operation<AABB> original,
            @Local(argsOnly = true, ordinal = 0) Vec3 from) {
        AABB box = original.call(entity);

        Level level = Compat.level(entity);
        if (!LoopingRegistry.isLooping(level)) return box;

        return SeamPositions.foldToward(level, box, from.y);
    }

    @WrapOperation(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
    private static Optional<Vec3> biblioteca$pickAlongRay(AABB box, Vec3 from, Vec3 to, Operation<Optional<Vec3>> original,
            @Local(argsOnly = true) Entity source) {
        Level level = Compat.level(source);
        if (!LoopingRegistry.isLooping(level)) return original.call(box, from, to);
        int period = Compat.height(level);
        // Extended interaction reach can cross more than half a short world. Clip every
        // candidate image along the actual segment, retaining the native closest-hit policy.
        long first = (long) Math.ceil((Math.min(from.y, to.y) - box.maxY) / period);
        long last = (long) Math.floor((Math.max(from.y, to.y) - box.minY) / period);
        Optional<Vec3> closest = Optional.empty(); double distance = Double.POSITIVE_INFINITY;
        for (long image = first; image <= last; image++) {
            Optional<Vec3> hit = original.call(image == 0 ? box : box.move(0, image * (double) period, 0), from, to);
            if (hit.isPresent()) {
                double next = from.distanceToSqr(hit.get());
                if (next < distance) { closest = hit; distance = next; }
            }
        }
        return closest;
    }
}
