package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/** Leash length and spring forces use attachment points, bypassing Entity.distanceTo. */
@Mixin(Leashable.class)
public interface LeashableYLoopMixin {
    @WrapOperation(method = "leashDistanceTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    private double biblioteca$leashLength(Vec3 holder, Vec3 attached, Operation<Double> original, Entity other) {
        Entity self = (Entity) (Object) this;
        if (self.level() == other.level() && LoopingRegistry.isLooping(self.level())) {
            holder = SeamPositions.foldToward(self.level(), holder, attached.y);
        }
        return original.call(holder, attached);
    }

    @WrapOperation(method = "computeElasticInteraction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Leashable;computeDampenedSpringInteraction("
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;D"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
    private static Optional<Leashable.Wrench> biblioteca$nearSpringPivot(
            Vec3 pivot, Vec3 attached, double slack, Vec3 motion, Vec3 lever,
            Operation<Optional<Leashable.Wrench>> original, Entity entity, Entity holder) {
        if (entity.level() == holder.level() && LoopingRegistry.isLooping(entity.level())) {
            pivot = SeamPositions.foldToward(entity.level(), pivot, attached.y);
        }
        return original.call(pivot, attached, slack, motion, lever);
    }
}
