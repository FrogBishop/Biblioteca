package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingSounds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps subtitle direction arrows correct after the listener crosses the boundary. */
@Mixin(SubtitleOverlay.class)
public abstract class SubtitleDirectionYLoopMixin {

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$directionAcrossBoundary(Vec3 source, Vec3 listener, Operation<Vec3> original) {
        return original.call(LoopingSounds.nearestPosition(source, listener), listener);
    }
}
