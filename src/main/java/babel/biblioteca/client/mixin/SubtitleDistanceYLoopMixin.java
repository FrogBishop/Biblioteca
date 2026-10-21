package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingSounds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Position;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Uses the same periodic distance for subtitle audibility and candidate selection as for audio. */
//? if >=1.21 {
@Mixin(targets = "net.minecraft.client.gui.components.SubtitleOverlay$Subtitle")
//?} else {
/*@Mixin(SubtitleOverlay.Subtitle.class)
*///?}
public abstract class SubtitleDistanceYLoopMixin {

    @WrapOperation(method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean biblioteca$audibleAcrossBoundary(
            Vec3 origin, Position target, double range, Operation<Boolean> original) {
        if (target instanceof Vec3 position) target = LoopingSounds.nearestPosition(position, origin);
        return original.call(origin, target, range);
    }

    //? if >=1.21 {
    @WrapOperation(method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double biblioteca$nearestSubtitle(Vec3 source, Vec3 listener, Operation<Double> original) {
        return original.call(LoopingSounds.nearestPosition(source, listener), listener);
    }
    //?}
}
